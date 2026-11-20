package com.siose.ff.logistics.routing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Builds the geo requests the tests send. */
public final class GeoRequests {

    /** A valid GLN-13 (GS1 check digit 5) for the territory's hub. */
    public static final String HUB_GLN = "8690123450015";

    private GeoRequests() {
    }

    public static MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Istanbul's European side around the Hadimkoy hub: 35 km, 420 delivery points, 840 visits a week. */
    public static String territory(String code, String hubGln) {
        return """
                {"territoryCode": "%s", "name": "Istanbul Europe", "hubGln": "%s",
                 "centerLatitude": 41.05, "centerLongitude": 28.85, "coverageRadiusKm": 35,
                 "activePointCount": 420, "weeklyVisitTarget": 840}
                """.formatted(code, hubGln);
    }

    /**
     * A van from Hadimkoy with four stops, listed in the reverse of the order it will drive them:
     * the nearest-neighbour order is Esenyurt, Bakirkoy, Besiktas, Kadikoy.
     */
    public static String route(String territoryCode, String stops) {
        return """
                {"territoryCode": "%s", "vehiclePlate": "34 ff 0417", "depotLatitude": 41.10, "depotLongitude": 28.65,
                 "vehicleCapacityKg": 3500, "averageSpeedKmh": 40, "stops": [%s]}
                """.formatted(territoryCode, stops);
    }

    public static String fourStops() {
        return String.join(",",
                stop("IST-KDK", "Kadikoy Market", 40.99, 29.03, 400),
                stop("IST-BSK", "Besiktas Pharmacy", 41.043, 29.007, 300),
                stop("IST-BKY", "Bakirkoy Bakery", 40.98, 28.87, 500),
                stop("IST-ESN", "Esenyurt Depot Shop", 41.03, 28.67, 200));
    }

    /** A stop with a time window, in minutes after departure. */
    public static String stop(String code, String customer, double latitude, double longitude, double demandKg,
            Integer readyMinutes, Integer dueMinutes) {
        return stop(code, customer, latitude, longitude, demandKg)
                .replace("}", ", \"readyMinutes\": " + readyMinutes + ", \"dueMinutes\": " + dueMinutes + "}");
    }

    public static String stop(String code, String customer, double latitude, double longitude, double demandKg) {
        return """
                {"stopCode": "%s", "customerName": "%s", "latitude": %s, "longitude": %s,
                 "demandWeightKg": %s, "serviceMinutes": 15}""".formatted(code, customer, latitude, longitude, demandKg);
    }
}
