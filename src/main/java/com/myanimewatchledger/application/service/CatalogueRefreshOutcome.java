package com.myanimewatchledger.application.service;

import java.time.Instant;
import java.util.List;

/** Summary of a successfully persisted seasonal catalogue refresh. */
public record CatalogueRefreshOutcome(
        long searchId,
        int animeCount,
        int skippedItems,
        List<String> warnings,
        Instant completedAt) {
    public CatalogueRefreshOutcome {
        if (searchId <= 0 || animeCount < 0 || skippedItems < 0 || completedAt == null) {
            throw new IllegalArgumentException("Invalid catalogue refresh outcome.");
        }
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
