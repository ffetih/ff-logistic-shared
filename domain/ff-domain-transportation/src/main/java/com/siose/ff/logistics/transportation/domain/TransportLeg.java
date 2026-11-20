package com.siose.ff.logistics.transportation.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import com.siose.ff.logistics.standards.TransportMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One stretch of a shipment's route, by one mode and one carrier: a sailing, a train, a truck run. */
@Entity
@Table(name = "tms_transport_legs")
public class TransportLeg extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 1 for the first leg, counting along the route. */
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportMode mode;

    @Column(nullable = false, length = 120)
    private String origin;

    @Column(nullable = false, length = 120)
    private String destination;

    private double distanceKm;

    /** The carrier's own number for this leg: a booking, a train slot, a trip. */
    @Column(nullable = false, length = 60)
    private String carrierReference;

    protected TransportLeg() {
    }

    TransportLeg(int sequenceNo, TransportMode mode, String origin, String destination, double distanceKm,
            String carrierReference) {
        this.sequenceNo = sequenceNo;
        this.mode = mode;
        this.origin = origin;
        this.destination = destination;
        this.distanceKm = distanceKm;
        this.carrierReference = carrierReference;
    }

    public UUID getId() {
        return id;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public TransportMode getMode() {
        return mode;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public String getCarrierReference() {
        return carrierReference;
    }
}
