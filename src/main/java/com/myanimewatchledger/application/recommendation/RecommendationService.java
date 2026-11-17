package com.myanimewatchledger.application.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.application.port.AnimeMetadataRepository;
import com.myanimewatchledger.application.port.LedgerRepository;
import com.myanimewatchledger.application.port.RecommendationHistoryRepository;
import com.myanimewatchledger.domain.recommendation.RecommendationDimension;
import com.myanimewatchledger.domain.recommendation.RecommendationFeedbackAction;
import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.domain.catalogue.CatalogueAnime;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.recommendation.AnimePreferenceProfile;
import com.myanimewatchledger.domain.recommendation.AnimePreferenceSignal;
import com.myanimewatchledger.domain.recommendation.AnimeRecommendation;
import com.myanimewatchledger.domain.recommendation.RecommendationFeatureSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds recommendations exclusively from the local ledger and metadata cache.
 * This service intentionally has no MAL gateway, HTTP client, or network port.
 */
public final class RecommendationService {
    private final LedgerRepository ledger;
    private final AnimeMetadataRepository metadata;
    private final RecommendationEngine engine;
    private final RecommendationHistoryRepository history;
    private final ObjectMapper json;

    public RecommendationService(LedgerRepository ledger, AnimeMetadataRepository metadata,
                                 RecommendationEngine engine, RecommendationHistoryRepository history) {
        this(ledger, metadata, engine, history, new ObjectMapper());
    }

    RecommendationService(LedgerRepository ledger, AnimeMetadataRepository metadata,
                          RecommendationEngine engine, RecommendationHistoryRepository history, ObjectMapper json) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.history = Objects.requireNonNull(history, "history");
        this.json = Objects.requireNonNull(json, "json");
    }

    /** Returns empty when the candidate is in the ledger or cached metadata is unavailable. */
    public Optional<AnimeRecommendation> recommend(CatalogueAnime candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (ledger.findByMalId(candidate.malId()).isPresent()) return Optional.empty();
        if (candidate.metadataJson() == null || candidate.metadataJson().isBlank()) return Optional.empty();

        AnimeMetadata candidateMetadata = readMetadata(candidate.metadataJson());
        AnimePreferenceProfile profile = buildProfile();
        return Optional.of(engine.recommend(candidateMetadata, profile));
    }

    private AnimePreferenceProfile buildProfile() {
        List<AnimePreferenceSignal> signals = new ArrayList<>();
        for (LedgerEntry entry : ledger.findAll()) {
            if (entry.preference() == null) continue;
            Optional<AnimeMetadataCache> cached = metadata.findByMalId(entry.malId());
            if (cached.isEmpty()) continue;
            AnimeMetadata known = readMetadata(cached.orElseThrow().metadataJson());
            signals.add(new AnimePreferenceSignal(known, entry.preference()));
        }
        return AnimePreferenceProfile.fromSignals(signals);
    }

    /** Persists the exact local feature and recommendation snapshots shown to the user. */
    public long recordRecommendation(CatalogueAnime candidate, AnimeRecommendation recommendation) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(recommendation, "recommendation");
        if (candidate.malId() != recommendation.malId()) {
            throw new IllegalArgumentException("Candidate and recommendation MAL IDs do not match.");
        }
        if (ledger.findByMalId(candidate.malId()).isPresent()) {
            throw new IllegalStateException("The anime entered the ledger before its recommendation was recorded.");
        }
        try {
            AnimeMetadata candidateMetadata = readMetadata(candidate.metadataJson());
            RecommendationFeatureSnapshot featureSnapshot =
                    new RecommendationFeatureSnapshot(candidateMetadata, recommendation.preferenceProfileSnapshot());
            return history.recordRecommendation(candidate.malId(), recommendation.engineId(),
                    recommendation.engineVersion(), json.writeValueAsString(featureSnapshot),
                    json.writeValueAsString(recommendation), java.time.Instant.now());
        } catch (Exception failure) {
            throw new IllegalStateException("Recommendation history could not be saved.", failure);
        }
    }

    public void recordFeedback(long eventId, RecommendationDimension dimension,
                               RecommendationFeedbackAction action, String selectedValue) {
        history.recordFeedback(eventId, dimension, action, selectedValue, java.time.Instant.now());
    }

    private AnimeMetadata readMetadata(String source) {
        try {
            return json.readValue(source, AnimeMetadata.class);
        } catch (Exception failure) {
            throw new IllegalStateException("Cached anime metadata could not be read for recommendations.", failure);
        }
    }
}
