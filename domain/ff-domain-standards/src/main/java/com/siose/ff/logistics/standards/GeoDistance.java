package com.siose.ff.logistics.standards;

/**
 * Distances on the earth's surface, for planning before a road network is asked.
 *
 * <p>The great-circle distance is the shortest way over a sphere of the earth's mean radius
 * (haversine formula). Roads are longer: the straight line times {@link #ROAD_CIRCUITY} is the
 * usual estimate for road freight in Europe, where measured factors lie between about 1.2 and 1.3.
 */
public final class GeoDistance {

    /** Mean earth radius (IUGG), in km. */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    /** Road distance per km of straight line, as an estimate. */
    public static final double ROAD_CIRCUITY = 1.24;

    private GeoDistance() {
    }

    /** In km, from latitude and longitude in degrees. */
    public static double greatCircleKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double phi1 = Math.toRadians(fromLatitude);
        double phi2 = Math.toRadians(toLatitude);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.pow(Math.sin(dPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(dLambda / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** The great-circle distance times {@link #ROAD_CIRCUITY}, rounded to two decimals. */
    public static double estimatedRoadKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        return Math.round(greatCircleKm(fromLatitude, fromLongitude, toLatitude, toLongitude) * ROAD_CIRCUITY * 100.0)
                / 100.0;
    }

    /** Whole minutes, rounded up, to drive {@code km} at {@code averageSpeedKmh}. */
    public static int driveMinutes(double km, double averageSpeedKmh) {
        if (averageSpeedKmh <= 0) {
            throw new IllegalArgumentException("Average speed must be above 0 km/h");
        }
        return (int) Math.ceil(km / averageSpeedKmh * 60.0);
    }
}
