package com.siose.ff.logistics.standards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    @Test
    void greatCircleBetweenParisAndLondon() {
        // The textbook example: about 343.6 km on a sphere of the mean radius.
        assertThat(GeoDistance.greatCircleKm(48.8566, 2.3522, 51.5074, -0.1278)).isCloseTo(343.56, within(0.01));
    }

    @Test
    void roadEstimateIsTheStraightLineTimesTheCircuity() {
        // Istanbul to Ankara: 349.36 km straight, 433.20 km estimated by road (the motorway is about 450).
        assertThat(GeoDistance.estimatedRoadKm(41.0082, 28.9784, 39.9334, 32.8597)).isEqualTo(433.2);
    }

    @Test
    void samePointIsZero() {
        assertThat(GeoDistance.greatCircleKm(41.0, 29.0, 41.0, 29.0)).isZero();
    }

    @Test
    void driveMinutesRoundUp() {
        assertThat(GeoDistance.driveMinutes(50.0, 60.0)).isEqualTo(50);
        assertThat(GeoDistance.driveMinutes(50.1, 60.0)).isEqualTo(51);
        assertThatThrownBy(() -> GeoDistance.driveMinutes(10.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
