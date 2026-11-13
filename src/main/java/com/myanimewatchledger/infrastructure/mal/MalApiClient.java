package com.myanimewatchledger.infrastructure.mal;

import com.myanimewatchledger.config.MalApiConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Synchronous, GET-only transport for MAL API v2. Callers are responsible for
 * invoking it from a background thread, never the JavaFX Application Thread.
 */
public final class MalApiClient {
    private static final String USER_AGENT = "MyAnimeWatchLedger/0.1.0";
    private final HttpClient httpClient;
    private final MalApiConfiguration configuration;

    public MalApiClient(MalApiConfiguration configuration) {
        this(configuration, buildHttpClient(configuration));
    }

    public MalApiClient(MalApiConfiguration configuration, HttpClient httpClient) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public String get(String relativePathAndQuery) {
        URI requestUri = resolveAndValidate(relativePathAndQuery);
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(configuration.requestTimeout())
                .header("Accept", "application/json")
                .header("X-MAL-CLIENT-ID", configuration.clientId())
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response.body();
            }
            throw httpFailure(status);
        } catch (HttpTimeoutException exception) {
            throw new MalApiException(MalApiErrorCategory.TIMEOUT, null,
                    "The MAL API request timed out.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MalApiException(MalApiErrorCategory.INTERRUPTED, null,
                    "The MAL API request was interrupted.", exception);
        } catch (IOException exception) {
            throw new MalApiException(MalApiErrorCategory.NETWORK_ERROR, null,
                    "The MAL API could not be reached.", exception);
        }
    }

    private URI resolveAndValidate(String relativePathAndQuery) {
        if (relativePathAndQuery == null || relativePathAndQuery.isBlank()
                || relativePathAndQuery.startsWith("/")
                || relativePathAndQuery.startsWith("\\")) {
            throw new IllegalArgumentException("MAL API endpoint must be a non-empty relative path.");
        }

        URI endpoint;
        try {
            endpoint = URI.create(relativePathAndQuery);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("MAL API endpoint is not a valid relative URI.", exception);
        }
        if (endpoint.isAbsolute() || endpoint.getRawAuthority() != null
                || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("MAL API endpoint must not override the configured API host.");
        }

        URI resolved = configuration.baseUri().resolve(endpoint);
        URI base = configuration.baseUri();
        if (!Objects.equals(base.getScheme(), resolved.getScheme())
                || !Objects.equals(base.getHost(), resolved.getHost())
                || effectivePort(base) != effectivePort(resolved)
                || !resolved.getPath().startsWith(base.getPath())) {
            throw new IllegalArgumentException("MAL API endpoint resolves outside the configured API base path.");
        }
        return resolved;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static MalApiException httpFailure(int status) {
        if (status == 401 || status == 403) {
            return new MalApiException(MalApiErrorCategory.REJECTED_CREDENTIALS, status,
                    "MAL rejected the configured Client ID or access to this resource.", null);
        }
        if (status == 429) {
            return new MalApiException(MalApiErrorCategory.RATE_LIMITED, status,
                    "MAL rate-limited the request. Try again later.", null);
        }
        if (status >= 500) {
            return new MalApiException(MalApiErrorCategory.SERVICE_ERROR, status,
                    "MAL is temporarily unable to process the request.", null);
        }
        return new MalApiException(MalApiErrorCategory.HTTP_ERROR, status,
                "MAL returned an unsuccessful HTTP response.", null);
    }

    private static HttpClient buildHttpClient(MalApiConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return HttpClient.newBuilder()
                .connectTimeout(configuration.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
