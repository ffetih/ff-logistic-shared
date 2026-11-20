package com.siose.ff.logistics.standards;

/**
 * ISO 6346 freight container codes, the eleven characters painted on every intermodal box:
 * {@code CSQU 305438 3}.
 *
 * <ul>
 *   <li>three letters, the owner code, registered with the BIC</li>
 *   <li>one letter, the equipment category: {@code U} container, {@code J} detachable
 *       equipment, {@code Z} trailer or chassis</li>
 *   <li>six digits, the serial number</li>
 *   <li>one digit, the Modulo-11 check digit</li>
 * </ul>
 */
public final class Iso6346ContainerCode {

    private static final String PREFIX = "[A-Z]{3}[UJZ]";

    private Iso6346ContainerCode() {
    }

    /** True for a well-formed code with the right check digit. Case and outer spaces are ignored. */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.trim().toUpperCase();
        if (!normalized.matches(PREFIX + "\\d{7}")) {
            return false;
        }
        return checkDigit(normalized.substring(0, 10)) == normalized.charAt(10) - '0';
    }

    /**
     * A complete code from owner code plus category ({@code FFLU}) and a six-digit serial.
     */
    public static String generate(String ownerAndCategory, int serial) {
        if (ownerAndCategory == null || !ownerAndCategory.toUpperCase().matches(PREFIX)) {
            throw new IllegalArgumentException(
                    "Owner code and category must be three letters and U, J or Z, for example FFLU");
        }
        if (serial < 0 || serial > 999_999) {
            throw new IllegalArgumentException("Container serial must be 0 to 999999");
        }
        String first10 = ownerAndCategory.toUpperCase() + String.format("%06d", serial);
        return first10 + checkDigit(first10);
    }

    /**
     * Modulo-11 over the first ten characters. Each character has a value (digits their own,
     * letters from 10 upwards, skipping 11, 22 and 33), is multiplied by 2 to the power of its
     * position, and the check digit is the sum modulo 11. A remainder of 10 is written as 0;
     * owners avoid serial numbers that produce it, because 0 is then ambiguous.
     */
    public static int checkDigit(String first10) {
        if (first10 == null || !first10.toUpperCase().matches(PREFIX + "\\d{6}")) {
            throw new IllegalArgumentException("Expected owner code, category and six digits, for example FFLU482910");
        }
        String upper = first10.toUpperCase();
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = upper.charAt(i);
            int value = Character.isDigit(c) ? c - '0' : letterValue(c);
            sum += value << i;
        }
        return sum % 11 % 10;
    }

    /** A = 10, B = 12, ... Z = 38: the multiples of 11 are skipped. */
    private static int letterValue(char letter) {
        int value = 10 + (letter - 'A');
        for (int multiple = 11; multiple <= value; multiple += 11) {
            value++;
        }
        return value;
    }
}
