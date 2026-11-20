package com.siose.ff.logistics.routing.api;

import com.siose.ff.logistics.routing.RoadDistances;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flow 1 over HTTP: the transportation service asks here for a road leg's distance, with the
 * planner's own token. Planners may ask, not only dispatchers: a distance is no tenant's data.
 */
@RestController
@RequestMapping("/api/v1/geo/distance")
@PreAuthorize("hasAnyRole('LOGISTICS_PLANNER', 'DISPATCHER', 'TENANT_ADMIN')")
public class DistanceController {

    private final RoadDistances distances;

    public DistanceController(RoadDistances distances) {
        this.distances = distances;
    }

    public record RoadDistanceView(double distanceKm) {
    }

    /** 503 {@code road-matrix-unavailable} when the road network service does not answer. */
    @GetMapping
    public RoadDistanceView between(@RequestParam double fromLatitude, @RequestParam double fromLongitude,
            @RequestParam double toLatitude, @RequestParam double toLongitude) {
        checkPoint(fromLatitude, fromLongitude);
        checkPoint(toLatitude, toLongitude);
        return new RoadDistanceView(distances.roadKm(fromLatitude, fromLongitude, toLatitude, toLongitude));
    }

    private static void checkPoint(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("A point is a latitude from -90 to 90 and a longitude from -180 to 180");
        }
    }
}
