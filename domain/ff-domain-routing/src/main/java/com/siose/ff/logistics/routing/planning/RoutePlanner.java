package com.siose.ff.logistics.routing.planning;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.siose.ff.logistics.routing.planning.timefold.Tour;
import com.siose.ff.logistics.routing.planning.timefold.TourConstraints;
import com.siose.ff.logistics.routing.planning.timefold.TourPlan;
import com.siose.ff.logistics.routing.planning.timefold.TourStop;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Puts a route's stops in driving order ({@code BR-GEO-04}) and times them.
 *
 * <p>A short list without time windows: nearest neighbour, from the depot always to the closest
 * stop not yet visited. Quick, and for a van's few stops usually close to the best order.
 *
 * <p>A longer list, or any stop with a time window: Timefold. Every stop on time first (hard),
 * then the shortest road distance (soft). The solver stops when its best order has not improved
 * for {@code solver-unimproved-limit}, and at the latest after {@code solver-limit}: a small route
 * is answered in a fraction of the limit, and a hard one cannot hold the request longer.
 */
@Component
public class RoutePlanner {

    private final RoadMatrix roads;
    private final int nearestNeighbourMaxStops;
    private final SolverFactory<TourPlan> solvers;

    public RoutePlanner(RoadMatrix roads,
            @Value("${ff.routing.nearest-neighbour-max-stops:8}") int nearestNeighbourMaxStops,
            @Value("${ff.routing.solver-limit:PT1S}") Duration solverLimit,
            @Value("${ff.routing.solver-unimproved-limit:PT0.2S}") Duration unimprovedLimit) {
        this.roads = roads;
        this.nearestNeighbourMaxStops = nearestNeighbourMaxStops;
        this.solvers = SolverFactory.create(new SolverConfig()
                .withSolutionClass(TourPlan.class)
                .withEntityClasses(Tour.class)
                .withConstraintProviderClass(TourConstraints.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(solverLimit)
                        .withUnimprovedSpentLimit(unimprovedLimit)));
    }

    /**
     * A stop as the planner sees it: where it is, how long the vehicle stays, and its time window
     * in minutes after departure (both null, or both set).
     */
    public record Visit(Location location, int serviceMinutes, Integer readyMinutes, Integer dueMinutes) {

        boolean hasTimeWindow() {
            return readyMinutes != null;
        }
    }

    /** @throws IllegalArgumentException if no order brings every stop inside its time window */
    public PlannedRoute plan(Location depot, List<Visit> visits, double averageSpeedKmh) {
        Travel travel = roads.between(depot, visits.stream().map(Visit::location).toList(), averageSpeedKmh);
        boolean windows = visits.stream().anyMatch(Visit::hasTimeWindow);
        List<Integer> order = !windows && visits.size() <= nearestNeighbourMaxStops
                ? nearestNeighbour(travel, visits.size())
                : solve(travel, visits);
        return schedule(order, visits, travel);
    }

    /** Stop indexes from 0, in driving order. */
    static List<Integer> nearestNeighbour(Travel travel, int stopCount) {
        boolean[] visited = new boolean[stopCount];
        List<Integer> order = new ArrayList<>(stopCount);
        int at = 0;
        while (order.size() < stopCount) {
            int next = -1;
            for (int stop = 0; stop < stopCount; stop++) {
                if (!visited[stop] && (next < 0 || travel.kilometers(at, stop + 1) < travel.kilometers(at, next + 1))) {
                    next = stop;
                }
            }
            visited[next] = true;
            order.add(next);
            at = next + 1;
        }
        return order;
    }

    private List<Integer> solve(Travel travel, List<Visit> visits) {
        List<TourStop> stops = IntStream.range(0, visits.size())
                .mapToObj(i -> {
                    Visit visit = visits.get(i);
                    return visit.hasTimeWindow()
                            ? new TourStop(i + 1, visit.readyMinutes(), visit.dueMinutes(), visit.serviceMinutes())
                            : new TourStop(i + 1, 0, Integer.MAX_VALUE, visit.serviceMinutes());
                })
                .toList();
        TourPlan solved = solvers.buildSolver().solve(new TourPlan(new Tour("vehicle", travel), new ArrayList<>(stops)));
        if (solved.getScore() == null || !solved.getScore().isFeasible()) {
            throw new IllegalArgumentException("No order of the stops keeps every stop inside its time window");
        }
        return solved.getTours().getFirst().getStops().stream().map(stop -> stop.index() - 1).toList();
    }

    /**
     * Arrival at each stop: the drive from the one before, after its service ends; a vehicle that
     * comes before the window opens waits, and the service starts when it does.
     */
    static PlannedRoute schedule(List<Integer> order, List<Visit> visits, Travel travel) {
        List<PlannedRoute.PlannedStop> stops = new ArrayList<>(order.size());
        int at = 0;
        int clock = 0;
        for (int stop : order) {
            Visit visit = visits.get(stop);
            int arrival = clock + travel.minutes(at, stop + 1);
            if (visit.hasTimeWindow()) {
                arrival = Math.max(arrival, visit.readyMinutes());
            }
            stops.add(new PlannedRoute.PlannedStop(stop, travel.kilometers(at, stop + 1), arrival));
            clock = arrival + visit.serviceMinutes();
            at = stop + 1;
        }
        return new PlannedRoute(List.copyOf(stops), clock);
    }
}
