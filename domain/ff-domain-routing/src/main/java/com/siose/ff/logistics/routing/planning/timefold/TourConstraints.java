package com.siose.ff.logistics.routing.planning.timefold;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

/**
 * Hard: no stop after its window closes, one point per minute late. Soft: the shortest road
 * distance, one point per metre. Any order that is on time beats every order that is late.
 */
public class TourConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            factory.forEach(Tour.class)
                    .filter(tour -> tour.latenessMinutes() > 0)
                    .penalize(HardSoftScore.ONE_HARD, Tour::latenessMinutes)
                    .asConstraint("arrival after the time window"),
            factory.forEach(Tour.class)
                    .penalize(HardSoftScore.ONE_SOFT, Tour::distanceMetres)
                    .asConstraint("road distance")
        };
    }
}
