package com.siose.ff.logistics.routing.planning;

import java.util.List;

/**
 * The stops in driving order.
 *
 * @param totalMinutes from departure until the service at the last stop ends
 */
public record PlannedRoute(List<PlannedStop> stops, int totalMinutes) {

    /**
     * @param stopIndex     the stop's position in the list the planner was given, from 0
     * @param legKm         from the depot or the stop before
     * @param arrivalMinutes after departure
     */
    public record PlannedStop(int stopIndex, double legKm, int arrivalMinutes) {
    }
}
