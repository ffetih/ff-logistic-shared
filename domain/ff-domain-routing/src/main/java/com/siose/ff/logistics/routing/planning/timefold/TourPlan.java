package com.siose.ff.logistics.routing.planning.timefold;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardSoftScore;
import java.util.List;

/** What the solver works on: one tour, the stops to put on it, and how good the result is. */
@PlanningSolution
public class TourPlan {

    @PlanningEntityCollectionProperty
    private List<Tour> tours;

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<TourStop> stops;

    @PlanningScore
    private HardSoftScore score;

    /** For the solver, which clones the plan while it searches. */
    public TourPlan() {
    }

    public TourPlan(Tour tour, List<TourStop> stops) {
        this.tours = List.of(tour);
        this.stops = stops;
    }

    public List<Tour> getTours() {
        return tours;
    }

    public List<TourStop> getStops() {
        return stops;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
