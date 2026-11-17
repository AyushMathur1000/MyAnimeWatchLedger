package com.myanimewatchledger.application.recommendation;

import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.ledger.AnimePreference;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.domain.recommendation.AnimePreferenceProfile;
import com.myanimewatchledger.domain.recommendation.AnimeRecommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, explainable Stage 1 engine. It has no MAL/API dependency and
 * receives only metadata already available to the local application.
 */
public final class RuleBasedRecommendationEngine implements RecommendationEngine {
    public static final String ENGINE_ID = "local-rules";
    public static final int ENGINE_VERSION = 1;
    private static final double BLACKLIST_THRESHOLD = -0.55;
    private static final double PREFERENCE_THRESHOLD = 0.35;

    @Override
    public AnimeRecommendation recommend(AnimeMetadata candidate, AnimePreferenceProfile profile) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(profile, "profile");

        Score score = score(candidate, profile);
        int confidence = confidence(profile, score.featureCount());
        boolean blacklist = score.value() != null
                && score.value() <= BLACKLIST_THRESHOLD
                && confidence >= 40;

        LedgerStatus status = blacklist ? LedgerStatus.BLACKLISTED : LedgerStatus.WATCH_LATER;
        AnimePreference preference = null;
        Integer priority = null;
        Integer compatibility = null;
        List<String> reasons = new ArrayList<>(score.reasons());

        if (score.value() != null) {
            if (confidence >= 40) compatibility = (int) Math.round((score.value() + 1.0) * 50.0);
            if (confidence >= 40) {
                if (score.value() >= PREFERENCE_THRESHOLD) preference = AnimePreference.LIKE;
                else if (score.value() <= -PREFERENCE_THRESHOLD) preference = AnimePreference.DISLIKE;
            }
            if (!blacklist && confidence >= 40) {
                // Existing project convention: 1 is highest Watch Later priority; 10 is lowest.
                priority = Math.max(1, Math.min(10,
                        (int) Math.round(10.0 - ((score.value() + 1.0) / 2.0) * 9.0)));
            }
        } else {
            reasons.add("There is not enough explicit preference history to estimate taste compatibility yet.");
            reasons.add("Watch Later is only a provisional suggestion; no ledger entry is created automatically.");
        }

        if (blacklist) {
            reasons.add("Your explicit feedback indicates a strong mismatch with this anime's available characteristics.");
        } else if (score.value() != null && preference == null) {
            reasons.add("The available preference signals are mixed or too weak for a Like/Dislike suggestion.");
        }

        return new AnimeRecommendation(candidate.malId(), status, preference, priority,
                compatibility, confidence, reasons, ENGINE_ID, ENGINE_VERSION, profile);
    }

    private static Score score(AnimeMetadata candidate, AnimePreferenceProfile profile) {
        List<Double> values = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        addMatches(values, reasons, "Genre", candidate.genres().stream()
                .map(AnimeMetadata.NamedValue::name).toList(), profile.genreAffinity());
        addMatches(values, reasons, "Studio", candidate.studios().stream()
                .map(AnimeMetadata.NamedValue::name).toList(), profile.studioAffinity());
        if (candidate.mediaType() != null) {
            addMatches(values, reasons, "Type", List.of(candidate.mediaType()), profile.typeAffinity());
        }
        if (values.isEmpty()) return new Score(null, 0, reasons);
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new Score(Math.max(-1.0, Math.min(1.0, average)), values.size(), reasons);
    }

    private static void addMatches(List<Double> values, List<String> reasons, String label,
                                   List<String> candidateValues, Map<String, Double> affinity) {
        for (String value : candidateValues) {
            if (value == null) continue;
            Double match = affinity.get(value.trim().toLowerCase(Locale.ROOT));
            if (match == null) continue;
            values.add(match);
            if (match >= PREFERENCE_THRESHOLD) {
                reasons.add(label + " match: " + value + " aligns with your positive feedback.");
            } else if (match <= -PREFERENCE_THRESHOLD) {
                reasons.add(label + " mismatch: " + value + " appears in anime you explicitly disliked.");
            }
        }
    }

    private static int confidence(AnimePreferenceProfile profile, int matchedFeatures) {
        if (profile.explicitFeedbackCount() == 0 || matchedFeatures == 0) return 0;
        return Math.min(95, 10 + profile.explicitFeedbackCount() * 12 + matchedFeatures * 3);
    }

    private record Score(Double value, int featureCount, List<String> reasons) { }
}
