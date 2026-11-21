package com.siose.ff.analytics.loader;

/**
 * A message that can never be loaded, however often it is retried: not JSON, a field missing,
 * a tenant id in the wrong format. It goes to the dead-letter topic at once.
 */
public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
