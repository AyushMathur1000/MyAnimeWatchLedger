package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.LedgerRepository;
import com.myanimewatchledger.domain.catalogue.CatalogueAnime;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.AnimePreference;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LedgerServiceEventTest {

    @Test
    void publishesAddedEntryAfterSuccessfulInsert() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        LedgerService service = new LedgerService(repository);
        List<LedgerChangeEvent> events = new ArrayList<>();
        service.subscribe(events::add);
        CatalogueAnime anime = new CatalogueAnime(99, "New Anime", "{}", "new anime",
                Instant.parse("2026-01-01T00:00:00Z"), 0, null);

        LedgerEntry added = service.add(anime, LedgerStatus.WATCH_LATER);

        assertEquals(added, repository.findByMalId(99).orElseThrow());
        assertEquals(1, events.size());
        assertEquals(LedgerChangeEvent.Type.ADDED, events.getFirst().type());
        assertEquals(99, events.getFirst().malId());
        assertEquals(added, events.getFirst().entry());
        assertNull(events.getFirst().previousStatus());
    }

    @Test
    void publishesStatusChangeOnlyAfterSuccessfulUpdate() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        repository.add(entry(101, LedgerStatus.WATCH_LATER));
        LedgerService service = new LedgerService(repository);
        List<LedgerChangeEvent> events = new ArrayList<>();
        service.subscribe(events::add);

        assertTrue(service.changeStatus(101, LedgerStatus.WATCHING));

        assertEquals(1, events.size());
        LedgerChangeEvent event = events.getFirst();
        assertEquals(LedgerChangeEvent.Type.STATUS_CHANGED, event.type());
        assertEquals(101, event.malId());
        assertEquals(LedgerStatus.WATCH_LATER, event.previousStatus());
        assertEquals(LedgerStatus.WATCHING, event.entry().status());
    }

    @Test
    void publishesRemovalAfterSuccessfulDeleteAndCanUnsubscribe() throws Exception {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        repository.add(entry(202, LedgerStatus.BLACKLISTED));
        LedgerService service = new LedgerService(repository);
        List<LedgerChangeEvent> events = new ArrayList<>();
        AutoCloseable subscription = service.subscribe(events::add);

        assertTrue(service.remove(202));
        subscription.close();
        assertFalse(service.remove(202));

        assertEquals(1, events.size());
        assertEquals(LedgerChangeEvent.Type.REMOVED, events.getFirst().type());
        assertEquals(202, events.getFirst().malId());
        assertNull(events.getFirst().entry());
        assertEquals(LedgerStatus.BLACKLISTED, events.getFirst().previousStatus());
    }

    @Test
    void listenerFailureDoesNotPreventOtherListenersOrMutation() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        repository.add(entry(303, LedgerStatus.WATCHING));
        LedgerService service = new LedgerService(repository);
        List<LedgerChangeEvent> received = new ArrayList<>();
        service.subscribe(event -> { throw new IllegalStateException("listener failure"); });
        service.subscribe(received::add);

        assertTrue(service.changeStatus(303, LedgerStatus.ABANDONED));
        assertEquals(1, received.size());
        assertEquals(LedgerStatus.ABANDONED, repository.findByMalId(303).orElseThrow().status());
    }

    @Test
    void preferenceChangesAreIndependentOfStatusAndPublishUpdatedEntry() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        repository.add(entry(404, LedgerStatus.WATCHING));
        LedgerService service = new LedgerService(repository);
        List<LedgerChangeEvent> events = new ArrayList<>();
        service.subscribe(events::add);

        assertTrue(service.changePreference(404, AnimePreference.LIKE));
        LedgerEntry updated = repository.findByMalId(404).orElseThrow();

        assertEquals(LedgerStatus.WATCHING, updated.status());
        assertEquals(AnimePreference.LIKE, updated.preference());
        assertEquals(LedgerChangeEvent.Type.PREFERENCE_CHANGED, events.getFirst().type());
        assertEquals(AnimePreference.LIKE, events.getFirst().entry().preference());

        assertTrue(service.changePreference(404, null));
        assertNull(repository.findByMalId(404).orElseThrow().preference());
    }

    @Test
    void priorityIsLimitedToWatchLaterAndClearedWhenStatusChanges() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        repository.add(entry(405, LedgerStatus.WATCH_LATER));
        LedgerService service = new LedgerService(repository);

        assertTrue(service.changeWatchLaterPriority(405, 1));
        assertEquals(1, repository.findByMalId(405).orElseThrow().watchLaterPriority());
        assertTrue(service.changeStatus(405, LedgerStatus.WATCHING));
        assertNull(repository.findByMalId(405).orElseThrow().watchLaterPriority());
        assertFalse(service.changeWatchLaterPriority(405, 5));
        assertThrows(IllegalArgumentException.class, () -> service.changeWatchLaterPriority(405, 11));
    }

    private static LedgerEntry entry(long malId, LedgerStatus status) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new LedgerEntry(malId, "Anime " + malId, status, now, now);
    }

    private static final class InMemoryLedgerRepository implements LedgerRepository {
        private final Map<Long, LedgerEntry> entries = new HashMap<>();

        @Override public void add(LedgerEntry entry) { entries.put(entry.malId(), entry); }
        @Override public Optional<LedgerEntry> findByMalId(long malId) { return Optional.ofNullable(entries.get(malId)); }
        @Override public List<LedgerEntry> findAll() {
            return entries.values().stream().sorted(Comparator.comparingLong(LedgerEntry::malId)).toList();
        }
        @Override public List<LedgerEntry> findByStatus(LedgerStatus status) {
            return entries.values().stream().filter(entry -> entry.status() == status).toList();
        }
        @Override public List<LedgerEntry> searchByTitle(String query) {
            return entries.values().stream().filter(entry -> entry.title().contains(query)).toList();
        }
        @Override public boolean updateStatus(long malId, LedgerStatus status) {
            LedgerEntry current = entries.get(malId);
            if (current == null) return false;
            entries.put(malId, new LedgerEntry(current.malId(), current.title(), status,
                    current.dateAdded(), Instant.now(), current.preference(),
                    status == LedgerStatus.WATCH_LATER ? current.watchLaterPriority() : null));
            return true;
        }
        @Override public boolean updatePreferredTitle(long malId, String title) { return false; }
        @Override public boolean updatePreference(long malId, AnimePreference preference) {
            LedgerEntry current = entries.get(malId);
            if (current == null) return false;
            entries.put(malId, new LedgerEntry(current.malId(), current.title(), current.status(),
                    current.dateAdded(), Instant.now(), preference, current.watchLaterPriority()));
            return true;
        }
        @Override public boolean updateWatchLaterPriority(long malId, Integer priority) {
            LedgerEntry current = entries.get(malId);
            if (current == null || current.status() != LedgerStatus.WATCH_LATER) return false;
            entries.put(malId, new LedgerEntry(current.malId(), current.title(), current.status(),
                    current.dateAdded(), Instant.now(), current.preference(), priority));
            return true;
        }
        @Override public boolean remove(long malId) { return entries.remove(malId) != null; }
    }
}
