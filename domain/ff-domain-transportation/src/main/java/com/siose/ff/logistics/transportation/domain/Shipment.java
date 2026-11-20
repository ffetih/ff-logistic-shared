package com.siose.ff.logistics.transportation.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import com.siose.ff.logistics.standards.EmissionsCalculator;
import com.siose.ff.logistics.standards.EmissionsCalculator.Footprint;
import com.siose.ff.logistics.standards.Gs1Standards;
import com.siose.ff.logistics.standards.Iso6346ContainerCode;
import com.siose.ff.logistics.standards.TransportMode;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * One consignment moved for a customer: one piece of equipment, labelled with the SSCC of what
 * it carries, from origin to destination over a chain of transport legs.
 *
 * <p>The e-CMR number is the electronic consignment note's: the road waybill of the CMR
 * convention, which every road leg in Europe travels under. The CO2e footprint is calculated
 * once, when the shipment is planned, and kept as it was.
 *
 * <p>The shipment and its legs are one aggregate: legs are created only through the shipment,
 * which checks that they form one unbroken chain.
 */
@Entity
// The unique index of V7, declared here too so the tests' generated schema has it.
@Table(name = "tms_shipments", uniqueConstraints = @UniqueConstraint(name = "uq_tms_shipments_customer_order",
        columnNames = {"tenant_id", "customer_order_reference"}))
public class Shipment extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String shipmentNumber;

    /** The customer's own order number, if it gave one; unique within the tenant. */
    @Column(length = 64)
    private String customerOrderReference;

    @Column(nullable = false, length = 20)
    private String ecmrNumber;

    @Column(nullable = false, length = 140)
    private String consignorName;

    @Column(nullable = false, length = 140)
    private String consigneeName;

    @Column(nullable = false, length = 11)
    private String equipmentCode;

    @Column(nullable = false, length = 18)
    private String sscc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Incoterm incoterm;

    @Column(nullable = false, length = 120)
    private String origin;

    @Column(nullable = false, length = 120)
    private String destination;

    /**
     * The GS1 location number of the destination, when it is a known place such as a warehouse:
     * a shipment delivered there opens an inbound arrival at that warehouse (flow 2). Optional.
     */
    @Column(length = 13)
    private String destinationGln;

    private double grossWeightKg;

    private double totalDistanceKm;

    private double co2eKg;

    private double roadOnlyCo2eKg;

    private double savedCo2eKg;

    private double reductionPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    private Instant pickedUpAt;

    private Instant departedAt;

    private Instant deliveredAt;

    @Column(length = 140)
    private String receivedBy;

    /**
     * Optimistic lock: two requests that read the same shipment and both change it cannot both
     * commit. The second fails, so a shipment is delivered once even when two drivers' apps
     * confirm at the same moment.
     */
    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "shipment_id", nullable = false)
    @OrderBy("sequenceNo")
    private List<TransportLeg> legs = new ArrayList<>();

    protected Shipment() {
    }

    /** What a planner books. Legs are given in order; each starts where the previous one ended. */
    public record Plan(String customerOrderReference, String consignorName, String consigneeName,
            String equipmentCode, String sscc, Incoterm incoterm, String origin, String destination,
            String destinationGln, double grossWeightKg, List<PlannedLeg> legs) {
    }

    /**
     * A leg as booked.
     *
     * @param distanceSource where {@code distanceKm} came from
     * @param ends           the coordinates of the leg's two ends; null when it was planned by its
     *                       distance only
     */
    public record PlannedLeg(TransportMode mode, String origin, String destination, double distanceKm,
            String carrierReference, DistanceSource distanceSource, LegEnds ends) {

        /** A leg whose distance the planner gave. */
        public PlannedLeg(TransportMode mode, String origin, String destination, double distanceKm,
                String carrierReference) {
            this(mode, origin, destination, distanceKm, carrierReference, DistanceSource.GIVEN, null);
        }
    }

    /** Where a leg starts and ends, in degrees. */
    public record LegEnds(double originLatitude, double originLongitude, double destinationLatitude,
            double destinationLongitude) {
    }

    /**
     * A checked, not yet numbered shipment: compare it with an existing one ({@link #samePlanAs}),
     * or {@link #assignNumber} it and save it. Numbering last means that a refused plan, or a
     * repeated one, does not use up a number.
     *
     * @throws IllegalArgumentException if the equipment code or SSCC is not valid, or the legs do
     *                                  not form one chain from origin to destination
     */
    public static Shipment plan(Plan plan) {
        String equipmentCode = plan.equipmentCode().trim().toUpperCase();
        if (!Iso6346ContainerCode.isValid(equipmentCode)) {
            throw new IllegalArgumentException("Equipment " + plan.equipmentCode()
                    + " is not an ISO 6346 code with a valid check digit");
        }
        if (!Gs1Standards.isValidSscc18(plan.sscc())) {
            throw new IllegalArgumentException("SSCC " + plan.sscc() + " is not 18 digits with a valid GS1 check digit");
        }
        String destinationGln = plan.destinationGln() == null || plan.destinationGln().isBlank()
                ? null : plan.destinationGln().trim();
        if (destinationGln != null && !Gs1Standards.isValidGln13(destinationGln)) {
            throw new IllegalArgumentException("Destination GLN " + destinationGln
                    + " is not 13 digits with a valid GS1 check digit");
        }
        if (plan.grossWeightKg() <= 0) {
            throw new IllegalArgumentException("Gross weight must be more than 0 kg");
        }

        Shipment shipment = new Shipment();
        String reference = plan.customerOrderReference();
        shipment.customerOrderReference = reference == null || reference.isBlank() ? null : reference.trim();
        shipment.consignorName = plan.consignorName().trim();
        shipment.consigneeName = plan.consigneeName().trim();
        shipment.equipmentCode = equipmentCode;
        shipment.sscc = plan.sscc();
        shipment.incoterm = plan.incoterm();
        shipment.origin = plan.origin().trim();
        shipment.destination = plan.destination().trim();
        shipment.destinationGln = destinationGln;
        shipment.grossWeightKg = plan.grossWeightKg();
        shipment.status = ShipmentStatus.PLANNED;

        String expectedOrigin = shipment.origin;
        for (PlannedLeg leg : plan.legs()) {
            if (!leg.origin().trim().equalsIgnoreCase(expectedOrigin)) {
                throw new IllegalArgumentException("Leg " + (shipment.legs.size() + 1) + " starts at " + leg.origin().trim()
                        + ", but the route is at " + expectedOrigin);
            }
            if (leg.distanceKm() <= 0) {
                throw new IllegalArgumentException("Leg " + (shipment.legs.size() + 1) + " needs a distance above 0 km");
            }
            shipment.legs.add(new TransportLeg(shipment.legs.size() + 1, leg));
            expectedOrigin = leg.destination().trim();
        }
        if (!expectedOrigin.equalsIgnoreCase(shipment.destination)) {
            throw new IllegalArgumentException("The last leg ends at " + expectedOrigin
                    + ", not at the destination " + shipment.destination);
        }

        Footprint footprint = EmissionsCalculator.footprint(shipment.grossWeightKg, shipment.legs.stream()
                .map(leg -> new EmissionsCalculator.Leg(leg.getMode(), leg.getDistanceKm()))
                .toList());
        shipment.totalDistanceKm = footprint.totalDistanceKm();
        shipment.co2eKg = footprint.co2eKg();
        shipment.roadOnlyCo2eKg = footprint.roadOnlyCo2eKg();
        shipment.savedCo2eKg = footprint.savedCo2eKg();
        shipment.reductionPercent = footprint.reductionPercent();
        return shipment;
    }

    /** @param number the shipment's serial number, from {@code tms_shipment_seq} */
    public void assignNumber(long number) {
        if (shipmentNumber != null) {
            throw new IllegalStateException("Shipment " + shipmentNumber + " is already numbered");
        }
        shipmentNumber = "SHP-" + number;
        ecmrNumber = "ECMR-" + number;
    }

    /**
     * Whether this shipment was booked from the same plan as {@code other}: same parties,
     * equipment, goods, terms and route. A client that repeats a request gets the same answer;
     * the same order number with anything else is another order.
     */
    public boolean samePlanAs(Shipment other) {
        return consignorName.equals(other.consignorName)
                && consigneeName.equals(other.consigneeName)
                && equipmentCode.equals(other.equipmentCode)
                && sscc.equals(other.sscc)
                && incoterm == other.incoterm
                && origin.equals(other.origin)
                && destination.equals(other.destination)
                && Objects.equals(destinationGln, other.destinationGln)
                && Double.compare(grossWeightKg, other.grossWeightKg) == 0
                && legs.size() == other.legs.size()
                && IntStream.range(0, legs.size()).allMatch(i -> sameLeg(legs.get(i), other.legs.get(i)));
    }

    /**
     * A leg booked by its ends is the same leg when its ends are, whatever distance it got: a
     * request repeated while routing does not answer gets the estimate, where the first one got the
     * road distance, and is still the same request (flow 1).
     */
    private static boolean sameLeg(TransportLeg a, TransportLeg b) {
        boolean sameDistance = a.getEnds() != null || b.getEnds() != null
                ? Objects.equals(a.getEnds(), b.getEnds())
                : Double.compare(a.getDistanceKm(), b.getDistanceKm()) == 0;
        return a.getMode() == b.getMode()
                && a.getOrigin().equals(b.getOrigin())
                && a.getDestination().equals(b.getDestination())
                && sameDistance
                && Objects.equals(a.getCarrierReference(), b.getCarrierReference());
    }

    /** The carrier has collected the goods. */
    public void pickUp(Instant at) {
        advanceTo(ShipmentStatus.PICKED_UP);
        pickedUpAt = at;
    }

    /** The first leg has left the origin. */
    public void depart(Instant at) {
        advanceTo(ShipmentStatus.DEPARTED);
        departedAt = at;
    }

    /** The consignee has signed for the goods; {@code receivedBy} is the name under the signature. */
    public void deliver(Instant at, String receivedBy) {
        advanceTo(ShipmentStatus.DELIVERED);
        deliveredAt = at;
        this.receivedBy = receivedBy.trim();
    }

    /** Forward only: the target must come after the current status. */
    private void advanceTo(ShipmentStatus target) {
        if (target.compareTo(status) <= 0) {
            throw TmsConflictException.statusConflict(shipmentNumber, status, target);
        }
        status = target;
    }

    public UUID getId() {
        return id;
    }

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public String getCustomerOrderReference() {
        return customerOrderReference;
    }

    public String getEcmrNumber() {
        return ecmrNumber;
    }

    public String getConsignorName() {
        return consignorName;
    }

    public String getConsigneeName() {
        return consigneeName;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public String getSscc() {
        return sscc;
    }

    public Incoterm getIncoterm() {
        return incoterm;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public double getGrossWeightKg() {
        return grossWeightKg;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public double getCo2eKg() {
        return co2eKg;
    }

    public double getRoadOnlyCo2eKg() {
        return roadOnlyCo2eKg;
    }

    public double getSavedCo2eKg() {
        return savedCo2eKg;
    }

    public double getReductionPercent() {
        return reductionPercent;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public Instant getDepartedAt() {
        return departedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public String getDestinationGln() {
        return destinationGln;
    }

    public List<TransportLeg> getLegs() {
        return List.copyOf(legs);
    }
}
