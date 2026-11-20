package com.siose.ff.logistics.standards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siose.ff.logistics.standards.EmissionsCalculator.Footprint;
import com.siose.ff.logistics.standards.EmissionsCalculator.Leg;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmissionsCalculatorTest {

    @Test
    void intermodalChainSavesAgainstRoadOnly() {
        // 22 t from Istanbul: Ro-Ro to Trieste, block train to Cologne, the last 80 km by truck.
        Footprint footprint = EmissionsCalculator.footprint(22_000.0, List.of(
                new Leg(TransportMode.SEA_RORO, 1450.0),
                new Leg(TransportMode.RAIL, 1050.0),
                new Leg(TransportMode.ROAD, 80.0)));

        // 22 x (1450 x 31 + 1050 x 22 + 80 x 62) g = 1 606 220 g; road only 22 x 2580 x 62 g.
        assertThat(footprint.totalDistanceKm()).isEqualTo(2580.0);
        assertThat(footprint.grossWeightTonnes()).isEqualTo(22.0);
        assertThat(footprint.co2eKg()).isEqualTo(1606.22);
        assertThat(footprint.roadOnlyCo2eKg()).isEqualTo(3519.12);
        assertThat(footprint.savedCo2eKg()).isEqualTo(1912.9);
        assertThat(footprint.reductionPercent()).isEqualTo(54.36);
        assertThat(footprint.treeYearsSaved()).isEqualTo(87.87);
    }

    @Test
    void roadOnlySavesNothing() {
        Footprint footprint = EmissionsCalculator.footprint(10_000.0, List.of(new Leg(TransportMode.ROAD, 500.0)));

        assertThat(footprint.co2eKg()).isEqualTo(310.0);
        assertThat(footprint.savedCo2eKg()).isZero();
        assertThat(footprint.reductionPercent()).isZero();
    }

    @Test
    void needsALeg() {
        assertThatThrownBy(() -> EmissionsCalculator.footprint(1000.0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
