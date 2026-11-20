package com.siose.ff.logistics.routing.service;

import com.siose.ff.logistics.routing.RouteStopCompleted;
import com.siose.ff.logistics.routing.domain.StopStatus;
import com.siose.ff.base.persistence.DatabaseSequences;
import com.siose.ff.base.web.ResourceNotFoundException;
import com.siose.ff.logistics.routing.domain.RoutePlan;
import com.siose.ff.logistics.routing.domain.RoutePlanRepository;
import com.siose.ff.logistics.routing.domain.RouteStop;
import com.siose.ff.logistics.routing.domain.TerritoryRepository;
import com.siose.ff.logistics.routing.planning.Location;
import com.siose.ff.logistics.routing.planning.PlannedRoute;
import com.siose.ff.logistics.routing.planning.RoutePlanner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Route plans of the caller's tenant: planning them and reading them back. */
@Service
public class RoutePlanService {

    /** Created in V2__routing_tables.sql. */
    private static final String ROUTE_SEQUENCE = "geo_route_seq";

    private final RoutePlanRepository plans;
    private final TerritoryRepository territories;
    private final RoutePlanner planner;
    private final DatabaseSequences sequences;
    private final int maxStops;
    private final ApplicationEventPublisher events;

    RoutePlanService(RoutePlanRepository plans, TerritoryRepository territories, RoutePlanner planner,
            DatabaseSequences sequences, @Value("${ff.routing.max-stops:99}") int maxStops,
            ApplicationEventPublisher events) {
        this.events = events;
        this.plans = plans;
        this.territories = territories;
        this.planner = planner;
        this.sequences = sequences;
        this.maxStops = maxStops;
    }

    public record NewRoute(
            @NotBlank @Size(max = 40) String territoryCode,
            @NotBlank @Size(max = 20) String vehiclePlate,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double depotLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double depotLongitude,
            @NotNull @Positive Double vehicleCapacityKg,
            @NotNull @DecimalMin("10") @DecimalMax("130") Double averageSpeedKmh,
            @NotEmpty List<@Valid @NotNull NewStop> stops) {
    }

    public record NewStop(
            @NotBlank @Size(max = 60) String stopCode,
            @NotBlank @Size(max = 140) String customerName,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @Positive Double demandWeightKg,
            @NotNull @PositiveOrZero @Max(600) Integer serviceMinutes,
            @PositiveOrZero @Max(1440) Integer readyMinutes,
            @PositiveOrZero @Max(1440) Integer dueMinutes,
            @Size(max = 20) String shipmentNumber) {
    }

    /** Who signed for the goods at the stop. */
    public record StopCompletion(@NotBlank @Size(max = 140) String receivedBy) {
    }

    public record RoutePlanView(UUID id, String tenantId, String routeNumber, String territoryCode,
            String vehiclePlate, double depotLatitude, double depotLongitude, double vehicleCapacityKg,
            double averageSpeedKmh, double loadedWeightKg, double capacityUtilizationPct, double totalDistanceKm,
            int totalMinutes, List<StopView> stops, Instant createdAt) {
    }

    public record StopView(UUID id, int sequenceNo, String stopCode, String customerName, double latitude,
            double longitude, double demandWeightKg, int serviceMinutes, Integer readyMinutes, Integer dueMinutes,
            double legDistanceKm, int arrivalMinutes, String shipmentNumber, StopStatus status, Instant completedAt,
            String receivedBy) {
    }

    /**
     * Checks first what is cheap and does not depend on the order: the number of stops
     * ({@code BR-GEO-07}, before anything else, because ordering takes work that grows with the
     * square of the list), the territory, the stop codes, the load and the time windows. Then
     * orders and numbers.
     */
    @Transactional
    public RoutePlanView plan(NewRoute request) {
        if (request.stops().size() > maxStops) {
            throw new IllegalArgumentException("A route takes at most %d stops; this one has %d. Split it over several routes."
                    .formatted(maxStops, request.stops().size()));
        }
        String territoryCode = request.territoryCode().trim().toUpperCase();
        if (!territories.existsByTerritoryCode(territoryCode)) {
            throw new IllegalArgumentException("Territory " + territoryCode + " does not exist");
        }
        List<RoutePlan.Stop> stops = request.stops().stream()
                .map(stop -> new RoutePlan.Stop(stop.stopCode(), stop.customerName(), stop.latitude(),
                        stop.longitude(), stop.demandWeightKg(), stop.serviceMinutes(), stop.readyMinutes(),
                        stop.dueMinutes(), blankToNull(stop.shipmentNumber())))
                .toList();
        RoutePlan.checkStops(stops, request.vehicleCapacityKg());

        PlannedRoute route = planner.plan(new Location(request.depotLatitude(), request.depotLongitude()),
                stops.stream()
                        .map(stop -> new RoutePlanner.Visit(new Location(stop.latitude(), stop.longitude()),
                                stop.serviceMinutes(), stop.readyMinutes(), stop.dueMinutes()))
                        .toList(),
                request.averageSpeedKmh());

        RoutePlan plan = RoutePlan.of(
                new RoutePlan.Vehicle(territoryCode, request.vehiclePlate(), request.depotLatitude(),
                        request.depotLongitude(), request.vehicleCapacityKg(), request.averageSpeedKmh()),
                route.stops().stream()
                        .map(planned -> new RoutePlan.ScheduledStop(stops.get(planned.stopIndex()), planned.legKm(),
                                planned.arrivalMinutes()))
                        .toList(),
                route.totalMinutes());
        plan.assignNumber(sequences.next(ROUTE_SEQUENCE));
        return view(plans.save(plan));
    }

    /**
     * The goods were handed over at a stop. The event is published in the same transaction as the
     * stop's new status: the outbox stores it with the commit, and the transportation area delivers
     * the stop's shipment from it (flow 3), in the modulith by a listener, between the services
     * over Kafka. A second completion of the same stop is a 409.
     */
    @Transactional
    public RoutePlanView completeStop(UUID routeId, String stopCode, StopCompletion completion) {
        RoutePlan plan = plans.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route plan " + routeId + " not found"));
        RouteStop stop = plan.completeStop(stopCode, Instant.now(), completion.receivedBy());
        events.publishEvent(new RouteStopCompleted(plan.getId(), plan.getTenantId(), plan.getRouteNumber(),
                stop.getStopCode(), stop.getShipmentNumber(), stop.getReceivedBy(), stop.getCompletedAt()));
        return view(plan);
    }

    @Transactional(readOnly = true)
    public List<RoutePlanView> list() {
        return plans.findAllByOrderByCreatedAtDesc().stream().map(RoutePlanService::view).toList();
    }

    @Transactional(readOnly = true)
    public RoutePlanView get(UUID id) {
        return view(plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route plan " + id + " not found")));
    }

    private static RoutePlanView view(RoutePlan p) {
        return new RoutePlanView(p.getId(), p.getTenantId(), p.getRouteNumber(), p.getTerritoryCode(),
                p.getVehiclePlate(), p.getDepotLatitude(), p.getDepotLongitude(), p.getVehicleCapacityKg(),
                p.getAverageSpeedKmh(), p.getLoadedWeightKg(), p.getCapacityUtilizationPct(), p.getTotalDistanceKm(),
                p.getTotalMinutes(), p.getStops().stream().map(RoutePlanService::view).toList(), p.getCreatedAt());
    }

    private static StopView view(RouteStop s) {
        return new StopView(s.getId(), s.getSequenceNo(), s.getStopCode(), s.getCustomerName(), s.getLatitude(),
                s.getLongitude(), s.getDemandWeightKg(), s.getServiceMinutes(), s.getReadyMinutes(), s.getDueMinutes(),
                s.getLegDistanceKm(), s.getArrivalMinutes(), s.getShipmentNumber(), s.getStatus(), s.getCompletedAt(),
                s.getReceivedBy());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
