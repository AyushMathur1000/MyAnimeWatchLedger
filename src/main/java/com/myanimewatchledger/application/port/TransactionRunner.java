package com.myanimewatchledger.application.port;

/** Executes a logical persistence unit of work atomically. */
public interface TransactionRunner {
    @FunctionalInterface
    interface Work<T> {
        T execute();
    }

    <T> T executeInTransaction(Work<T> work);
}
