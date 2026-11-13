package com.myanimewatchledger.domain.catalogue;

import java.util.List;
import java.util.Objects;

/** A complete seasonal result returned by a catalogue gateway. */
public record SeasonalCatalogue(
        List<AnimeMetadataCache> anime,
        int skippedItems,
        List<String> warnings) {
    public SeasonalCatalogue {
        anime = List.copyOf(Objects.requireNonNull(anime, "anime"));
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        if (skippedItems < 0) {
            throw new IllegalArgumentException("Skipped item count cannot be negative.");
        }
    }
}
