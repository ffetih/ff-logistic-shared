package com.siose.ff.logistics.transportation.domain;

/** Where a leg's distance came from. */
public enum DistanceSource {

    /** The planner gave it. */
    GIVEN,

    /** Routing worked it out from the coordinates of the leg's two ends (flow 1). */
    ROUTING,

    /** Routing did not answer: the straight line between the two ends, times 1.24. */
    ESTIMATE
}
