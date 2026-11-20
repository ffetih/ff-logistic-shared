package com.siose.ff.logistics.warehousing.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A delivered shipment at a warehouse's dock, waiting to be received. Opened by the delivery
 * itself, not by a person: the warehouse learns of it from transportation (flow 2).
 */
@Entity
@Table(name = "wms_inbound_arrivals")
public class InboundArrival extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID warehouseId;

    /** Transportation's id: unique, so a shipment opens one arrival however often it is reported. */
    @Column(nullable = false)
    private UUID shipmentId;

    @Column(nullable = false, length = 20)
    private String shipmentNumber;

    @Column(nullable = false, length = 18)
    private String sscc;

    private double grossWeightKg;

    @Column(nullable = false)
    private Instant arrivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArrivalStatus status;

    protected InboundArrival() {
    }

    public InboundArrival(UUID warehouseId, UUID shipmentId, String shipmentNumber, String sscc,
            double grossWeightKg, Instant arrivedAt) {
        this.warehouseId = warehouseId;
        this.shipmentId = shipmentId;
        this.shipmentNumber = shipmentNumber;
        this.sscc = sscc;
        this.grossWeightKg = grossWeightKg;
        this.arrivedAt = arrivedAt;
        this.status = ArrivalStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public String getSscc() {
        return sscc;
    }

    public double getGrossWeightKg() {
        return grossWeightKg;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public ArrivalStatus getStatus() {
        return status;
    }
}
