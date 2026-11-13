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

/** Synchronous, GET-only MAL transport. Invoke from a background thread, never the JavaFX thread. */
public final class MalApiClient implements MalApiTransport {
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

    public URI baseUri() {
        return configuration.baseUri();
    }

    @Override
    public String get(String relativePathAndQuery) {
        return send(resolveAndValidate(relativePathAndQuery));
    }

    @Override
    public String getNextPage(URI nextPageUri) {
        Objects.requireNonNull(nextPageUri, "nextPageUri");
        URI base = configuration.baseUri();
        URI normalized = nextPageUri.normalize();
        if (!normalized.isAbsolute()
                || !"https".equalsIgnoreCase(normalized.getScheme())
                || normalized.getHost() == null
                || !normalized.getHost().equalsIgnoreCase(base.getHost())
                || effectivePort(normalized) != 443
                || normalized.getUserInfo() != null
                || normalized.getFragment() != null
                || !isWithinApiPath(normalized.getPath(), base.getPath())) {
            throw new IllegalArgumentException("MAL pagination URL must use the expected HTTPS API host and path.");
        }
        return send(normalized);
    }

    private String send(URI requestUri) {
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
            if (status >= 200 && status < 300) return response.body();
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
                || relativePathAndQuery.startsWith("/") || relativePathAndQuery.startsWith("\\")) {
            throw new IllegalArgumentException("MAL API endpoint must be a non-empty relative path.");
        }
        URI endpoint;
        try {
            endpoint = URI.create(relativePathAndQuery);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("MAL API endpoint is not a valid relative URI.", exception);
        }
        if (endpoint.isAbsolute() || endpoint.getRawAuthority() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("MAL API endpoint must not override the configured API host.");
        }
        URI resolved = configuration.baseUri().resolve(endpoint).normalize();
        URI base = configuration.baseUri();
        if (!Objects.equals(base.getScheme(), resolved.getScheme())
                || !Objects.equals(base.getHost(), resolved.getHost())
                || effectivePort(base) != effectivePort(resolved)
                || !isWithinApiPath(resolved.getPath(), base.getPath())) {
            throw new IllegalArgumentException("MAL API endpoint resolves outside the configured API base path.");
        }
        return resolved;
    }

    private static boolean isWithinApiPath(String path, String basePath) {
        if (path == null || basePath == null || !path.startsWith(basePath)) return false;
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) return false;
        }
        return true;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
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
