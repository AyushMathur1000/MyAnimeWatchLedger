package com.myanimewatchledger.application.port;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import java.util.*;
public interface AnimeMetadataRepository {
 void upsert(AnimeMetadataCache metadata);
 void upsertAll(Collection<AnimeMetadataCache> metadata);
 Optional<AnimeMetadataCache> findByMalId(long malId);
 void deleteAll();
}
