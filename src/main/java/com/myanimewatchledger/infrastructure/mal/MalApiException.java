package com.myanimewatchledger.infrastructure.mal;

/** A safe, credential-free description of a failed MAL API request. */
public final class MalApiException extends RuntimeException {
    private final MalApiErrorCategory category;
    private final Integer statusCode;

    public MalApiException(MalApiErrorCategory category, Integer statusCode, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.statusCode = statusCode;
    }

    public MalApiErrorCategory category() {
        return category;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
