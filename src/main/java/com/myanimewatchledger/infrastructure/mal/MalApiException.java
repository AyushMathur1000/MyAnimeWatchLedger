package com.myanimewatchledger.infrastructure.mal;

import java.time.Duration;

/** A safe, credential-free description of a failed MAL API request. */
public final class MalApiException extends RuntimeException {
    private static final int MAX_DIAGNOSTIC_BODY_LENGTH = 2_048;

    private final MalApiErrorCategory category;
    private final Integer statusCode;
    private final Duration retryAfter;
    private final String diagnosticBody;

    public MalApiException(MalApiErrorCategory category, Integer statusCode, String message, Throwable cause) {
        this(category, statusCode, message, cause, null, null);
    }

    public MalApiException(
            MalApiErrorCategory category, Integer statusCode, String message,
            Throwable cause, Duration retryAfter) {
        this(category, statusCode, message, cause, retryAfter, null);
    }

    public MalApiException(
            MalApiErrorCategory category, Integer statusCode, String message,
            Throwable cause, Duration retryAfter, String diagnosticBody) {
        super(message, cause);
        this.category = category;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        this.diagnosticBody = truncate(diagnosticBody);
    }

    public MalApiErrorCategory category() {
        return category;
    }

    public Integer statusCode() {
        return statusCode;
    }

    /** Server-provided retry delay, or null when no valid Retry-After value was supplied. */
    public Duration retryAfter() {
        return retryAfter;
    }

    /** Bounded response-body detail for diagnostics; never use this as a user-facing message. */
    public String diagnosticBody() {
        return diagnosticBody;
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= MAX_DIAGNOSTIC_BODY_LENGTH
                ? value
                : value.substring(0, MAX_DIAGNOSTIC_BODY_LENGTH);
    }
}
