package com.siose.ff.logistics.routing.planning;

import com.siose.ff.base.web.DependencyUnavailableException;

/** The road network service did not give a usable answer: 503 {@code road-matrix-unavailable}. */
public class RoadMatrixUnavailableException extends DependencyUnavailableException {

    public RoadMatrixUnavailableException(String message) {
        super(message);
    }

    public RoadMatrixUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String problemSlug() {
        return "road-matrix-unavailable";
    }

    @Override
    public String problemTitle() {
        return "Road Matrix Unavailable";
    }
}
