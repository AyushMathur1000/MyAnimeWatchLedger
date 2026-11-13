package com.myanimewatchledger.application.port;

import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;

/** Applies the conservative metadata-cache merge policy to an incoming MAL record. */
@FunctionalInterface
public interface AnimeMetadataCacheMerger {
    AnimeMetadataCache merge(AnimeMetadataCache existing, AnimeMetadataCache incoming);
}
