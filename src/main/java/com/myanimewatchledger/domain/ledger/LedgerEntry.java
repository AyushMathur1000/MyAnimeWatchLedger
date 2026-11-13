package com.myanimewatchledger.domain.ledger;
import java.time.Instant;
import java.util.Objects;
public record LedgerEntry(long malId, String title, LedgerStatus status, Instant dateAdded, Instant lastModified) {
 public LedgerEntry {
  if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");
  if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required.");
  Objects.requireNonNull(status); Objects.requireNonNull(dateAdded); Objects.requireNonNull(lastModified);
 }
}
