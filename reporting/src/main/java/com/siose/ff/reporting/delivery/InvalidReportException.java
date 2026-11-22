package com.siose.ff.reporting.delivery;

/** A report-ready message that can never be delivered. It is not tried again. */
public class InvalidReportException extends RuntimeException {

    public InvalidReportException(String message) {
        super(message);
    }
}
