package com.siose.ff.logistics.warehousing.service;

import com.siose.ff.base.persistence.DatabaseSequences;
import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.standards.Gs1Standards;
import com.siose.ff.logistics.warehousing.domain.Asn;
import com.siose.ff.logistics.warehousing.domain.AsnLine;
import com.siose.ff.logistics.warehousing.domain.AsnRepository;
import com.siose.ff.logistics.warehousing.domain.AsnStatus;
import com.siose.ff.logistics.warehousing.domain.BinLocation;
import com.siose.ff.logistics.warehousing.domain.BinLocationRepository;
import com.siose.ff.logistics.warehousing.domain.Pallet;
import com.siose.ff.logistics.warehousing.domain.PalletRepository;
import com.siose.ff.logistics.warehousing.domain.Warehouse;
import com.siose.ff.logistics.warehousing.domain.WarehouseRepository;
import com.siose.ff.logistics.warehousing.domain.WmsConflictException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receiving: goods of an ASN line are built into a pallet, the pallet gets an SSCC label and is
 * put away into a bin location. One request is one pallet.
 */
@Service
public class PalletizingService {

    /** Created in V3__wms_pallets.sql. */
    private static final String SSCC_SERIAL_SEQUENCE = "wms_sscc_serial_seq";

    /** The SSCC's first digit. The company chooses it; this platform always prints 0. */
    private static final int SSCC_EXTENSION_DIGIT = 0;

    private final AsnRepository asns;
    private final WarehouseRepository warehouses;
    private final BinLocationRepository bins;
    private final PalletRepository pallets;
    private final DatabaseSequences sequences;

    PalletizingService(AsnRepository asns, WarehouseRepository warehouses, BinLocationRepository bins,
            PalletRepository pallets, DatabaseSequences sequences) {
        this.asns = asns;
        this.warehouses = warehouses;
        this.bins = bins;
        this.pallets = pallets;
        this.sequences = sequences;
    }

    public record NewPallet(
            @NotBlank String gtin,
            @NotNull @Min(1) Integer quantity,
            @NotNull @Positive Double grossWeightKg,
            @NotBlank String binCode) {
    }

    public record PalletView(UUID id, String sscc, String asnNumber, AsnStatus asnStatus, String gtin,
            String skuCode, int quantity, double grossWeightKg, UUID warehouseId, String binCode,
            Instant createdAt) {
    }

    /**
     * Locks the ASN, then the bin (always in this order), checks both, books the quantity on the
     * line and labels the pallet. Everything happens in one transaction: if the label cannot be
     * saved, the booking is undone too. An SSCC serial taken from the sequence is never given
     * back; a gap in the numbers is allowed, a number used twice is not.
     */
    @Transactional
    public PalletView palletize(UUID asnId, NewPallet request) {
        Asn asn = asns.findForReceivingById(asnId)
                .orElseThrow(() -> new ResourceNotFoundException("ASN " + asnId + " not found"));
        Warehouse warehouse = warehouses.findById(asn.getWarehouseId())
                .orElseThrow(() -> new IllegalStateException("ASN " + asn.getAsnNumber() + " has no warehouse"));
        String binCode = request.binCode().trim().toUpperCase();
        BinLocation bin = bins.findForPutawayByWarehouseIdAndCode(warehouse.getId(), binCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Warehouse " + warehouse.getCode() + " has no bin location " + binCode));
        if (pallets.countByBinLocationId(bin.getId()) >= bin.getPalletCapacity()) {
            throw WmsConflictException.binFull(bin.getCode(), bin.getPalletCapacity());
        }

        AsnLine line = asn.receive(request.gtin(), request.quantity());

        String sscc = Gs1Standards.generateSscc18(
                SSCC_EXTENSION_DIGIT, warehouse.getGs1CompanyPrefix(), sequences.next(SSCC_SERIAL_SEQUENCE));
        Pallet pallet = pallets.save(new Pallet(sscc, asn, line, bin, request.quantity(), request.grossWeightKg()));
        return view(pallet, asn, bin);
    }

    @Transactional(readOnly = true)
    public List<PalletView> palletsOf(UUID asnId) {
        Asn asn = asns.findById(asnId)
                .orElseThrow(() -> new ResourceNotFoundException("ASN " + asnId + " not found"));
        return pallets.findAllByAsnIdOrderBySscc(asn.getId()).stream()
                .map(pallet -> view(pallet, asn, bin(pallet)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PalletView bySscc(String sscc) {
        Pallet pallet = pallets.findBySscc(sscc)
                .orElseThrow(() -> new ResourceNotFoundException("No pallet with SSCC " + sscc));
        Asn asn = asns.findById(pallet.getAsnId())
                .orElseThrow(() -> new IllegalStateException("Pallet " + sscc + " has no ASN"));
        return view(pallet, asn, bin(pallet));
    }

    private BinLocation bin(Pallet pallet) {
        return bins.findById(pallet.getBinLocationId())
                .orElseThrow(() -> new IllegalStateException("Pallet " + pallet.getSscc() + " has no bin location"));
    }

    private static PalletView view(Pallet pallet, Asn asn, BinLocation bin) {
        return new PalletView(pallet.getId(), pallet.getSscc(), asn.getAsnNumber(), asn.getStatus(),
                pallet.getGtin(), pallet.getSkuCode(), pallet.getQuantity(), pallet.getGrossWeightKg(),
                pallet.getWarehouseId(), bin.getCode(), pallet.getCreatedAt());
    }
}
