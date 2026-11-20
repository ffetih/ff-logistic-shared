package com.siose.ff.logistics.routing;

/**
 * The road distance between two points, from the road matrix routing plans with (OSRM, or the
 * straight line times 1.24). Part of the area's API: another area asks it (flow 1, transportation's
 * legs) through the implementation's wiring, a method call in the modulith, and over HTTP
 * ({@code GET /api/v1/geo/distance}) between the services.
 */
public interface RoadDistances {

    /**
     * Kilometres by road, in degrees.
     *
     * @throws com.siose.ff.logistics.routing.planning.RoadMatrixUnavailableException if the road
     *         network service does not answer
     */
    double roadKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude);
}
