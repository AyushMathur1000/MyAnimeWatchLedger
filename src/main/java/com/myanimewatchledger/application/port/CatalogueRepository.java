package com.myanimewatchledger.application.port;
import com.myanimewatchledger.domain.catalogue.*;
import java.time.Instant;
import java.util.*;
public interface CatalogueRepository {
 CatalogueSearch findOrCreate(Season season, int year);
 Optional<CatalogueSearch> findById(long searchId);
 List<CatalogueSearch> findAll();
 void markFetchSucceeded(long searchId, Instant completedAt);
 void markFetchFailed(long searchId, Instant attemptedAt, String error);
 void replaceMembership(long searchId, List<Long> malIdsInDisplayOrder);
 List<CatalogueAnime> findEligibleAnime(long searchId, List<String> andTokens);
 void clearCache();
}
