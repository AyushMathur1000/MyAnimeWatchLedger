package com.myanimewatchledger.infrastructure.persistence;

/** Indicates that the local database could not safely complete an operation. */
public final class DatabaseException extends RuntimeException {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
