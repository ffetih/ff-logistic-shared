package com.siose.ff.reporting.delivery;

import com.siose.ff.reporting.generation.ReportReady;

/**
 * What delivery is allowed to know of a generated report: the event on
 * {@link ReportReady#TOPIC}. The listener that sends it is not in this slice.
 */
public final class AcceptedReport {

    private AcceptedReport() {
    }

    public static String topic() {
        return ReportReady.TOPIC;
    }
}
