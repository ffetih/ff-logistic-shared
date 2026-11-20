package com.siose.ff.logistics.standards;

/**
 * The three GS1 identifiers the platform uses. All of them end in the same Modulo-10 check
 * digit, so one routine checks them all.
 *
 * <ul>
 *   <li>GLN-13, Global Location Number: a warehouse, a dock, a hub, a customer site.</li>
 *   <li>GTIN-8/12/13/14, Global Trade Item Number: a trade item, what a barcode on a box says.</li>
 *   <li>SSCC-18, Serial Shipping Container Code: one pallet or other handling unit, printed on
 *       its label. Extension digit, GS1 company prefix, serial reference, check digit.</li>
 * </ul>
 */
public final class Gs1Standards {

    private static final int SSCC_LENGTH = 18;

    private Gs1Standards() {
    }

    /**
     * An SSCC for one handling unit.
     *
     * @param extensionDigit  0 to 9, chosen by the company, often to widen its serial range
     * @param companyPrefix   the GS1 company prefix, 6 to 10 digits
     * @param serialReference a number the company never reuses for this prefix; it fills the
     *                        digits the prefix leaves free, so a longer prefix leaves fewer
     */
    public static String generateSscc18(int extensionDigit, String companyPrefix, long serialReference) {
        if (extensionDigit < 0 || extensionDigit > 9) {
            throw new IllegalArgumentException("SSCC extension digit must be between 0 and 9");
        }
        if (companyPrefix == null || !companyPrefix.matches("\\d{6,10}")) {
            throw new IllegalArgumentException("GS1 company prefix must be 6 to 10 digits");
        }
        if (serialReference < 0) {
            throw new IllegalArgumentException("SSCC serial reference must not be negative");
        }
        int serialLength = SSCC_LENGTH - 2 - companyPrefix.length();
        String serial = String.format("%0" + serialLength + "d", serialReference);
        if (serial.length() > serialLength) {
            throw new IllegalArgumentException("SSCC serial " + serialReference + " does not fit in "
                    + serialLength + " digits after GS1 company prefix " + companyPrefix);
        }
        String payload = extensionDigit + companyPrefix + serial;
        return payload + checkDigit(payload);
    }

    public static boolean isValidSscc18(String sscc) {
        return sscc != null && sscc.matches("\\d{18}") && hasValidCheckDigit(sscc);
    }

    public static boolean isValidGln13(String gln) {
        return gln != null && gln.matches("\\d{13}") && hasValidCheckDigit(gln);
    }

    public static boolean isValidGtin(String gtin) {
        return gtin != null && gtin.matches("\\d{8}|\\d{12}|\\d{13}|\\d{14}") && hasValidCheckDigit(gtin);
    }

    /** True if the last digit is the GS1 check digit of the digits before it. */
    public static boolean hasValidCheckDigit(String digitsWithCheck) {
        if (digitsWithCheck == null || digitsWithCheck.length() < 2 || !digitsWithCheck.matches("\\d+")) {
            return false;
        }
        int last = digitsWithCheck.length() - 1;
        return checkDigit(digitsWithCheck.substring(0, last)) == digitsWithCheck.charAt(last) - '0';
    }

    /**
     * GS1 Modulo-10: from the right, the digits are weighted 3, 1, 3, 1, ...; the check digit
     * brings the weighted sum up to the next multiple of ten. The weights start from the right
     * so that every GS1 key, whatever its length, uses the same rule.
     */
    public static int checkDigit(String payloadDigits) {
        if (payloadDigits == null || payloadDigits.isEmpty() || !payloadDigits.matches("\\d+")) {
            throw new IllegalArgumentException("GS1 payload must contain only digits");
        }
        int sum = 0;
        for (int i = 0; i < payloadDigits.length(); i++) {
            int digit = payloadDigits.charAt(payloadDigits.length() - 1 - i) - '0';
            sum += digit * (i % 2 == 0 ? 3 : 1);
        }
        return (10 - sum % 10) % 10;
    }
}
