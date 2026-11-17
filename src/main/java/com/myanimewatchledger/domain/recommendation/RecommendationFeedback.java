package com.myanimewatchledger.domain.recommendation;

import java.time.Instant;
import java.util.Objects;

public record RecommendationFeedback(
        RecommendationDimension dimension,
        RecommendationFeedbackAction action,
        String selectedValue,
        Instant respondedAt) {
    public RecommendationFeedback {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(respondedAt, "respondedAt");
        if ((action == RecommendationFeedbackAction.ACCEPTED
                || action == RecommendationFeedbackAction.MODIFIED)
                && (selectedValue == null || selectedValue.isBlank())) {
            throw new IllegalArgumentException("Accepted or modified feedback requires the selected value.");
        }
        if ((action == RecommendationFeedbackAction.REJECTED
                || action == RecommendationFeedbackAction.DISMISSED) && selectedValue != null) {
            throw new IllegalArgumentException("Rejected or dismissed feedback cannot contain a selected value.");
        }
    }
}
