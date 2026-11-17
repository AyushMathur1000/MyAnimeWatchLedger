package com.myanimewatchledger.domain.recommendation;

import com.myanimewatchledger.domain.ledger.AnimePreference;
import com.myanimewatchledger.domain.ledger.LedgerStatus;

import java.util.List;
import java.util.Objects;

/** Explainable Stage 1 suggestion for an anime that is not in the ledger. */
public record AnimeRecommendation(
        long malId,
        LedgerStatus suggestedStatus,
        AnimePreference suggestedPreference,
        Integer suggestedWatchLaterPriority,
        Integer tasteCompatibility,
        int confidence,
        List<String> reasons,
        String engineId,
        int engineVersion,
        AnimePreferenceProfile preferenceProfileSnapshot) {

    public AnimeRecommendation {
        if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");
        Objects.requireNonNull(suggestedStatus, "suggestedStatus");
        Objects.requireNonNull(preferenceProfileSnapshot, "preferenceProfileSnapshot");
        if (suggestedStatus != LedgerStatus.WATCH_LATER
                && suggestedStatus != LedgerStatus.BLACKLISTED) {
            throw new IllegalArgumentException(
                    "A recommendation for an anime outside the ledger may only suggest Watch Later or Blacklisted.");
        }
        if (suggestedWatchLaterPriority != null
                && (suggestedStatus != LedgerStatus.WATCH_LATER
                || suggestedWatchLaterPriority < 1 || suggestedWatchLaterPriority > 10)) {
            throw new IllegalArgumentException("Watch Later priority must be 1–10 and only applies to Watch Later.");
        }
        if (tasteCompatibility != null && (tasteCompatibility < 0 || tasteCompatibility > 100)) {
            throw new IllegalArgumentException("Taste compatibility must be between 0 and 100.");
        }
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("Confidence must be between 0 and 100.");
        }
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
        if (engineId == null || engineId.isBlank()) throw new IllegalArgumentException("Engine ID is required.");
        if (engineVersion < 1) throw new IllegalArgumentException("Engine version must be positive.");
    }
}
