package com.myanimewatchledger.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.application.port.AnimeMetadataCacheMerger;
import com.myanimewatchledger.application.port.AnimeMetadataRepository;
import com.myanimewatchledger.application.port.MalAnimeDetailGateway;
import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;

import java.util.Objects;
import java.util.Optional;

/**
 * Loads cached anime details and fills incomplete cache records from MAL.
 * Call load() only from a background thread; repository and gateway operations are blocking.
 */
public final class AnimeDetailService {
    private final AnimeMetadataRepository metadata;
    private final MalAnimeDetailGateway gateway;
    private final AnimeMetadataCacheMerger merger;
    private final ObjectMapper json;

    public AnimeDetailService(
            AnimeMetadataRepository metadata,
            MalAnimeDetailGateway gateway,
            AnimeMetadataCacheMerger merger) {
        this(metadata, gateway, merger, new ObjectMapper());
    }

    AnimeDetailService(
            AnimeMetadataRepository metadata,
            MalAnimeDetailGateway gateway,
            AnimeMetadataCacheMerger merger,
            ObjectMapper json) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.gateway = gateway;
        this.merger = Objects.requireNonNull(merger, "merger");
        this.json = Objects.requireNonNull(json, "json");
    }

    public AnimeDetailResult load(long malId) {
        if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");

        Optional<AnimeMetadataCache> cached = metadata.findByMalId(malId);
        if (cached.isPresent() && isComplete(cached.orElseThrow())) {
            return new AnimeDetailResult(malId, cached, false, null);
        }
        if (gateway == null) {
            return new AnimeDetailResult(malId, cached, false,
                    "MAL is not configured; showing cached information where available.");
        }

        try {
            AnimeMetadataCache incoming = Objects.requireNonNull(gateway.fetchAnime(malId),
                    "MAL detail gateway returned no metadata.");
            if (incoming.malId() != malId) {
                throw new IllegalStateException("MAL returned metadata for a different anime.");
            }
            AnimeMetadataCache effective = cached
                    .map(existing -> merger.merge(existing, incoming))
                    .orElse(incoming);
            metadata.upsert(effective);
            return new AnimeDetailResult(malId, Optional.of(effective), true, null);
        } catch (RuntimeException failure) {
            String warning = failure.getMessage();
            if (warning == null || warning.isBlank()) warning = "MAL detail retrieval failed.";
            return new AnimeDetailResult(malId, cached, false, warning);
        }
    }

    private boolean isComplete(AnimeMetadataCache cached) {
        try {
            AnimeMetadata value = json.readValue(cached.metadataJson(), AnimeMetadata.class);
            // A non-empty synopsis is the indicator that the detailed record has
            // already been populated. Other optional fields may legitimately be null.
            return value.synopsis() != null && !value.synopsis().isBlank();
        } catch (Exception invalidCache) {
            return false;
        }
    }
}
