package com.myanimewatchledger.infrastructure.mal;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ResilientMalApiTransportTest {

    @Test
    void retriesTransientServiceFailureWithBoundedBackoff() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<Long>> waits = new AtomicReference<>(List.of());
        java.util.ArrayList<Long> recordedWaits = new java.util.ArrayList<>();
        MalApiTransport delegate = transport(path -> {
            if (calls.incrementAndGet() == 1) {
                throw new MalApiException(MalApiErrorCategory.SERVICE_ERROR, 503, "temporarily unavailable", null);
            }
            return "{\"ok\":true}";
        });
        ResilientMalApiTransport resilient = new ResilientMalApiTransport(
                delegate, 3, Duration.ofMillis(10), Duration.ofMillis(40),
                recordedWaits::add, () -> 1.0);

        assertEquals("{\"ok\":true}", resilient.get("anime/1"));
        assertEquals(2, calls.get());
        assertEquals(List.of(10L), recordedWaits);
    }

    @Test
    void honoursServerRetryAfterInsteadOfUsingLocalBackoff() {
        AtomicInteger calls = new AtomicInteger();
        java.util.ArrayList<Long> waits = new java.util.ArrayList<>();
        MalApiTransport delegate = transport(path -> {
            if (calls.incrementAndGet() == 1) {
                throw new MalApiException(MalApiErrorCategory.RATE_LIMITED, 429,
                        "rate limited", null, Duration.ofSeconds(7));
            }
            return "ok";
        });
        ResilientMalApiTransport resilient = new ResilientMalApiTransport(
                delegate, 2, Duration.ofMillis(10), Duration.ofMillis(40),
                waits::add, () -> 1.0);

        assertEquals("ok", resilient.get("anime/1"));
        assertEquals(2, calls.get());
        assertEquals(List.of(7_000L), waits);
    }

    @Test
    void doesNotRetryNotFoundOrCredentialFailures() {
        AtomicInteger calls = new AtomicInteger();
        MalApiTransport delegate = transport(path -> {
            calls.incrementAndGet();
            throw new MalApiException(MalApiErrorCategory.NOT_FOUND, 404, "not found", null);
        });
        ResilientMalApiTransport resilient = new ResilientMalApiTransport(
                delegate, 3, Duration.ofMillis(1), Duration.ofMillis(5),
                millis -> fail("A non-retryable response must not sleep."), () -> 1.0);

        MalApiException failure = assertThrows(MalApiException.class, () -> resilient.get("anime/404"));
        assertEquals(MalApiErrorCategory.NOT_FOUND, failure.category());
        assertEquals(1, calls.get());
    }

    @Test
    void coalescesIdenticalConcurrentRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch allowResponse = new CountDownLatch(1);
        MalApiTransport delegate = transport(path -> {
            calls.incrementAndGet();
            requestStarted.countDown();
            try {
                if (!allowResponse.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Test did not release the request.");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return "shared-response";
        });
        ResilientMalApiTransport resilient = new ResilientMalApiTransport(delegate);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = workers.submit(() -> resilient.get("anime/1"));
            assertTrue(requestStarted.await(1, TimeUnit.SECONDS));
            Future<String> second = workers.submit(() -> resilient.get("anime/1"));
            Thread.sleep(40);
            allowResponse.countDown();

            assertEquals("shared-response", first.get(1, TimeUnit.SECONDS));
            assertEquals("shared-response", second.get(1, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            allowResponse.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void allowsOnlyOneDifferentMalRequestAtATime() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        MalApiTransport delegate = transport(path -> {
            int nowActive = active.incrementAndGet();
            maximumActive.accumulateAndGet(nowActive, Math::max);
            try {
                Thread.sleep(40);
                return path;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            } finally {
                active.decrementAndGet();
            }
        });
        ResilientMalApiTransport resilient = new ResilientMalApiTransport(delegate);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = workers.submit(() -> resilient.get("anime/1"));
            Future<String> second = workers.submit(() -> resilient.get("anime/2"));
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertEquals(1, maximumActive.get());
        } finally {
            workers.shutdownNow();
        }
    }

    private static MalApiTransport transport(java.util.function.Function<String, String> handler) {
        return new MalApiTransport() {
            @Override
            public String get(String relativePathAndQuery) {
                return handler.apply(relativePathAndQuery);
            }

            @Override
            public String getNextPage(URI nextPageUri) {
                return handler.apply(nextPageUri.toString());
            }
        };
    }
}
