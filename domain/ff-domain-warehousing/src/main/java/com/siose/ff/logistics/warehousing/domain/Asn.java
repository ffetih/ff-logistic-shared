package com.siose.ff.logistics.warehousing.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import com.siose.ff.logistics.standards.Gs1Standards;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An advance shipping notice: the supplier's message that goods are on their way to one of our
 * warehouses (EDIFACT DESADV, GS1 "despatch advice"). The warehouse plans its dock and labour
 * from it, and receiving checks what arrives against its lines.
 *
 * <p>The ASN and its lines are one aggregate: lines are created, saved and changed only through
 * the ASN, which keeps the rules (one line per GTIN, numbered from 1) in one place.
 */
@Entity
@Table(name = "wms_asns")
public class Asn extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The supplier's document number, up to 35 characters as in EDIFACT. */
    @Column(nullable = false, length = 35)
    private String asnNumber;

    @Column(nullable = false)
    private UUID warehouseId;

    @Column(nullable = false, length = 140)
    private String supplierName;

    @Column(nullable = false)
    private LocalDate expectedArrival;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AsnStatus status;

    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "asn_id", nullable = false)
    @OrderBy("lineNumber")
    private List<AsnLine> lines = new ArrayList<>();

    protected Asn() {
    }

    public Asn(String asnNumber, UUID warehouseId, String supplierName, LocalDate expectedArrival) {
        this.asnNumber = asnNumber.trim().toUpperCase();
        this.warehouseId = warehouseId;
        this.supplierName = supplierName.trim();
        this.expectedArrival = expectedArrival;
        this.status = AsnStatus.EXPECTED;
    }

    /**
     * @throws IllegalArgumentException for a GTIN with a wrong check digit, a GTIN already on
     *                                  this ASN, or a quantity below 1
     */
    public void addLine(String gtin, String skuCode, int expectedQuantity) {
        if (!Gs1Standards.isValidGtin(gtin)) {
            throw new IllegalArgumentException("GTIN " + gtin + " is not 8, 12, 13 or 14 digits with a valid GS1 check digit");
        }
        if (expectedQuantity < 1) {
            throw new IllegalArgumentException("Expected quantity must be at least 1");
        }
        Set<String> gtins = new HashSet<>();
        lines.forEach(line -> gtins.add(line.getGtin()));
        if (!gtins.add(gtin)) {
            throw new IllegalArgumentException("GTIN " + gtin + " is on this ASN twice; announce it on one line");
        }
        lines.add(new AsnLine(lines.size() + 1, gtin, skuCode, expectedQuantity));
    }

    /**
     * Books goods for one line, as one pallet is built. The first receipt moves the ASN to
     * {@code RECEIVING}; the one that completes the last open line moves it to {@code RECEIVED}.
     *
     * @return the line the goods were booked on
     * @throws IllegalArgumentException if no line has this GTIN, or the quantity is below 1
     * @throws WmsConflictException     if the ASN is already received, or the quantity is more than
     *                                  the line still expects
     */
    public AsnLine receive(String gtin, int quantity) {
        if (status == AsnStatus.RECEIVED) {
            throw WmsConflictException.asnAlreadyReceived(asnNumber);
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("A pallet holds at least one unit");
        }
        AsnLine line = lines.stream()
                .filter(candidate -> candidate.getGtin().equals(gtin))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("GTIN " + gtin + " is not on ASN " + asnNumber));
        if (quantity > line.getOpenQuantity()) {
            throw WmsConflictException.overReceipt(asnNumber, line.getLineNumber(), quantity, line.getOpenQuantity());
        }
        line.receive(quantity);
        status = lines.stream().allMatch(candidate -> candidate.getOpenQuantity() == 0)
                ? AsnStatus.RECEIVED
                : AsnStatus.RECEIVING;
        return line;
    }

    public UUID getId() {
        return id;
    }

    public String getAsnNumber() {
        return asnNumber;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public LocalDate getExpectedArrival() {
        return expectedArrival;
    }

    public AsnStatus getStatus() {
        return status;
    }

    public List<AsnLine> getLines() {
        return List.copyOf(lines);
    }
}
