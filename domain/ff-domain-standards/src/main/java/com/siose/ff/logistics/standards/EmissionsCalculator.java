package com.siose.ff.logistics.standards;

import java.util.List;

/**
 * Greenhouse gas emissions of a transport chain, the EN 16258 way: for every leg, distance
 * times weight times the mode's factor, added up. ISO 14083 (2023) has since replaced EN 16258
 * and the GLEC Framework follows it; the leg-by-leg sum is the same in both.
 *
 * <p>The result also says what the same cargo would have emitted by road all the way, the
 * comparison a customer asks for when it chooses rail or sea for part of the route.
 */
public final class EmissionsCalculator {

    /** CO2 a mature tree takes up in a year, in kg: a figure people can picture. */
    private static final double KG_CO2_PER_TREE_YEAR = 21.77;

    private EmissionsCalculator() {
    }

    public record Leg(TransportMode mode, double distanceKm) {
    }

    /**
     * Everything rounded to two decimals; weights in tonnes, emissions in kg CO2e.
     */
    public record Footprint(
            double totalDistanceKm,
            double grossWeightTonnes,
            double co2eKg,
            double roadOnlyCo2eKg,
            double savedCo2eKg,
            double reductionPercent,
            double treeYearsSaved) {
    }

    public static Footprint footprint(double grossWeightKg, List<Leg> legs) {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("A transport chain needs at least one leg");
        }
        double tonnes = Math.max(0.001, grossWeightKg / 1000.0);
        double distanceKm = 0.0;
        double grams = 0.0;
        for (Leg leg : legs) {
            double legKm = Math.max(0.0, leg.distanceKm());
            distanceKm += legKm;
            grams += legKm * tonnes * leg.mode().gramsCo2ePerTonneKm();
        }

        double co2eKg = round2(grams / 1000.0);
        double roadOnlyKg = round2(distanceKm * tonnes * TransportMode.ROAD.gramsCo2ePerTonneKm() / 1000.0);
        double savedKg = round2(Math.max(0.0, roadOnlyKg - co2eKg));
        double reductionPercent = roadOnlyKg > 0 ? round2(savedKg / roadOnlyKg * 100.0) : 0.0;

        return new Footprint(round2(distanceKm), round2(tonnes), co2eKg, roadOnlyKg, savedKg,
                reductionPercent, round2(savedKg / KG_CO2_PER_TREE_YEAR));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
