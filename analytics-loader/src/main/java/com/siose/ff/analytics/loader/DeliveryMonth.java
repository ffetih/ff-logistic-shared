package com.siose.ff.analytics.loader;

/**
 * Which month a delivery counts for: the UTC calendar month of {@code delivered_at}. The one place
 * that says so, as SQL, for both the mark that a month changed ({@link DeliveryStore}) and the
 * recount ({@link MonthlyDeliveryRollup}), so the two always agree.
 *
 * <p>UTC, not the tenant's time zone: a delivery at 01:30 on 1 November in Istanbul (22:30 UTC on
 * 31 October) counts for October. One rule for every tenant; a report in local months would need
 * the tenant's zone, which the platform does not record yet.
 */
final class DeliveryMonth {

    private DeliveryMonth() {
    }

    /** The month, as a date on its first day, of the timestamp {@code column}. */
    static String of(String column) {
        return "date_trunc('month', " + column + " at time zone 'UTC')::date";
    }

    /** The first moment of the month {@code month} (a date column), as a timestamp with time zone. */
    static String start(String month) {
        return "(" + month + "::timestamp at time zone 'UTC')";
    }

    /** The first moment after the month {@code month}. */
    static String end(String month) {
        return "((" + month + " + interval '1 month')::timestamp at time zone 'UTC')";
    }
}
