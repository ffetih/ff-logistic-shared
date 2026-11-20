package com.siose.ff.logistics.standards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The examples come from the GS1 General Specifications and from real products. */
class Gs1StandardsTest {

    @Test
    void ssccMatchesTheGs1Example() {
        // Extension digit 1, company prefix 0614141, serial reference 123456789.
        assertThat(Gs1Standards.generateSscc18(1, "0614141", 123_456_789L)).isEqualTo("106141411234567897");
    }

    @Test
    void ssccPadsTheSerialAndValidates() {
        String sscc = Gs1Standards.generateSscc18(3, "8690001", 42L);

        assertThat(sscc).isEqualTo("386900010000000425");
        assertThat(Gs1Standards.isValidSscc18(sscc)).isTrue();
        assertThat(Gs1Standards.isValidSscc18("386900010000000426")).isFalse();
        assertThat(Gs1Standards.isValidSscc18("38690001000000042")).isFalse();
    }

    @Test
    void ssccRefusesASerialThatDoesNotFit() {
        // A 7-digit prefix leaves 9 digits for the serial.
        assertThatThrownBy(() -> Gs1Standards.generateSscc18(3, "8690001", 1_000_000_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not fit");
        assertThatThrownBy(() -> Gs1Standards.generateSscc18(3, "8690001", -1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gs1Standards.generateSscc18(10, "8690001", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gs1Standards.generateSscc18(3, "86900", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void glnAndGtinUseTheSameCheckDigit() {
        assertThat(Gs1Standards.isValidGln13("0614141000012")).isTrue();
        assertThat(Gs1Standards.isValidGln13("0614141000013")).isFalse();

        assertThat(Gs1Standards.isValidGtin("4006381333931")).isTrue();
        assertThat(Gs1Standards.isValidGtin("4006381333932")).isFalse();
        assertThat(Gs1Standards.isValidGtin("00012345600012")).isTrue();
        assertThat(Gs1Standards.isValidGtin("40063813339")).isFalse();
    }

    @Test
    void checkDigitRefusesNonDigits() {
        assertThat(Gs1Standards.checkDigit("061414100001")).isEqualTo(2);
        assertThat(Gs1Standards.hasValidCheckDigit("06141410000A2")).isFalse();
        assertThat(Gs1Standards.hasValidCheckDigit(null)).isFalse();
        assertThatThrownBy(() -> Gs1Standards.checkDigit("12a4")).isInstanceOf(IllegalArgumentException.class);
    }
}
