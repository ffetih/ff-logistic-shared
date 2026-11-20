package com.siose.ff.logistics.transportation.service;

import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import com.siose.ff.logistics.transportation.ShipmentDeliveries;
import com.siose.ff.logistics.transportation.StopDelivery;
import com.siose.ff.logistics.transportation.domain.Shipment;
import com.siose.ff.logistics.transportation.domain.ShipmentRepository;
import com.siose.ff.logistics.transportation.domain.ShipmentStatus;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Flow 3: a completed route stop delivers its shipment. */
@Service
class StopDeliveryService implements ShipmentDeliveries {

    private static final Logger log = LoggerFactory.getLogger(StopDeliveryService.class);

    private final ShipmentRepository shipments;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;

    StopDeliveryService(ShipmentRepository shipments, ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.shipments = shipments;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Binds the stop's tenant before the transaction starts: Hibernate takes the tenant when a
     * transaction opens. The caller's own tenant, if any, is put back afterwards.
     *
     * <p>Two completions of the same stop at once (a message read twice, in two pods) can both find
     * the shipment not yet delivered. The shipment's version lets one commit; the other's
     * transaction is rolled back, and the answer is the same as for a repeat: already delivered.
     * {@code ShipmentDelivered} is published inside the transaction, so it is stored with the
     * delivery or not at all.
     */
    @Override
    public Outcome deliver(StopDelivery delivery) {
        Optional<TenantInfo> caller = TenantContext.findCurrent();
        TenantContext.setCurrent(new TenantInfo(delivery.tenantId(), "system:stop-deliveries", Set.of(), "EVENT"));
        try {
            Outcome outcome = transactions.execute(status -> deliverInTenant(delivery));
            log.info("Shipment {} from stop {} of {}: {}", delivery.shipmentNumber(), delivery.stopCode(),
                    delivery.routeNumber(), outcome);
            return outcome;
        } catch (ObjectOptimisticLockingFailureException lostRace) {
            log.info("Shipment {} from stop {} of {}: {} (by a concurrent completion)", delivery.shipmentNumber(),
                    delivery.stopCode(), delivery.routeNumber(), Outcome.ALREADY_DELIVERED);
            return Outcome.ALREADY_DELIVERED;
        } finally {
            caller.ifPresentOrElse(TenantContext::setCurrent, TenantContext::clear);
        }
    }

    private Outcome deliverInTenant(StopDelivery delivery) {
        Optional<Shipment> found = shipments.findByShipmentNumber(delivery.shipmentNumber().trim());
        if (found.isEmpty()) {
            return Outcome.UNKNOWN_SHIPMENT;
        }
        Shipment shipment = found.get();
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            return Outcome.ALREADY_DELIVERED;
        }
        shipment.deliver(delivery.deliveredAt(), delivery.receivedBy());
        shipments.saveAndFlush(shipment);
        events.publishEvent(ShipmentService.delivered(shipment));
        return Outcome.DELIVERED;
    }
}
