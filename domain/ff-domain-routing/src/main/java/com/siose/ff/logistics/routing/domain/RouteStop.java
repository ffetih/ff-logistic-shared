package com.siose.ff.logistics.routing.domain;

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

/** One stop of a route plan, in driving order. Created only through {@link RoutePlan}. */
@Entity
@Table(name = "geo_route_stops")
public class RouteStop extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 1 for the first stop after the depot. */
    private int sequenceNo;

    @Column(nullable = false, length = 60)
    private String stopCode;

    @Column(nullable = false, length = 140)
    private String customerName;

    private double latitude;

    private double longitude;

    private double demandWeightKg;

    private int serviceMinutes;

    /** From the depot for the first stop, from the stop before for the others. */
    private double legDistanceKm;

    /** Minutes after departure; service starts here, after a wait if the vehicle came early. */
    private int arrivalMinutes;

    /** The time window, in minutes after departure; both null for a stop without one. */
    private Integer readyMinutes;

    private Integer dueMinutes;

    /** The transportation shipment this stop delivers; null for one that delivers none. */
    @Column(length = 20)
    private String shipmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StopStatus status = StopStatus.PLANNED;

    private Instant completedAt;

    @Column(length = 140)
    private String receivedBy;

    protected RouteStop() {
    }

    RouteStop(int sequenceNo, RoutePlan.Stop stop, double legDistanceKm, int arrivalMinutes) {
        this.sequenceNo = sequenceNo;
        this.stopCode = stop.stopCode().trim();
        this.customerName = stop.customerName().trim();
        this.latitude = stop.latitude();
        this.longitude = stop.longitude();
        this.demandWeightKg = stop.demandWeightKg();
        this.serviceMinutes = stop.serviceMinutes();
        this.legDistanceKm = legDistanceKm;
        this.arrivalMinutes = arrivalMinutes;
        this.readyMinutes = stop.readyMinutes();
        this.dueMinutes = stop.dueMinutes();
        this.shipmentNumber = stop.shipmentNumber() == null ? null : stop.shipmentNumber().trim();
    }

    /**
     * The goods were handed over: once only.
     *
     * @throws GeoConflictException if the stop is already completed
     */
    void complete(String routeNumber, Instant at, String receivedBy) {
        if (status == StopStatus.COMPLETED) {
            throw GeoConflictException.stopAlreadyCompleted(routeNumber, stopCode);
        }
        status = StopStatus.COMPLETED;
        completedAt = at;
        this.receivedBy = receivedBy.trim();
    }

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public StopStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public UUID getId() {
        return id;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getStopCode() {
        return stopCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getDemandWeightKg() {
        return demandWeightKg;
    }

    public int getServiceMinutes() {
        return serviceMinutes;
    }

    public double getLegDistanceKm() {
        return legDistanceKm;
    }

    public int getArrivalMinutes() {
        return arrivalMinutes;
    }

    public Integer getReadyMinutes() {
        return readyMinutes;
    }

    public Integer getDueMinutes() {
        return dueMinutes;
    }
}
