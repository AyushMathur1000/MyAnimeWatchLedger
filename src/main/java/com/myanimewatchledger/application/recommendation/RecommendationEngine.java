package com.myanimewatchledger.application.recommendation;

import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.recommendation.AnimePreferenceProfile;
import com.myanimewatchledger.domain.recommendation.AnimeRecommendation;

/** Algorithm boundary: callers do not depend on how a recommendation is produced. */
public interface RecommendationEngine {
    AnimeRecommendation recommend(AnimeMetadata candidate, AnimePreferenceProfile profile);
}
