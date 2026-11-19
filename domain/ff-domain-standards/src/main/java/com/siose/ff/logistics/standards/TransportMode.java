package com.siose.ff.logistics.standards;

/**
 * Transport modes with an average well-to-wheel emission factor in grams of CO2e per tonne-km,
 * the unit EN 16258 and the GLEC Framework calculate in. The factors are typical averages for
 * a practice project, not certified values: a real carrier uses its own measured figures.
 */
public enum TransportMode {

    ROAD(62.0, "Road, heavy goods vehicle"),
    RAIL(22.0, "Rail, intermodal block train"),
    SEA_RORO(31.0, "Sea, Ro-Ro or short-sea vessel"),
    INLAND_WATERWAY(28.0, "Inland waterway barge"),
    AIR(602.0, "Air cargo");

    private final double gramsCo2ePerTonneKm;
    private final String description;

    TransportMode(double gramsCo2ePerTonneKm, String description) {
        this.gramsCo2ePerTonneKm = gramsCo2ePerTonneKm;
        this.description = description;
    }

    public double gramsCo2ePerTonneKm() {
        return gramsCo2ePerTonneKm;
    }

    public String description() {
        return description;
    }
}
