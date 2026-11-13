package com.myanimewatchledger.domain.catalogue;
import java.time.Instant;
public record CatalogueSearch(long searchId, Season season, int year, Instant lastAttemptAt,
 Instant lastSuccessfulFetchAt, FetchStatus fetchStatus, String lastError) {
 public CatalogueSearch {
  if (searchId <= 0) throw new IllegalArgumentException("Search ID must be positive.");
  if (season == null || fetchStatus == null) throw new IllegalArgumentException("Season and fetch status are required.");
  if (year < 1900 || year > 9999) throw new IllegalArgumentException("Year is out of range.");
 }
}
