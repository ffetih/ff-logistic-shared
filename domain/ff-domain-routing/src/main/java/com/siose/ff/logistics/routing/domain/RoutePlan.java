package com.siose.ff.logistics.routing.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One vehicle's tour from a depot: its stops in driving order, each with the distance from the
 * previous one and the arrival time, counted in minutes from departure. The tour ends at the last
 * stop; the way back to the depot is not part of it.
 *
 * <p>The plan and its stops are one aggregate. The order comes from the route planner; the plan
 * checks what does not depend on the order: the stop codes and the load.
 */
@Entity
@Table(name = "geo_route_plans")
public class RoutePlan extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String routeNumber;

    @Column(nullable = false, length = 40)
    private String territoryCode;

    @Column(nullable = false, length = 20)
    private String vehiclePlate;

    private double depotLatitude;

    private double depotLongitude;

    private double vehicleCapacityKg;

    private double averageSpeedKmh;

    private double loadedWeightKg;

    private double capacityUtilizationPct;

    private double totalDistanceKm;

    /** From departure until the service at the last stop ends. */
    private int totalMinutes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "route_plan_id", nullable = false)
    @OrderBy("sequenceNo")
    private List<RouteStop> stops = new ArrayList<>();

    protected RoutePlan() {
    }

    /** What a dispatcher asks for, apart from the stops. */
    public record Vehicle(String territoryCode, String vehiclePlate, double depotLatitude, double depotLongitude,
            double capacityKg, double averageSpeedKmh) {
    }

    /**
     * A stop as requested, in any order.
     *
     * @param readyMinutes earliest arrival, in minutes after departure; with {@code dueMinutes} or not at all
     * @param dueMinutes   latest arrival; the vehicle waits if it is early, and may not be late
     */
    public record Stop(String stopCode, String customerName, double latitude, double longitude,
            double demandWeightKg, int serviceMinutes, Integer readyMinutes, Integer dueMinutes) {

        public boolean hasTimeWindow() {
            return readyMinutes != null;
        }
    }

    /** A stop in driving order, with what the planner worked out for it. */
    public record ScheduledStop(Stop stop, double legDistanceKm, int arrivalMinutes) {
    }

    /**
     * Checks the stops before anything is planned: every code once ({@code BR-GEO-06}), no more
     * load than the vehicle carries ({@code BR-GEO-03}), and time windows that are whole
     * ({@code BR-GEO-04}).
     *
     * <p>Codes are compared the way a file name is made from them: lower case, and anything other
     * than letters, digits and hyphens as a hyphen. Each stop's proof of delivery is filed under
     * its code (S05-05), so {@code STOP_1}, {@code stop 1} and {@code STOP-1} are one code.
     *
     * @throws IllegalArgumentException naming the first clash, the load and the capacity, or the
     *                                  stop whose time window is half or ends before it starts
     */
    public static void checkStops(List<Stop> stops, double capacityKg) {
        Map<String, String> seen = new HashMap<>();
        for (Stop stop : stops) {
            String code = stop.stopCode().trim();
            if ((stop.readyMinutes() == null) != (stop.dueMinutes() == null)) {
                throw new IllegalArgumentException("Stop " + code + " needs both readyMinutes and dueMinutes, or neither");
            }
            if (stop.hasTimeWindow() && stop.dueMinutes() < stop.readyMinutes()) {
                throw new IllegalArgumentException("The time window of stop " + code + " ends before it starts");
            }
            String earlier = seen.putIfAbsent(fileKey(code), code);
            if (earlier != null) {
                throw new IllegalArgumentException(earlier.equals(code)
                        ? "Stop code " + code + " appears more than once in this route"
                        : "Stop codes " + earlier + " and " + code + " are the same code in this route");
            }
        }
        double load = stops.stream().mapToDouble(Stop::demandWeightKg).sum();
        if (load > capacityKg) {
            throw new IllegalArgumentException("The stops need %.1f kg, more than the vehicle's %.1f kg"
                    .formatted(load, capacityKg));
        }
    }

    static String fileKey(String stopCode) {
        return stopCode.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    }

    /**
     * A plan from the planner's order. Not yet numbered: {@link #assignNumber} last, so a refused
     * plan uses up no number.
     *
     * @param route the stops in driving order; the same stops {@link #checkStops} accepted
     */
    public static RoutePlan of(Vehicle vehicle, List<ScheduledStop> route, int totalMinutes) {
        checkStops(route.stream().map(ScheduledStop::stop).toList(), vehicle.capacityKg());
        RoutePlan plan = new RoutePlan();
        plan.territoryCode = vehicle.territoryCode().trim().toUpperCase();
        plan.vehiclePlate = vehicle.vehiclePlate().trim().toUpperCase();
        plan.depotLatitude = vehicle.depotLatitude();
        plan.depotLongitude = vehicle.depotLongitude();
        plan.vehicleCapacityKg = vehicle.capacityKg();
        plan.averageSpeedKmh = vehicle.averageSpeedKmh();
        double load = 0;
        double distance = 0;
        for (ScheduledStop scheduled : route) {
            plan.stops.add(new RouteStop(plan.stops.size() + 1, scheduled.stop(), scheduled.legDistanceKm(),
                    scheduled.arrivalMinutes()));
            load += scheduled.stop().demandWeightKg();
            distance += scheduled.legDistanceKm();
        }
        plan.loadedWeightKg = round2(load);
        plan.capacityUtilizationPct = round2(load / vehicle.capacityKg() * 100.0);
        plan.totalDistanceKm = round2(distance);
        plan.totalMinutes = totalMinutes;
        return plan;
    }

    /** @param number the plan's serial number, from {@code geo_route_seq} */
    public void assignNumber(long number) {
        if (routeNumber != null) {
            throw new IllegalStateException("Route " + routeNumber + " is already numbered");
        }
        routeNumber = "RTE-" + number;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public UUID getId() {
        return id;
    }

    public String getRouteNumber() {
        return routeNumber;
    }

    public String getTerritoryCode() {
        return territoryCode;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public double getDepotLatitude() {
        return depotLatitude;
    }

    public double getDepotLongitude() {
        return depotLongitude;
    }

    public double getVehicleCapacityKg() {
        return vehicleCapacityKg;
    }

    public double getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    public double getLoadedWeightKg() {
        return loadedWeightKg;
    }

    public double getCapacityUtilizationPct() {
        return capacityUtilizationPct;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public List<RouteStop> getStops() {
        return List.copyOf(stops);
    }
}
