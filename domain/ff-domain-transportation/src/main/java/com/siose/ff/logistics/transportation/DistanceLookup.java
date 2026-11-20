package com.siose.ff.logistics.transportation;

/**
 * Flow 1: the road distance of a leg, asked of whichever area knows the roads (routing). A port of
 * this area's API: transportation declares the question, the implementation that runs it answers.
 * The modulith answers with a method call into routing; the transportation service asks the
 * routing service over HTTP.
 *
 * <p>An answer that cannot be had (routing down, slow, or without a road between the two points)
 * is an exception. The area then uses the straight-line estimate and records that it did
 * ({@code DistanceSource.ESTIMATE}): a shipment is never refused because routing did not answer.
 */
public interface DistanceLookup {

    /** Road kilometres from one point to the other, in degrees; above 0. */
    double roadKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude);
}
