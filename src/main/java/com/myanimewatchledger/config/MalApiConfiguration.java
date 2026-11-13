package com.myanimewatchledger.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Runtime settings for public, read-only MyAnimeList API requests. */
public record MalApiConfiguration(
        String clientId,
        URI baseUri,
        Duration connectTimeout,
        Duration requestTimeout) {

    public static final URI DEFAULT_BASE_URI = URI.create("https://api.myanimelist.net/v2/");
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public MalApiConfiguration {
        clientId = validateClientId(clientId);
        Objects.requireNonNull(baseUri, "baseUri");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (!("https".equalsIgnoreCase(baseUri.getScheme())
                || "http".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null) {
            throw new IllegalArgumentException("MAL API base URI must be an absolute HTTP(S) URI.");
        }
        if (!baseUri.getPath().endsWith("/")) {
            throw new IllegalArgumentException("MAL API base URI must end with '/'.");
        }
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("HTTP timeouts must be positive.");
        }
    }

    public static MalApiConfiguration defaults(String clientId) {
        return new MalApiConfiguration(
                clientId, DEFAULT_BASE_URI, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    private static String validateClientId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A MAL Client ID must be configured before making API requests.");
        }
        String normalized = value.trim();
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("The MAL Client ID contains invalid control characters.");
        }
        return normalized;
    }
}
