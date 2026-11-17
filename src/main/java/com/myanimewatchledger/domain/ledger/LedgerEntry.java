package com.myanimewatchledger.domain.ledger;

import java.time.Instant;
import java.util.Objects;

/** Permanent user-owned classification and preferences for one MAL anime. */
public record LedgerEntry(long malId, String title, LedgerStatus status,
                          Instant dateAdded, Instant lastModified,
                          AnimePreference preference, Integer watchLaterPriority) {
    public LedgerEntry {
        if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required.");
        Objects.requireNonNull(status);
        Objects.requireNonNull(dateAdded);
        Objects.requireNonNull(lastModified);
        if (watchLaterPriority != null && (watchLaterPriority < 1 || watchLaterPriority > 10)) {
            throw new IllegalArgumentException("Watch Later priority must be between 1 and 10.");
        }
        if (status != LedgerStatus.WATCH_LATER && watchLaterPriority != null) {
            throw new IllegalArgumentException("Priority is only valid for Watch Later entries.");
        }
    }

    /** Compatibility constructor for callers that do not set optional preferences. */
    public LedgerEntry(long malId, String title, LedgerStatus status, Instant dateAdded, Instant lastModified) {
        this(malId, title, status, dateAdded, lastModified, null, null);
    }
}