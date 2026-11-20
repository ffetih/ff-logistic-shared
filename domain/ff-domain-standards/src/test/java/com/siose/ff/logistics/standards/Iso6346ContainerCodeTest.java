package com.siose.ff.logistics.standards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Iso6346ContainerCodeTest {

    @Test
    void acceptsRealContainerCodes() {
        // The ISO 6346 example, and a container of a large carrier.
        assertThat(Iso6346ContainerCode.isValid("CSQU3054383")).isTrue();
        assertThat(Iso6346ContainerCode.isValid("MSKU9070323")).isTrue();
        assertThat(Iso6346ContainerCode.isValid(" csqu3054383 ")).isTrue();
    }

    @Test
    void refusesAWrongCheckDigitOrShape() {
        assertThat(Iso6346ContainerCode.isValid("CSQU3054389")).isFalse();
        assertThat(Iso6346ContainerCode.isValid("CSQX3054383")).isFalse();
        assertThat(Iso6346ContainerCode.isValid("CSQU305438")).isFalse();
        assertThat(Iso6346ContainerCode.isValid(null)).isFalse();
    }

    @Test
    void generatesAValidCode() {
        String code = Iso6346ContainerCode.generate("FFLU", 482_910);

        assertThat(code).isEqualTo("FFLU4829108");
        assertThat(Iso6346ContainerCode.isValid(code)).isTrue();
    }

    @Test
    void lettersSkipTheMultiplesOfEleven() {
        // L is 23 and V is 34, not 22 and 33: the codes below only check if the skip is right.
        assertThat(Iso6346ContainerCode.generate("LLLU", 1)).isEqualTo("LLLU0000015");
        assertThat(Iso6346ContainerCode.generate("VVVU", 1)).isEqualTo("VVVU0000015");
        assertThat(Iso6346ContainerCode.generate("ZZZU", 1)).isEqualTo("ZZZU0000010");
    }

    @Test
    void refusesABadPrefixOrSerial() {
        assertThatThrownBy(() -> Iso6346ContainerCode.generate("FFL", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iso6346ContainerCode.generate("FFLX", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iso6346ContainerCode.generate("FFLU", 1_000_000)).isInstanceOf(IllegalArgumentException.class);
    }
}
