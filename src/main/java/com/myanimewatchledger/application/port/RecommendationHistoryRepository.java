package com.myanimewatchledger.application.port;

import com.myanimewatchledger.domain.recommendation.*;

import java.time.Instant;
import java.util.List;

public interface RecommendationHistoryRepository {
    long recordRecommendation(long malId, String engineId, int engineVersion,
                              String featureSnapshotJson, String recommendationJson, Instant createdAt);

    void recordFeedback(long eventId, RecommendationDimension dimension,
                        RecommendationFeedbackAction action, String selectedValue, Instant respondedAt);

    List<RecommendationHistoryEntry> findAll();
}
