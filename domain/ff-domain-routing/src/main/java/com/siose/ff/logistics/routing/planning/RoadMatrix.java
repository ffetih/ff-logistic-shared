package com.siose.ff.logistics.routing.planning;

import java.util.List;

/** Where the road distances and drive times of a route come from. */
public interface RoadMatrix {

    /**
     * @param depot           index 0 of the result
     * @param stops           indexes 1 to n, in this order
     * @param averageSpeedKmh for drive times, where the source does not know them itself
     */
    Travel between(Location depot, List<Location> stops, double averageSpeedKmh);
}
