package com.myanimewatchledger.application.recommendation;

import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.ledger.AnimePreference;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.domain.recommendation.AnimePreferenceProfile;
import com.myanimewatchledger.domain.recommendation.AnimePreferenceSignal;
import com.myanimewatchledger.domain.recommendation.AnimeRecommendation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedRecommendationEngineTest {
    private final RuleBasedRecommendationEngine engine = new RuleBasedRecommendationEngine();

    @Test
    void insufficientHistoryProducesProvisionalWatchLaterWithoutInventingPreferenceOrPriority() {
        AnimeRecommendation recommendation = engine.recommend(anime(1, "Fantasy", "Studio A"),
                AnimePreferenceProfile.empty());

        assertEquals(LedgerStatus.WATCH_LATER, recommendation.suggestedStatus());
        assertNull(recommendation.suggestedPreference());
        assertNull(recommendation.suggestedWatchLaterPriority());
        assertNull(recommendation.tasteCompatibility());
        assertEquals(0, recommendation.confidence());
    }

    @Test
    void oneExplicitPreferenceSignalIsNotEnoughToForcePreferenceOrPriority() {
        AnimePreferenceProfile profile = AnimePreferenceProfile.fromSignals(List.of(
                new AnimePreferenceSignal(anime(10, "Fantasy", "Studio A"), AnimePreference.LIKE)
        ));

        AnimeRecommendation recommendation = engine.recommend(anime(2, "Fantasy", "Studio A"), profile);

        assertEquals(LedgerStatus.WATCH_LATER, recommendation.suggestedStatus());
        assertNull(recommendation.suggestedPreference());
        assertNull(recommendation.suggestedWatchLaterPriority());
        assertTrue(recommendation.confidence() < 40);
    }

    @Test
    void strongNegativeExplicitFeedbackCanRecommendBlacklisted() {
        AnimePreferenceProfile profile = AnimePreferenceProfile.fromSignals(List.of(
                new AnimePreferenceSignal(anime(10, "Fantasy", "Studio A"), AnimePreference.DISLIKE),
                new AnimePreferenceSignal(anime(11, "Fantasy", "Studio A"), AnimePreference.DISLIKE),
                new AnimePreferenceSignal(anime(12, "Fantasy", "Studio A"), AnimePreference.DISLIKE)
        ));

        AnimeRecommendation recommendation = engine.recommend(anime(2, "Fantasy", "Studio A"), profile);

        assertEquals(LedgerStatus.BLACKLISTED, recommendation.suggestedStatus());
        assertEquals(AnimePreference.DISLIKE, recommendation.suggestedPreference());
        assertNull(recommendation.suggestedWatchLaterPriority());
        assertTrue(recommendation.reasons().stream().anyMatch(reason -> reason.contains("mismatch")));
    }

    @Test
    void strongPositiveExplicitFeedbackSuggestsLikeAndHighWatchLaterPriority() {
        AnimePreferenceProfile profile = AnimePreferenceProfile.fromSignals(List.of(
                new AnimePreferenceSignal(anime(10, "Fantasy", "Studio A"), AnimePreference.LIKE),
                new AnimePreferenceSignal(anime(11, "Fantasy", "Studio A"), AnimePreference.LIKE),
                new AnimePreferenceSignal(anime(12, "Fantasy", "Studio A"), AnimePreference.LIKE)
        ));

        AnimeRecommendation recommendation = engine.recommend(anime(2, "Fantasy", "Studio A"), profile);

        assertEquals(LedgerStatus.WATCH_LATER, recommendation.suggestedStatus());
        assertEquals(AnimePreference.LIKE, recommendation.suggestedPreference());
        assertNotNull(recommendation.suggestedWatchLaterPriority());
        assertTrue(recommendation.suggestedWatchLaterPriority() <= 2);
        assertTrue(recommendation.confidence() >= 40);
    }

    @Test
    void neverSuggestsHoldWatchedWatchingOrAbandonedForAnimeOutsideLedger() {
        AnimeRecommendation recommendation = engine.recommend(anime(1, "Fantasy", "Studio A"),
                AnimePreferenceProfile.empty());

        assertNotEquals(LedgerStatus.HOLD, recommendation.suggestedStatus());
        assertNotEquals(LedgerStatus.WATCHED, recommendation.suggestedStatus());
        assertNotEquals(LedgerStatus.WATCHING, recommendation.suggestedStatus());
        assertNotEquals(LedgerStatus.ABANDONED, recommendation.suggestedStatus());
    }

    private static AnimeMetadata anime(long id, String genre, String studio) {
        return new AnimeMetadata(id, "Canonical " + id, null, "Romanized " + id, null,
                List.of(), "Anime " + id, null, null, null, null, null,
                8.0, null, null, null, null, "TV", "finished_airing", 12,
                2025, "winter", null, null, null,
                List.of(new AnimeMetadata.NamedValue(null, genre)),
                List.of(new AnimeMetadata.NamedValue(null, studio)),
                null, null, null, null);
    }
}
