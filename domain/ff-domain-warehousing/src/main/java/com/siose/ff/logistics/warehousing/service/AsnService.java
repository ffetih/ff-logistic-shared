package com.siose.ff.logistics.warehousing.service;

import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.warehousing.domain.Asn;
import com.siose.ff.logistics.warehousing.domain.AsnLine;
import com.siose.ff.logistics.warehousing.domain.AsnRepository;
import com.siose.ff.logistics.warehousing.domain.AsnStatus;
import com.siose.ff.logistics.warehousing.domain.Warehouse;
import com.siose.ff.logistics.warehousing.domain.WarehouseRepository;
import com.siose.ff.logistics.warehousing.domain.WmsConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inbound ASNs: what suppliers have announced for the caller's warehouses. */
@Service
public class AsnService {

    private final AsnRepository asns;
    private final WarehouseRepository warehouses;

    AsnService(AsnRepository asns, WarehouseRepository warehouses) {
        this.asns = asns;
        this.warehouses = warehouses;
    }

    public record NewAsn(
            @NotBlank @Size(max = 35) String asnNumber,
            @NotNull UUID warehouseId,
            @NotBlank @Size(max = 140) String supplierName,
            @NotNull LocalDate expectedArrival,
            @NotEmpty List<@Valid @NotNull NewAsnLine> lines) {
    }

    public record NewAsnLine(
            @NotBlank String gtin,
            @NotBlank @Size(max = 60) String skuCode,
            @NotNull @Min(1) Integer quantity) {
    }

    public record AsnView(UUID id, String tenantId, String asnNumber, UUID warehouseId, String supplierName,
            LocalDate expectedArrival, AsnStatus status, List<AsnLineView> lines) {
    }

    public record AsnLineView(int lineNumber, String gtin, String skuCode, int expectedQuantity, int receivedQuantity) {
    }

    @Transactional
    public AsnView create(NewAsn request) {
        Warehouse warehouse = warehouses.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse " + request.warehouseId() + " not found"));
        Asn asn = new Asn(request.asnNumber(), warehouse.getId(), request.supplierName(), request.expectedArrival());
        request.lines().forEach(line -> asn.addLine(line.gtin(), line.skuCode(), line.quantity()));
        if (asns.existsByAsnNumber(asn.getAsnNumber())) {
            throw WmsConflictException.asnNumberTaken(asn.getAsnNumber());
        }
        return view(asns.save(asn));
    }

    @Transactional(readOnly = true)
    public List<AsnView> list() {
        return asns.findAllByOrderByExpectedArrivalAscAsnNumberAsc().stream().map(AsnService::view).toList();
    }

    @Transactional(readOnly = true)
    public AsnView get(UUID id) {
        return view(asn(id));
    }

    private Asn asn(UUID id) {
        return asns.findById(id).orElseThrow(() -> new ResourceNotFoundException("ASN " + id + " not found"));
    }

    private static AsnView view(Asn asn) {
        return new AsnView(asn.getId(), asn.getTenantId(), asn.getAsnNumber(), asn.getWarehouseId(),
                asn.getSupplierName(), asn.getExpectedArrival(), asn.getStatus(),
                asn.getLines().stream().map(AsnService::view).toList());
    }

    private static AsnLineView view(AsnLine line) {
        return new AsnLineView(line.getLineNumber(), line.getGtin(), line.getSkuCode(),
                line.getExpectedQuantity(), line.getReceivedQuantity());
    }
}
