package com.myanimewatchledger.application.port;

import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;

/** Retrieves the complete metadata record for one MAL anime ID. */
@FunctionalInterface
public interface MalAnimeDetailGateway {
    AnimeMetadataCache fetchAnime(long malId);
}
