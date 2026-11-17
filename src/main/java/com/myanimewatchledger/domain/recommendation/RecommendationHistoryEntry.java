package com.myanimewatchledger.domain.recommendation;

import java.time.Instant;
import java.util.List;

public record RecommendationHistoryEntry(
        long eventId,
        long malId,
        String engineId,
        int engineVersion,
        String featureSnapshotJson,
        String recommendationJson,
        Instant createdAt,
        List<RecommendationFeedback> feedback) {
    public RecommendationHistoryEntry {
        if (eventId <= 0 || malId <= 0) throw new IllegalArgumentException("Event and MAL IDs must be positive.");
        if (engineId == null || engineId.isBlank()) throw new IllegalArgumentException("Engine ID is required.");
        if (engineVersion < 1) throw new IllegalArgumentException("Engine version must be positive.");
        if (featureSnapshotJson == null || featureSnapshotJson.isBlank()
                || recommendationJson == null || recommendationJson.isBlank()) {
            throw new IllegalArgumentException("Recommendation snapshots are required.");
        }
        if (createdAt == null) throw new IllegalArgumentException("Creation time is required.");
        feedback = List.copyOf(feedback == null ? List.of() : feedback);
    }
}
