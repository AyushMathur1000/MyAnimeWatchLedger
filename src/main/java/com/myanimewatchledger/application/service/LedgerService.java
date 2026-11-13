package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.LedgerRepository;
import com.myanimewatchledger.domain.catalogue.CatalogueAnime;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Coordinates user-facing ledger operations without exposing persistence details. */
public final class LedgerService {
    private final LedgerRepository ledger;
    private final Clock clock;

    public LedgerService(LedgerRepository ledger) {
        this(ledger, Clock.systemUTC());
    }

    public LedgerService(LedgerRepository ledger, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<LedgerEntry> getAll() {
        return ledger.findAll();
    }

    public List<LedgerEntry> getByStatus(LedgerStatus status) {
        return ledger.findByStatus(Objects.requireNonNull(status, "status"));
    }

    public List<LedgerEntry> search(String query) {
        return ledger.searchByTitle(query == null ? "" : query.trim());
    }

    public Optional<LedgerEntry> find(long malId) {
        requireValidMalId(malId);
        return ledger.findByMalId(malId);
    }

    /** Adds a catalogue anime using Watch Later as the default initial status. */
    public LedgerEntry add(CatalogueAnime anime) {
        return add(anime, LedgerStatus.WATCH_LATER);
    }

    /** Adds a catalogue anime to the permanent ledger using the selected status. */
    public LedgerEntry add(CatalogueAnime anime, LedgerStatus status) {
        Objects.requireNonNull(anime, "anime");
        Objects.requireNonNull(status, "status");
        if (ledger.findByMalId(anime.malId()).isPresent()) {
            throw new IllegalStateException("Anime is already present in the ledger: " + anime.malId());
        }
        Instant now = clock.instant();
        LedgerEntry entry = new LedgerEntry(anime.malId(), anime.preferredTitle(), status, now, now);
        ledger.add(entry);
        return entry;
    }

    public boolean changeStatus(long malId, LedgerStatus status) {
        requireValidMalId(malId);
        return ledger.updateStatus(malId, Objects.requireNonNull(status, "status"));
    }

    public boolean rename(long malId, String preferredTitle) {
        requireValidMalId(malId);
        if (preferredTitle == null || preferredTitle.isBlank()) {
            throw new IllegalArgumentException("Preferred title is required.");
        }
        return ledger.updatePreferredTitle(malId, preferredTitle.trim());
    }

    public boolean remove(long malId) {
        requireValidMalId(malId);
        return ledger.remove(malId);
    }

    private static void requireValidMalId(long malId) {
        if (malId <= 0) {
            throw new IllegalArgumentException("MAL ID must be positive.");
        }
    }
}
