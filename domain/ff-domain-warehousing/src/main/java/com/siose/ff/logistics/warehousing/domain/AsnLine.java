package com.siose.ff.logistics.warehousing.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One trade item on an ASN: which GTIN, and how many units the supplier says are coming. */
@Entity
@Table(name = "wms_asn_lines")
public class AsnLine extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private int lineNumber;

    @Column(nullable = false, length = 14)
    private String gtin;

    @Column(nullable = false, length = 60)
    private String skuCode;

    private int expectedQuantity;

    private int receivedQuantity;

    protected AsnLine() {
    }

    AsnLine(int lineNumber, String gtin, String skuCode, int expectedQuantity) {
        this.lineNumber = lineNumber;
        this.gtin = gtin;
        this.skuCode = skuCode.trim().toUpperCase();
        this.expectedQuantity = expectedQuantity;
        this.receivedQuantity = 0;
    }

    /** Units still to come: what was announced minus what has been received. */
    public int getOpenQuantity() {
        return expectedQuantity - receivedQuantity;
    }

    void receive(int quantity) {
        receivedQuantity += quantity;
    }

    public UUID getId() {
        return id;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getGtin() {
        return gtin;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public int getExpectedQuantity() {
        return expectedQuantity;
    }

    public int getReceivedQuantity() {
        return receivedQuantity;
    }
}
