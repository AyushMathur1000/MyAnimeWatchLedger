package com.myanimewatchledger.domain.recommendation;

import com.myanimewatchledger.domain.catalogue.AnimeMetadata;

import java.util.Objects;

/** Exact local feature inputs used for one recommendation event. */
public record RecommendationFeatureSnapshot(
        AnimeMetadata candidateMetadata,
        AnimePreferenceProfile preferenceProfile) {
    public RecommendationFeatureSnapshot {
        Objects.requireNonNull(candidateMetadata, "candidateMetadata");
        Objects.requireNonNull(preferenceProfile, "preferenceProfile");
    }
}
