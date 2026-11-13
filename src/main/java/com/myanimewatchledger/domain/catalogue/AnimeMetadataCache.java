package com.myanimewatchledger.domain.catalogue;
import java.time.Instant;
public record AnimeMetadataCache(long malId, String preferredTitle, String metadataJson, String searchableText, Instant cachedAt) {
 public AnimeMetadataCache {
  if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");
  if (preferredTitle == null || preferredTitle.isBlank()) throw new IllegalArgumentException("Preferred title is required.");
  if (metadataJson == null || metadataJson.isBlank()) throw new IllegalArgumentException("Metadata JSON is required.");
  if (searchableText == null || cachedAt == null) throw new IllegalArgumentException("Search text and cache timestamp are required.");
 }
}
