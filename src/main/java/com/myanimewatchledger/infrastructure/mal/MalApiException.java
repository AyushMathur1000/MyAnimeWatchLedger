package com.myanimewatchledger.infrastructure.mal;

import java.time.Duration;

/** A safe, credential-free description of a failed MAL API request. */
public final class MalApiException extends RuntimeException {
    private final MalApiErrorCategory category;
    private final Integer statusCode;
    private final Duration retryAfter;

    public MalApiException(MalApiErrorCategory category, Integer statusCode, String message, Throwable cause) {
        this(category, statusCode, message, cause, null);
    }

    public MalApiException(
            MalApiErrorCategory category, Integer statusCode, String message,
            Throwable cause, Duration retryAfter) {
        super(message, cause);
        this.category = category;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
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
}
