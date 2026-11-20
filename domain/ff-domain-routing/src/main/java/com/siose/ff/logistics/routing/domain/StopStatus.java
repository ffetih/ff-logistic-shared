package com.siose.ff.logistics.routing.domain;

/** Where a stop is: still to be driven to, or done. */
public enum StopStatus {

    /** On the route, not reached yet. */
    PLANNED,

    /** The goods were handed over and signed for. */
    COMPLETED
}
