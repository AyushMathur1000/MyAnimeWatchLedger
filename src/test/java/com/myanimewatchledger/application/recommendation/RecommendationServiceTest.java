package com.myanimewatchledger.application.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.AnimePreference;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.application.service.CatalogueService;
import com.myanimewatchledger.infrastructure.persistence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationServiceTest {
    @TempDir Path temporaryDirectory;
    private DatabaseManager database;
    private PersistenceContext persistence;
    private RecommendationService recommendations;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(temporaryDirectory.resolve("recommendations.db"));
        database.initialize();
        new DatabaseMigrationRunner(database).migrate();
        persistence = new PersistenceContext(database);
        recommendations = new RecommendationService(persistence.ledger(), persistence.animeMetadata(),
                new RuleBasedRecommendationEngine(), persistence.recommendationHistory());
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    @Test
    void usesOnlyCachedMetadataAndExplicitLedgerPreferences() throws Exception {
        AnimeMetadata liked = anime(10, "Fantasy", "Studio A");
        AnimeMetadata likedAgain = anime(11, "Fantasy", "Studio A");
        AnimeMetadata candidate = anime(20, "Fantasy", "Studio A");
        persistence.animeMetadata().upsert(cache(liked));
        persistence.animeMetadata().upsert(cache(likedAgain));
        persistence.animeMetadata().upsert(cache(candidate));
        persistence.ledger().add(new com.myanimewatchledger.domain.ledger.LedgerEntry(
                10, liked.preferredTitle(), LedgerStatus.WATCHED, Instant.now(), Instant.now(),
                AnimePreference.LIKE, null));
        persistence.ledger().add(new com.myanimewatchledger.domain.ledger.LedgerEntry(
                11, likedAgain.preferredTitle(), LedgerStatus.WATCH_LATER, Instant.now(), Instant.now(),
                AnimePreference.LIKE, null));

        var recommendation = recommendations.recommend(catalogue(candidate)).orElseThrow();

        assertEquals(LedgerStatus.WATCH_LATER, recommendation.suggestedStatus());
        assertEquals(AnimePreference.LIKE, recommendation.suggestedPreference());
    }

    @Test
    void skipsLedgerAnimeAndCandidatesWithoutCachedMetadata() throws Exception {
        AnimeMetadata existing = anime(30, "Fantasy", "Studio A");
        persistence.ledger().add(new com.myanimewatchledger.domain.ledger.LedgerEntry(
                30, existing.preferredTitle(), LedgerStatus.WATCH_LATER, Instant.now(), Instant.now()));
        assertTrue(recommendations.recommend(catalogue(existing)).isEmpty());

        CatalogueAnime missing = new CatalogueAnime(31, "Missing", null, "", Instant.now(), 0, null);
        assertTrue(recommendations.recommend(missing).isEmpty());
    }

    @Test
    void appliesStatusPreferenceAndPriorityAsOneLedgerEntry() throws Exception {
        AnimeMetadata candidate = anime(50, "Fantasy", "Studio A");
        var added = new com.myanimewatchledger.application.service.LedgerService(persistence.ledger())
                .add(catalogue(candidate), LedgerStatus.WATCH_LATER, AnimePreference.LIKE, 2);

        assertEquals(LedgerStatus.WATCH_LATER, added.status());
        assertEquals(AnimePreference.LIKE, added.preference());
        assertEquals(2, added.watchLaterPriority());
        var persisted = persistence.ledger().findByMalId(50).orElseThrow();
        assertEquals(added.malId(), persisted.malId());
        assertEquals(added.title(), persisted.title());
        assertEquals(added.status(), persisted.status());
        assertEquals(added.preference(), persisted.preference());
        assertEquals(added.watchLaterPriority(), persisted.watchLaterPriority());
        // SQLite stores ledger timestamps as epoch milliseconds.
        assertEquals(added.dateAdded().toEpochMilli(), persisted.dateAdded().toEpochMilli());
        assertEquals(added.lastModified().toEpochMilli(), persisted.lastModified().toEpochMilli());
    }

    @Test
    void storesRejectedAndDismissedAsDifferentFeedbackSignals() throws Exception {
        AnimeMetadata candidate = anime(40, "Fantasy", "Studio A");
        persistence.animeMetadata().upsert(cache(candidate));
        CatalogueAnime catalogue = catalogue(candidate);
        var recommendation = recommendations.recommend(catalogue).orElseThrow();

        long rejectedEvent = recommendations.recordRecommendation(catalogue, recommendation);
        recommendations.recordFeedback(rejectedEvent,
                com.myanimewatchledger.domain.recommendation.RecommendationDimension.STATUS,
                com.myanimewatchledger.domain.recommendation.RecommendationFeedbackAction.REJECTED, null);

        long dismissedEvent = recommendations.recordRecommendation(catalogue, recommendation);
        recommendations.recordFeedback(dismissedEvent,
                com.myanimewatchledger.domain.recommendation.RecommendationDimension.STATUS,
                com.myanimewatchledger.domain.recommendation.RecommendationFeedbackAction.DISMISSED, null);

        var history = persistence.recommendationHistory().findAll();
        assertEquals(2, history.size());
        assertEquals(com.myanimewatchledger.domain.recommendation.RecommendationFeedbackAction.REJECTED,
                history.get(0).feedback().getFirst().action());
        assertEquals(com.myanimewatchledger.domain.recommendation.RecommendationFeedbackAction.DISMISSED,
                history.get(1).feedback().getFirst().action());
        assertEquals(candidate.malId(), history.getFirst().malId());
        assertTrue(history.getFirst().featureSnapshotJson().contains("Fantasy"));

        new CatalogueService(persistence.catalogue()).clearCache();
        assertEquals(2, persistence.recommendationHistory().findAll().size(),
                "Recommendation history must survive disposable catalogue cache clearing.");
    }

    private AnimeMetadataCache cache(AnimeMetadata metadata) throws Exception {
        return new AnimeMetadataCache(metadata.malId(), metadata.preferredTitle(),
                json.writeValueAsString(metadata), metadata.preferredTitle(), Instant.now());
    }

    private CatalogueAnime catalogue(AnimeMetadata metadata) throws Exception {
        return new CatalogueAnime(metadata.malId(), metadata.preferredTitle(),
                json.writeValueAsString(metadata), metadata.preferredTitle(), Instant.now(), 0, null);
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
