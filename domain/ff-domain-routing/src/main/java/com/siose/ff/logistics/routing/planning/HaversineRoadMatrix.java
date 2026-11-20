package com.siose.ff.logistics.routing.planning;

import com.siose.ff.logistics.standards.GeoDistance;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code BR-GEO-05}: the straight line times 1.24 as the road distance, and the drive time at the
 * requested average speed. Needs nothing outside the app; the default.
 */
@Component
@ConditionalOnProperty(name = "ff.routing.road-matrix", havingValue = "haversine", matchIfMissing = true)
public class HaversineRoadMatrix implements RoadMatrix {

    @Override
    public Travel between(Location depot, List<Location> stops, double averageSpeedKmh) {
        List<Location> points = new ArrayList<>(stops.size() + 1);
        points.add(depot);
        points.addAll(stops);
        int n = points.size();
        double[][] kilometers = new double[n][n];
        int[][] minutes = new int[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                if (from != to) {
                    Location a = points.get(from);
                    Location b = points.get(to);
                    kilometers[from][to] = GeoDistance.estimatedRoadKm(a.latitude(), a.longitude(), b.latitude(),
                            b.longitude());
                    minutes[from][to] = GeoDistance.driveMinutes(kilometers[from][to], averageSpeedKmh);
                }
            }
        }
        return new Travel(kilometers, minutes);
    }
}
