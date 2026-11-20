package com.siose.ff.logistics.warehousing.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import com.siose.ff.logistics.standards.Gs1Standards;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One pallet built at receiving: goods of one ASN line, with its SSCC label, standing in a bin
 * location. The SSCC is the pallet's identity for everyone after this point: the next scan, the
 * outbound shipment, the customer's own receiving.
 */
@Entity
@Table(name = "wms_pallets")
public class Pallet extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 18)
    private String sscc;

    @Column(nullable = false)
    private UUID asnId;

    @Column(nullable = false)
    private UUID warehouseId;

    @Column(nullable = false)
    private UUID binLocationId;

    @Column(nullable = false, length = 14)
    private String gtin;

    @Column(nullable = false, length = 60)
    private String skuCode;

    private int quantity;

    private double grossWeightKg;

    protected Pallet() {
    }

    public Pallet(String sscc, Asn asn, AsnLine line, BinLocation bin, int quantity, double grossWeightKg) {
        if (!Gs1Standards.isValidSscc18(sscc)) {
            throw new IllegalArgumentException("SSCC " + sscc + " is not 18 digits with a valid GS1 check digit");
        }
        if (!bin.getWarehouseId().equals(asn.getWarehouseId())) {
            throw new IllegalArgumentException("Bin location " + bin.getCode() + " is not in the ASN's warehouse");
        }
        this.sscc = sscc;
        this.asnId = asn.getId();
        this.warehouseId = asn.getWarehouseId();
        this.binLocationId = bin.getId();
        this.gtin = line.getGtin();
        this.skuCode = line.getSkuCode();
        this.quantity = quantity;
        this.grossWeightKg = grossWeightKg;
    }

    public UUID getId() {
        return id;
    }

    public String getSscc() {
        return sscc;
    }

    public UUID getAsnId() {
        return asnId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public UUID getBinLocationId() {
        return binLocationId;
    }

    public String getGtin() {
        return gtin;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getGrossWeightKg() {
        return grossWeightKg;
    }
}
