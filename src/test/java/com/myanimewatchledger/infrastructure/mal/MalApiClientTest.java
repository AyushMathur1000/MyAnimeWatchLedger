package com.myanimewatchledger.infrastructure.mal;

import com.myanimewatchledger.config.MalApiConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MalApiClientTest {
    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsGetWithRequiredHeadersAndPreservesResponseBody() {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> clientId = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/v2/anime/season/2026/fall", exchange -> {
            method.set(exchange.getRequestMethod());
            clientId.set(exchange.getRequestHeaders().getFirst("X-MAL-CLIENT-ID"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            path.set(exchange.getRequestURI().toString());
            byte[] body = "{\"data\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        String body = client().get("anime/season/2026/fall?limit=100&offset=0");

        assertEquals("{\"data\":[]}", body);
        assertEquals("GET", method.get());
        assertEquals("test-client-id", clientId.get());
        assertEquals("application/json", accept.get());
        assertTrue(userAgent.get().startsWith("MyAnimeWatchLedger/"));
        assertEquals("/v2/anime/season/2026/fall?limit=100&offset=0", path.get());
    }

    @Test
    void mapsRejectedClientIdResponsesWithoutExposingResponseBody() {
        respond(401, "private diagnostic response containing nothing useful");
        MalApiException exception = assertThrows(MalApiException.class, () -> client().get("anime/1"));
        assertEquals(MalApiErrorCategory.REJECTED_CREDENTIALS, exception.category());
        assertEquals(401, exception.statusCode());
        assertFalse(exception.getMessage().contains("private diagnostic"));
        assertFalse(exception.getMessage().contains("test-client-id"));
    }

    @Test
    void mapsRateLimitResponses() {
        respond(429, "rate limited");
        MalApiException exception = assertThrows(MalApiException.class, () -> client().get("anime/1"));
        assertEquals(MalApiErrorCategory.RATE_LIMITED, exception.category());
        assertEquals(429, exception.statusCode());
    }

    @Test
    void mapsServiceFailures() {
        respond(503, "maintenance");
        MalApiException exception = assertThrows(MalApiException.class, () -> client().get("anime/1"));
        assertEquals(MalApiErrorCategory.SERVICE_ERROR, exception.category());
        assertEquals(503, exception.statusCode());
    }

    @Test
    void rejectsAbsoluteOrHostEscapingEndpointsBeforeSending() {
        MalApiClient client = client();
        assertThrows(IllegalArgumentException.class, () -> client.get("https://example.com/steal"));
        assertThrows(IllegalArgumentException.class, () -> client.get("../outside"));
        assertThrows(IllegalArgumentException.class, () -> client.get("/anime/1"));
    }

    @Test
    void rejectsUnsafeNextPageUrlsBeforeSending() {
        MalApiClient client = client();
        assertThrows(IllegalArgumentException.class,
                () -> client.getNextPage(URI.create("http://127.0.0.1/v2/page")));
        assertThrows(IllegalArgumentException.class,
                () -> client.getNextPage(URI.create("https://example.com/v2/page")));
        assertThrows(IllegalArgumentException.class,
                () -> client.getNextPage(URI.create("https://127.0.0.1/v2/../outside")));
    }

    @Test
    void mapsRequestTimeouts() {
        server.createContext("/v2/slow", exchange -> {
            try {
                Thread.sleep(350);
                byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        MalApiException exception = assertThrows(MalApiException.class, () -> {
            MalApiConfiguration configuration = new MalApiConfiguration(
                    "test-client-id", baseUri, Duration.ofSeconds(1), Duration.ofMillis(75));
            new MalApiClient(configuration).get("slow");
        });
        assertEquals(MalApiErrorCategory.TIMEOUT, exception.category());
    }

    private MalApiClient client() {
        MalApiConfiguration configuration = new MalApiConfiguration(
                "test-client-id", baseUri, Duration.ofSeconds(2), Duration.ofSeconds(2));
        return new MalApiClient(configuration);
    }

    private void respond(int status, String responseBody) {
        server.createContext("/v2/anime/1", exchange -> {
            byte[] body = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }
}
