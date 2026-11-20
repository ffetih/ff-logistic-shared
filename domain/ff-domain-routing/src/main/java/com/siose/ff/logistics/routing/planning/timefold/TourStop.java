package com.siose.ff.logistics.routing.planning.timefold;

import ai.timefold.solver.core.api.domain.common.PlanningId;

/**
 * A stop the solver places on the tour: a problem fact, it does not change while solving.
 *
 * @param index        the stop's index in the {@code Travel} matrix (the depot is 0)
 * @param readyMinutes earliest service start; 0 for a stop without a window
 * @param dueMinutes   latest arrival; {@link Integer#MAX_VALUE} for a stop without a window
 */
public record TourStop(@PlanningId Integer index, int readyMinutes, int dueMinutes, int serviceMinutes) {
}
