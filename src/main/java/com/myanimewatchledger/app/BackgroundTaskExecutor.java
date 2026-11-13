package com.myanimewatchledger.app;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Owns background operations and waits for them before application shutdown. */
public final class BackgroundTaskExecutor implements AutoCloseable {
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);
    private final ExecutorService executor;
    private volatile boolean closed;

    public BackgroundTaskExecutor(int threadCount) {
        if (threadCount < 1) throw new IllegalArgumentException("Thread count must be positive.");
        AtomicInteger sequence = new AtomicInteger();
        executor = Executors.newFixedThreadPool(threadCount, task ->
                new Thread(task, "malw-background-" + sequence.incrementAndGet()));
    }

    public <T> CompletableFuture<T> submit(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (closed) throw new IllegalStateException("Background task executor is closed.");
        return CompletableFuture.supplyAsync(operation, executor);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        executor.shutdown();
        boolean interrupted = false;
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Background tasks did not stop within the shutdown timeout.");
                }
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Background tasks did not stop after shutdown interruption.");
                }
            } catch (InterruptedException secondInterruption) {
                exception.addSuppressed(secondInterruption);
                throw new IllegalStateException("Interrupted while stopping background tasks.", exception);
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
