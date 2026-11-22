package com.siose.ff.reporting.delivery;

/** A send that failed for a reason that may pass. The listener tries it again, then dead-letters it. */
public class DeliveryFailedException extends RuntimeException {

    public DeliveryFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
