package com.siose.ff.logistics.warehousing.service;

import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.warehousing.InboundArrivals;
import com.siose.ff.logistics.warehousing.ShipmentArrival;
import com.siose.ff.logistics.warehousing.domain.ArrivalStatus;
import com.siose.ff.logistics.warehousing.domain.InboundArrival;
import com.siose.ff.logistics.warehousing.domain.InboundArrivalRepository;
import com.siose.ff.logistics.warehousing.domain.Warehouse;
import com.siose.ff.logistics.warehousing.domain.WarehouseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Inbound arrivals: opened by deliveries (flow 2), read by the warehouse's staff. */
@Service
public class ArrivalService implements InboundArrivals {

    private static final Logger log = LoggerFactory.getLogger(ArrivalService.class);

    private final InboundArrivalRepository arrivals;
    private final WarehouseRepository warehouses;
    private final TransactionTemplate transactions;

    ArrivalService(InboundArrivalRepository arrivals, WarehouseRepository warehouses,
            PlatformTransactionManager transactionManager) {
        this.arrivals = arrivals;
        this.warehouses = warehouses;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public record ArrivalView(UUID id, UUID warehouseId, UUID shipmentId, String shipmentNumber, String sscc,
            double grossWeightKg, Instant arrivedAt, ArrivalStatus status) {
    }

    /**
     * Binds the arrival's tenant before the transaction starts: Hibernate takes the tenant when a
     * transaction opens. The caller's own tenant, if any, is put back afterwards.
     *
     * <p>Two deliveries of the same shipment at once can both find no arrival and both insert; the
     * unique key lets one commit. The other's transaction is rolled back, and the answer is the
     * same as for a repeat: already open.
     */
    @Override
    public Outcome open(ShipmentArrival arrival) {
        Optional<TenantInfo> caller = TenantContext.findCurrent();
        TenantContext.setCurrent(new TenantInfo(arrival.tenantId(), "system:inbound-arrivals", Set.of(), "EVENT"));
        try {
            Outcome outcome = transactions.execute(status -> openInTenant(arrival));
            log.info("Shipment {} at {}: {}", arrival.shipmentNumber(), arrival.warehouseGln(), outcome);
            return outcome;
        } catch (DataIntegrityViolationException lostRace) {
            log.info("Shipment {} at {}: {} (by a concurrent delivery)", arrival.shipmentNumber(),
                    arrival.warehouseGln(), Outcome.ALREADY_OPEN);
            return Outcome.ALREADY_OPEN;
        } finally {
            caller.ifPresentOrElse(TenantContext::setCurrent, TenantContext::clear);
        }
    }

    private Outcome openInTenant(ShipmentArrival arrival) {
        if (arrivals.existsByShipmentId(arrival.shipmentId())) {
            return Outcome.ALREADY_OPEN;
        }
        Optional<Warehouse> warehouse = warehouses.findByGln(arrival.warehouseGln());
        if (warehouse.isEmpty()) {
            return Outcome.NOT_A_WAREHOUSE;
        }
        arrivals.saveAndFlush(new InboundArrival(warehouse.get().getId(), arrival.shipmentId(),
                arrival.shipmentNumber(), arrival.sscc(), arrival.grossWeightKg(), arrival.arrivedAt()));
        return Outcome.OPENED;
    }

    @Transactional(readOnly = true)
    public List<ArrivalView> list() {
        return arrivals.findAllByOrderByArrivedAtDesc().stream().map(ArrivalService::view).toList();
    }

    @Transactional(readOnly = true)
    public ArrivalView get(UUID id) {
        return arrivals.findById(id).map(ArrivalService::view)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound arrival " + id + " not found"));
    }

    private static ArrivalView view(InboundArrival a) {
        return new ArrivalView(a.getId(), a.getWarehouseId(), a.getShipmentId(), a.getShipmentNumber(), a.getSscc(),
                a.getGrossWeightKg(), a.getArrivedAt(), a.getStatus());
    }
}
