package com.myanimewatchledger.infrastructure.mal;

/** Indicates that a MAL response cannot be safely interpreted as the expected contract. */
public final class MalResponseMappingException extends RuntimeException {
    public MalResponseMappingException(String message) {
        super(message);
    }

    public MalResponseMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
