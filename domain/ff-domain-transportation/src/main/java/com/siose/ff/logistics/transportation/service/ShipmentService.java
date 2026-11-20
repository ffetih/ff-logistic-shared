package com.siose.ff.logistics.transportation.service;

import com.siose.ff.base.persistence.DatabaseSequences;
import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.standards.GeoDistance;
import com.siose.ff.logistics.standards.TransportMode;
import com.siose.ff.logistics.transportation.DistanceLookup;
import com.siose.ff.logistics.transportation.ShipmentDelivered;
import com.siose.ff.logistics.transportation.ShipmentProgressed;
import com.siose.ff.logistics.transportation.domain.DistanceSource;
import com.siose.ff.logistics.transportation.domain.Incoterm;
import com.siose.ff.logistics.transportation.domain.Shipment;
import com.siose.ff.logistics.transportation.domain.ShipmentRepository;
import com.siose.ff.logistics.transportation.domain.ShipmentStatus;
import com.siose.ff.logistics.transportation.domain.TmsConflictException;
import com.siose.ff.logistics.transportation.domain.TransportLeg;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Shipments of the caller's tenant: planning them and reading them back. */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    /** Created in transportation/V2__transportation_tables.sql. */
    private static final String SHIPMENT_SEQUENCE = "tms_shipment_seq";

    private final ShipmentRepository shipments;
    private final DatabaseSequences sequences;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<DistanceLookup> distances;

    /**
     * @param distances flow 1: the implementation's way to routing. An application without one (the
     *                  area's own tests) plans road legs by the straight-line estimate.
     */
    ShipmentService(ShipmentRepository shipments, DatabaseSequences sequences,
            PlatformTransactionManager transactionManager, ApplicationEventPublisher events,
            ObjectProvider<DistanceLookup> distances) {
        this.shipments = shipments;
        this.sequences = sequences;
        this.transactions = new TransactionTemplate(transactionManager);
        this.events = events;
        this.distances = distances;
    }

    public record NewShipment(
            @Size(max = 64) String customerOrderReference,
            @NotBlank @Size(max = 140) String consignorName,
            @NotBlank @Size(max = 140) String consigneeName,
            @NotBlank String equipmentCode,
            @NotBlank String sscc,
            @NotNull Incoterm incoterm,
            @NotBlank @Size(max = 120) String origin,
            @NotBlank @Size(max = 120) String destination,
            String destinationGln,
            @NotNull @Positive Double grossWeightKg,
            @NotEmpty @Size(max = 10) List<@Valid @NotNull NewLeg> legs) {
    }

    /**
     * A leg with its distance, or (a road leg only) with the coordinates of its two ends, from which
     * routing works the distance out (flow 1).
     */
    public record NewLeg(
            @NotNull TransportMode mode,
            @NotBlank @Size(max = 120) String origin,
            @NotBlank @Size(max = 120) String destination,
            @Positive Double distanceKm,
            @NotBlank @Size(max = 60) String carrierReference,
            @DecimalMin("-90") @DecimalMax("90") Double originLatitude,
            @DecimalMin("-180") @DecimalMax("180") Double originLongitude,
            @DecimalMin("-90") @DecimalMax("90") Double destinationLatitude,
            @DecimalMin("-180") @DecimalMax("180") Double destinationLongitude) {

        boolean hasEnds() {
            return originLatitude != null && originLongitude != null && destinationLatitude != null
                    && destinationLongitude != null;
        }

        boolean hasSomeEnds() {
            return originLatitude != null || originLongitude != null || destinationLatitude != null
                    || destinationLongitude != null;
        }
    }

    /** The shipment, and whether this request created it or found it from an earlier one. */
    public record PlanResult(ShipmentView shipment, boolean created) {
    }

    public record ShipmentView(UUID id, String tenantId, String shipmentNumber, String customerOrderReference,
            String ecmrNumber,
            String consignorName, String consigneeName, String equipmentCode, String sscc, Incoterm incoterm,
            String origin, String destination, String destinationGln, double grossWeightKg, ShipmentStatus status,
            Instant pickedUpAt,
            Instant departedAt, Instant deliveredAt, String receivedBy, FootprintView footprint, List<LegView> legs,
            Instant createdAt) {
    }

    public record Delivery(@NotBlank @Size(max = 140) String receivedBy) {
    }

    public record FootprintView(double totalDistanceKm, double co2eKg, double roadOnlyCo2eKg, double savedCo2eKg,
            double reductionPercent) {
    }

    public record LegView(int sequenceNo, TransportMode mode, String origin, String destination, double distanceKm,
            DistanceSource distanceSource, String carrierReference) {
    }

    /**
     * Plans a shipment, or finds the one this customer order number already has.
     *
     * <p>Not one transaction on purpose. Two requests with the same new order number can both
     * find nothing and both insert; the unique index lets only one commit. The other fails in its
     * own transaction, which is rolled back, and a second transaction reads the winner and
     * answers with it. A rolled-back transaction cannot read anything, hence the two.
     *
     * <p>A refused or repeated plan takes no shipment number. A gap in the numbers still happens
     * when an insert fails after taking one, and is allowed; a number used twice is not.
     */
    public PlanResult plan(NewShipment request) {
        Shipment candidate = Shipment.plan(new Shipment.Plan(request.customerOrderReference(),
                request.consignorName(), request.consigneeName(), request.equipmentCode(), request.sscc(),
                request.incoterm(), request.origin(), request.destination(), request.destinationGln(),
                request.grossWeightKg(),
                IntStream.range(0, request.legs().size())
                        .mapToObj(i -> plannedLeg(i + 1, request.legs().get(i)))
                        .toList()));
        String reference = candidate.getCustomerOrderReference();
        if (reference == null) {
            return transactions.execute(status -> new PlanResult(view(insert(candidate)), true));
        }
        try {
            return transactions.execute(status -> existing(reference, candidate)
                    .orElseGet(() -> new PlanResult(view(insert(candidate)), true)));
        } catch (DataIntegrityViolationException lostRace) {
            return transactions.execute(status -> existing(reference, candidate).orElseThrow(() -> lostRace));
        }
    }

    /**
     * A leg's distance: the planner's, or (flow 1) routing's from the leg's ends. Asked before any
     * transaction starts, so no database connection waits for routing.
     *
     * @throws IllegalArgumentException if the leg has neither, or ends on a leg that is not by road
     */
    private Shipment.PlannedLeg plannedLeg(int number, NewLeg leg) {
        if (leg.hasSomeEnds() && !leg.hasEnds()) {
            throw new IllegalArgumentException("Leg " + number + " needs all four coordinates of its ends, or none");
        }
        Shipment.LegEnds ends = leg.hasEnds()
                ? new Shipment.LegEnds(leg.originLatitude(), leg.originLongitude(), leg.destinationLatitude(),
                        leg.destinationLongitude())
                : null;
        if (leg.distanceKm() != null) {
            return new Shipment.PlannedLeg(leg.mode(), leg.origin(), leg.destination(), leg.distanceKm(),
                    leg.carrierReference(), DistanceSource.GIVEN, ends);
        }
        if (ends == null) {
            throw new IllegalArgumentException("Leg " + number + " needs its distanceKm, or the coordinates of its ends");
        }
        if (leg.mode() != TransportMode.ROAD) {
            throw new IllegalArgumentException("Leg " + number + " goes by " + leg.mode()
                    + ": only a road leg's distance can be worked out from its ends; give its distanceKm");
        }
        return roadDistance(number, leg, ends);
    }

    private Shipment.PlannedLeg roadDistance(int number, NewLeg leg, Shipment.LegEnds ends) {
        DistanceLookup lookup = distances.getIfAvailable();
        if (lookup != null) {
            try {
                double km = lookup.roadKm(ends.originLatitude(), ends.originLongitude(), ends.destinationLatitude(),
                        ends.destinationLongitude());
                if (km > 0) {
                    return new Shipment.PlannedLeg(leg.mode(), leg.origin(), leg.destination(), km,
                            leg.carrierReference(), DistanceSource.ROUTING, ends);
                }
                log.warn("Routing answered {} km for leg {}; using the straight-line estimate", km, number);
            } catch (RuntimeException unavailable) {
                log.warn("Routing did not answer for leg {} ({}); using the straight-line estimate", number,
                        unavailable.toString());
            }
        }
        double estimate = GeoDistance.estimatedRoadKm(ends.originLatitude(), ends.originLongitude(),
                ends.destinationLatitude(), ends.destinationLongitude());
        return new Shipment.PlannedLeg(leg.mode(), leg.origin(), leg.destination(), estimate,
                leg.carrierReference(), DistanceSource.ESTIMATE, ends);
    }

    /** The order number is known: the same plan again gets its shipment, another plan a 409. */
    private Optional<PlanResult> existing(String reference, Shipment candidate) {
        return shipments.findByCustomerOrderReference(reference).map(shipment -> {
            if (!shipment.samePlanAs(candidate)) {
                throw TmsConflictException.customerOrderTaken(reference, shipment.getShipmentNumber());
            }
            return new PlanResult(view(shipment), false);
        });
    }

    /** saveAndFlush: a clash on the order number shows here, inside the transaction, not at its end. */
    private Shipment insert(Shipment candidate) {
        candidate.assignNumber(sequences.next(SHIPMENT_SEQUENCE));
        return shipments.saveAndFlush(candidate);
    }

    /** Publishes {@link ShipmentProgressed} in the same transaction, like {@link #deliver}. */
    @Transactional
    public ShipmentView pickUp(UUID id) {
        Shipment shipment = shipment(id);
        Instant at = Instant.now();
        shipment.pickUp(at);
        events.publishEvent(progressed(shipment, ShipmentProgressed.PICKED_UP, at));
        return view(shipment);
    }

    /** Publishes {@link ShipmentProgressed} in the same transaction, like {@link #deliver}. */
    @Transactional
    public ShipmentView depart(UUID id) {
        Shipment shipment = shipment(id);
        Instant at = Instant.now();
        shipment.depart(at);
        events.publishEvent(progressed(shipment, ShipmentProgressed.DEPARTED, at));
        return view(shipment);
    }

    private static ShipmentProgressed progressed(Shipment shipment, String stage, Instant at) {
        return new ShipmentProgressed(shipment.getId(), shipment.getTenantId(), shipment.getShipmentNumber(),
                shipment.getCustomerOrderReference(), stage, at);
    }

    /**
     * Once, also under concurrency. A second confirmation after the first has committed sees
     * {@code DELIVERED} and is refused by the lifecycle (409 shipment-status-conflict). One that
     * read the shipment before the first committed fails at its own commit, on the version
     * (409 concurrent-update). Either way the shipment is delivered exactly once.
     *
     * <p>{@link ShipmentDelivered} is published in the same transaction. The event publication
     * registry writes it to {@code event_publication} with the shipment's update: both commit, or
     * neither. A confirmation that loses on the version therefore publishes nothing, and one that
     * wins cannot lose its event, even if the pod stops right after the commit.
     */
    @Transactional
    public ShipmentView deliver(UUID id, Delivery delivery) {
        Shipment shipment = shipment(id);
        shipment.deliver(Instant.now(), delivery.receivedBy());
        events.publishEvent(delivered(shipment));
        return view(shipment);
    }

    /** The event of a delivery, by the API or from a route stop (flow 3): the same either way. */
    static ShipmentDelivered delivered(Shipment shipment) {
        return new ShipmentDelivered(shipment.getId(), shipment.getTenantId(), shipment.getShipmentNumber(),
                shipment.getCustomerOrderReference(), shipment.getTotalDistanceKm(), shipment.getSavedCo2eKg(),
                shipment.getDeliveredAt(), shipment.getDestinationGln(), shipment.getSscc(),
                shipment.getGrossWeightKg());
    }

    @Transactional(readOnly = true)
    public List<ShipmentView> list() {
        return shipments.findAllByOrderByCreatedAtDesc().stream().map(ShipmentService::view).toList();
    }

    @Transactional(readOnly = true)
    public ShipmentView get(UUID id) {
        return view(shipment(id));
    }

    private Shipment shipment(UUID id) {
        return shipments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment " + id + " not found"));
    }

    private static ShipmentView view(Shipment s) {
        return new ShipmentView(s.getId(), s.getTenantId(), s.getShipmentNumber(), s.getCustomerOrderReference(),
                s.getEcmrNumber(),
                s.getConsignorName(), s.getConsigneeName(), s.getEquipmentCode(), s.getSscc(), s.getIncoterm(),
                s.getOrigin(), s.getDestination(), s.getDestinationGln(), s.getGrossWeightKg(), s.getStatus(),
                s.getPickedUpAt(),
                s.getDepartedAt(), s.getDeliveredAt(), s.getReceivedBy(), new FootprintView(s.getTotalDistanceKm(), s.getCo2eKg(), s.getRoadOnlyCo2eKg(), s.getSavedCo2eKg(),
                        s.getReductionPercent()),
                s.getLegs().stream().map(ShipmentService::view).toList(), s.getCreatedAt());
    }

    private static LegView view(TransportLeg leg) {
        return new LegView(leg.getSequenceNo(), leg.getMode(), leg.getOrigin(), leg.getDestination(),
                leg.getDistanceKm(), leg.getDistanceSource(), leg.getCarrierReference());
    }
}
