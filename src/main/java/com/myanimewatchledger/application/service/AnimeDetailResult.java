package com.myanimewatchledger.application.service;

import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import java.util.Optional;

/** Cached or refreshed detail data, with a non-fatal warning when refresh was unavailable. */
public record AnimeDetailResult(
        long malId,
        Optional<AnimeMetadataCache> metadata,
        boolean refreshed,
        String warning) {
    public AnimeDetailResult {
        if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");
        metadata = metadata == null ? Optional.empty() : metadata;
        if (metadata.isPresent() && metadata.orElseThrow().malId() != malId) {
            throw new IllegalArgumentException("Detail metadata does not match the requested MAL ID.");
        }
    }
}
