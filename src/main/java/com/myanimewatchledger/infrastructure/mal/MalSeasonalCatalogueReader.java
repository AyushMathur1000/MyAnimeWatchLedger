package com.myanimewatchledger.infrastructure.mal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads every page of a seasonal response and rejects incomplete or looping pagination. */
public final class MalSeasonalCatalogueReader {
    private final MalApiTransport transport;
    private final URI apiBaseUri;
    private final ObjectMapper objectMapper;
    private final MalResponseMapper mapper;

    public MalSeasonalCatalogueReader(MalApiTransport transport, URI apiBaseUri, MalResponseMapper mapper) {
        this(transport, apiBaseUri, mapper, new ObjectMapper());
    }

    public MalSeasonalCatalogueReader(
            MalApiTransport transport, URI apiBaseUri, MalResponseMapper mapper, ObjectMapper objectMapper) {
        this.transport = transport;
        this.apiBaseUri = apiBaseUri;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public SeasonalCatalogueResult read(String initialRelativePath) {
        URI currentUri = apiBaseUri.resolve(initialRelativePath).normalize();
        Set<URI> visited = new HashSet<>();
        Map<Long, AnimeMetadata> uniqueAnime = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;

        while (true) {
            if (!visited.add(currentUri)) {
                throw new MalResponseMappingException("MAL pagination repeated a page URL; catalogue is incomplete.");
            }
            String body = visited.size() == 1
                    ? transport.get(initialRelativePath)
                    : transport.getNextPage(currentUri);
            MalSeasonalCataloguePage page = parsePage(body);
            for (JsonNode node : page.animeNodes()) {
                try {
                    AnimeMetadata mapped = mapper.mapAnime(node);
                    AnimeMetadata previous = uniqueAnime.putIfAbsent(mapped.malId(), mapped);
                    if (previous != null) {
                        uniqueAnime.put(mapped.malId(), mapper.merge(previous, mapped));
                        warnings.add("Duplicate MAL ID merged: " + mapped.malId());
                    }
                } catch (MalResponseMappingException exception) {
                    skipped++;
                    warnings.add(exception.getMessage());
                }
            }
            if (page.nextPageUri() == null) break;
            validateNextPage(page.nextPageUri());
            currentUri = page.nextPageUri().normalize();
        }

        List<AnimeMetadataCache> cache = uniqueAnime.values().stream().map(mapper::toCache).toList();
        return new SeasonalCatalogueResult(cache, skipped, warnings);
    }

    private MalSeasonalCataloguePage parsePage(String body) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new MalResponseMappingException("MAL returned invalid JSON; seasonal retrieval is incomplete.", exception);
        }
        if (root == null || !root.isObject() || !root.path("data").isArray()) {
            throw new MalResponseMappingException("MAL seasonal response is missing its data array.");
        }

        List<JsonNode> nodes = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            JsonNode node = item != null && item.isObject() ? item.get("node") : null;
            if (node == null || !node.isObject()) {
                throw new MalResponseMappingException("MAL seasonal response contains a malformed list item.");
            }
            nodes.add(node);
        }

        JsonNode paging = root.get("paging");
        if (paging != null && !paging.isNull() && !paging.isObject()) {
            throw new MalResponseMappingException("MAL seasonal response contains malformed paging metadata.");
        }
        JsonNode next = paging == null || paging.isNull() ? null : paging.get("next");
        URI nextUri = null;
        if (next != null && !next.isNull()) {
            if (!next.isTextual()) {
                throw new MalResponseMappingException("MAL returned a non-text pagination URL.");
            }
            if (!next.asText().isBlank()) {
                try {
                    nextUri = URI.create(next.asText());
                } catch (IllegalArgumentException exception) {
                    throw new MalResponseMappingException("MAL returned an invalid pagination URL.", exception);
                }
            }
        }
        return new MalSeasonalCataloguePage(nodes, nextUri);
    }

    private void validateNextPage(URI next) {
        URI normalized = next.normalize();
        if (!normalized.isAbsolute()
                || !"https".equalsIgnoreCase(normalized.getScheme())
                || normalized.getHost() == null
                || !normalized.getHost().equalsIgnoreCase(apiBaseUri.getHost())
                || effectivePort(normalized) != 443
                || normalized.getUserInfo() != null
                || normalized.getFragment() != null
                || !isWithinApiPath(normalized.getPath(), apiBaseUri.getPath())) {
            throw new MalResponseMappingException("MAL pagination URL points outside the expected HTTPS API host/path.");
        }
    }

    private static boolean isWithinApiPath(String path, String basePath) {
        if (path == null || basePath == null || !path.startsWith(basePath)) return false;
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) return false;
        }
        return true;
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : 443;
    }

    public record SeasonalCatalogueResult(
            List<AnimeMetadataCache> anime, int skippedItems, List<String> warnings) {
        public SeasonalCatalogueResult {
            anime = List.copyOf(anime);
            warnings = List.copyOf(warnings);
        }
    }
}
