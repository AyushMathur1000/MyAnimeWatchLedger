package com.myanimewatchledger.infrastructure.mal;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Shared MAL transport policy: one request in flight, duplicate GET coalescing,
 * and bounded retries for transient failures. Use one instance for the whole app.
 */
public final class ResilientMalApiTransport implements MalApiTransport {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(8);

    private final MalApiTransport delegate;
    private final Semaphore requestPermit = new Semaphore(1, true);
    private final ConcurrentMap<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Sleeper sleeper;
    private final DoubleSupplier jitterMultiplier;

    public ResilientMalApiTransport(MalApiTransport delegate) {
        this(delegate, DEFAULT_MAX_ATTEMPTS, INITIAL_BACKOFF, MAX_BACKOFF,
                Thread::sleep, () -> 0.5 + ThreadLocalRandom.current().nextDouble());
    }

    ResilientMalApiTransport(
            MalApiTransport delegate,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            Sleeper sleeper,
            DoubleSupplier jitterMultiplier) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxAttempts < 1) throw new IllegalArgumentException("At least one request attempt is required.");
        this.maxAttempts = maxAttempts;
        this.initialBackoff = positive(initialBackoff, "initialBackoff");
        this.maxBackoff = positive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must not be smaller than initialBackoff.");
        }
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.jitterMultiplier = Objects.requireNonNull(jitterMultiplier, "jitterMultiplier");
    }

    @Override
    public String get(String relativePathAndQuery) {
        Objects.requireNonNull(relativePathAndQuery, "relativePathAndQuery");
        return coalesce("GET " + relativePathAndQuery, () -> delegate.get(relativePathAndQuery));
    }

    @Override
    public String getNextPage(URI nextPageUri) {
        Objects.requireNonNull(nextPageUri, "nextPageUri");
        URI normalized = nextPageUri.normalize();
        return coalesce("GET " + normalized.toASCIIString(), () -> delegate.getNextPage(normalized));
    }

    private String coalesce(String key, Request request) {
        CompletableFuture<String> mine = new CompletableFuture<>();
        CompletableFuture<String> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) return await(existing);

        try {
            String response = executeWithRetry(request);
            mine.complete(response);
            return response;
        } catch (RuntimeException | Error failure) {
            mine.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private String executeWithRetry(Request request) {
        for (int attempt = 1; ; attempt++) {
            try {
                requestPermit.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new MalApiException(MalApiErrorCategory.INTERRUPTED, null,
                        "The MAL API request was interrupted while waiting for request capacity.", interrupted);
            }

            Duration delay = null;
            try {
                return request.execute();
            } catch (MalApiException failure) {
                if (attempt >= maxAttempts || !isRetryable(failure)) throw failure;
                delay = retryDelay(attempt, failure);
            } finally {
                requestPermit.release();
            }
            // Do not occupy the global request slot while waiting for a retry.
            pause(delay);
        }
    }

    private Duration retryDelay(int failedAttempt, MalApiException failure) {
        if (failure.retryAfter() != null) return failure.retryAfter();

        long multiplier = 1L << Math.min(failedAttempt - 1, 30);
        Duration exponential;
        try {
            exponential = initialBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException overflow) {
            exponential = maxBackoff;
        }
        if (exponential.compareTo(maxBackoff) > 0) exponential = maxBackoff;

        double factor = jitterMultiplier.getAsDouble();
        if (!Double.isFinite(factor) || factor < 0.5 || factor > 1.5) factor = 1.0;
        long nanos;
        try {
            nanos = Math.max(0L, Math.round(exponential.toNanos() * factor));
        } catch (ArithmeticException overflow) {
            return maxBackoff;
        }
        Duration jittered = Duration.ofNanos(nanos);
        return jittered.compareTo(maxBackoff) > 0 ? maxBackoff : jittered;
    }

    private void pause(Duration delay) {
        if (delay.isZero() || delay.isNegative()) return;
        try {
            sleeper.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new MalApiException(MalApiErrorCategory.INTERRUPTED, null,
                    "The MAL API retry wait was interrupted.", interrupted);
        }
    }

    private static boolean isRetryable(MalApiException failure) {
        return switch (failure.category()) {
            case NETWORK_ERROR, TIMEOUT, RATE_LIMITED, SERVICE_ERROR -> true;
            case REJECTED_CREDENTIALS, NOT_FOUND, HTTP_ERROR, INTERRUPTED -> false;
        };
    }

    private static String await(CompletableFuture<String> future) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw failure;
        }
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return value;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    @FunctionalInterface
    private interface Request {
        String execute();
    }
}
