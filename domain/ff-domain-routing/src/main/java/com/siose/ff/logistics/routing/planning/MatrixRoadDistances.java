package com.siose.ff.logistics.routing.planning;

import com.siose.ff.logistics.routing.RoadDistances;
import java.util.List;
import org.springframework.stereotype.Component;

/** A route of one stop, read off the road matrix in use. */
@Component
class MatrixRoadDistances implements RoadDistances {

    /** Only the drive times depend on it, and only the distance is read. */
    private static final double ANY_SPEED_KMH = 60;

    private final RoadMatrix roads;

    MatrixRoadDistances(RoadMatrix roads) {
        this.roads = roads;
    }

    @Override
    public double roadKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        return roads.between(new Location(fromLatitude, fromLongitude),
                List.of(new Location(toLatitude, toLongitude)), ANY_SPEED_KMH).kilometers(0, 1);
    }
}
