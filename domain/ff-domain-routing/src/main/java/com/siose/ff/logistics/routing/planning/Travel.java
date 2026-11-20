package com.siose.ff.logistics.routing.planning;

/**
 * Road distance and drive time between every pair of points of one route: the depot is index 0,
 * the stops are 1 to n in the order they were given. Not symmetric in general: one-way streets
 * and turns make A to B differ from B to A on a real road network.
 */
public final class Travel {

    private final double[][] kilometers;
    private final int[][] minutes;

    public Travel(double[][] kilometers, int[][] minutes) {
        this.kilometers = kilometers;
        this.minutes = minutes;
    }

    public double kilometers(int from, int to) {
        return kilometers[from][to];
    }

    public int minutes(int from, int to) {
        return minutes[from][to];
    }

    /** Points in the matrix: the depot and the stops. */
    public int size() {
        return kilometers.length;
    }
}
