package com.siose.ff.logistics.routing.planning.timefold;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import com.siose.ff.logistics.routing.planning.Travel;
import java.util.ArrayList;
import java.util.List;

/** The one vehicle's tour: the list of stops the solver fills and reorders. */
@PlanningEntity
public class Tour {

    @PlanningId
    private String id;

    private Travel travel;

    @PlanningListVariable
    private List<TourStop> stops = new ArrayList<>();

    /** For the solver, which clones the tour while it searches. */
    public Tour() {
    }

    public Tour(String id, Travel travel) {
        this.id = id;
        this.travel = travel;
    }

    /**
     * Minutes after the windows, added up over the stops. The same clock as the plan's: drive,
     * wait for the window to open if early, serve, drive on.
     */
    public long latenessMinutes() {
        long late = 0;
        long clock = 0;
        int at = 0;
        for (TourStop stop : stops) {
            long arrival = Math.max(clock + travel.minutes(at, stop.index()), stop.readyMinutes());
            late += Math.max(0, clock + travel.minutes(at, stop.index()) - stop.dueMinutes());
            clock = arrival + stop.serviceMinutes();
            at = stop.index();
        }
        return late;
    }

    public long distanceMetres() {
        long metres = 0;
        int at = 0;
        for (TourStop stop : stops) {
            metres += Math.round(travel.kilometers(at, stop.index()) * 1000.0);
            at = stop.index();
        }
        return metres;
    }

    public String getId() {
        return id;
    }

    public Travel getTravel() {
        return travel;
    }

    public List<TourStop> getStops() {
        return stops;
    }

    public void setStops(List<TourStop> stops) {
        this.stops = stops;
    }
}
