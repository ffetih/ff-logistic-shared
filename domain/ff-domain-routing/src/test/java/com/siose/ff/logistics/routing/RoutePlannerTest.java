package com.siose.ff.logistics.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siose.ff.logistics.routing.planning.HaversineRoadMatrix;
import com.siose.ff.logistics.routing.planning.Location;
import com.siose.ff.logistics.routing.planning.PlannedRoute;
import com.siose.ff.logistics.routing.planning.RoutePlanner;
import com.siose.ff.logistics.routing.planning.RoutePlanner.Visit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RoutePlannerTest {

    private final RoutePlanner planner = new RoutePlanner(new HaversineRoadMatrix(), 8, Duration.ofSeconds(1), Duration.ofMillis(200));

    private static final Location DEPOT = new Location(41.0, 29.0);

    @Test
    void drivesToTheNearestStopNext() {
        // Three stops due east of the depot, listed far to near.
        List<Visit> visits = List.of(
                new Visit(new Location(41.0, 29.3), 0, null, null),
                new Visit(new Location(41.0, 29.1), 0, null, null),
                new Visit(new Location(41.0, 29.2), 0, null, null));

        PlannedRoute route = planner.plan(DEPOT, visits, 60);

        assertThat(route.stops()).extracting(PlannedRoute.PlannedStop::stopIndex).containsExactly(1, 2, 0);
    }

    @Test
    void arrivalCountsTheServiceAtTheStopBefore() {
        List<Visit> visits = List.of(new Visit(new Location(41.0, 29.1), 30, null, null), new Visit(new Location(41.0, 29.2), 10, null, null));

        PlannedRoute route = planner.plan(DEPOT, visits, 60);

        // Each leg: 8.39 km straight x 1.24 = 10.41 km, 11 minutes at 60 km/h.
        assertThat(route.stops()).extracting(PlannedRoute.PlannedStop::arrivalMinutes).containsExactly(11, 11 + 30 + 11);
        assertThat(route.totalMinutes()).isEqualTo(11 + 30 + 11 + 10);
    }

    // Below: Timefold. Each leg 0.1 degree east is 10.41 km by the estimate, 11 minutes at 60 km/h.

    @Test
    void aTimeWindowPutsTheFarStopFirst() {
        // Nearest first would reach the far stop at 11 + 5 + 11 = 27, after its window closes at 25.
        List<Visit> visits = List.of(
                new Visit(new Location(41.0, 29.1), 5, null, null),
                new Visit(new Location(41.0, 29.2), 5, 0, 25));

        PlannedRoute route = planner.plan(DEPOT, visits, 60);

        assertThat(route.stops()).extracting(PlannedRoute.PlannedStop::stopIndex).containsExactly(1, 0);
        assertThat(route.stops()).extracting(PlannedRoute.PlannedStop::arrivalMinutes).containsExactly(21, 37);
    }

    @Test
    void anEarlyVehicleWaitsForTheWindow() {
        PlannedRoute route = planner.plan(DEPOT, List.of(new Visit(new Location(41.0, 29.1), 10, 60, 90)), 60);

        assertThat(route.stops().getFirst().arrivalMinutes()).isEqualTo(60);
        assertThat(route.totalMinutes()).isEqualTo(70);
    }

    @Test
    void refusesAWindowNoOrderCanMeet() {
        List<Visit> visits = List.of(new Visit(new Location(41.0, 29.2), 5, 0, 10));

        assertThatThrownBy(() -> planner.plan(DEPOT, visits, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No order of the stops keeps every stop inside its time window");
    }

    @Test
    void ordersALongListByDistance() {
        // Twelve stops on a line east of the depot, given shuffled: the shortest open tour drives them in order.
        List<Integer> shuffled = new ArrayList<>(IntStream.rangeClosed(1, 12).boxed().toList());
        Collections.shuffle(shuffled, new Random(7));
        List<Visit> visits = shuffled.stream()
                .map(k -> new Visit(new Location(41.0, 29.0 + 0.05 * k), 5, null, null))
                .toList();

        PlannedRoute route = planner.plan(DEPOT, visits, 60);

        assertThat(route.stops()).extracting(stop -> shuffled.get(stop.stopIndex()))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }
}
