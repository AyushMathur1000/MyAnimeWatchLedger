package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.AnimeMetadataRepository;
import com.myanimewatchledger.application.port.CatalogueRepository;
import com.myanimewatchledger.application.port.MalCatalogueGateway;
import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.infrastructure.mal.MalResponseMapper;
import com.myanimewatchledger.infrastructure.persistence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogueRefreshServiceTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseManager database;
    private PersistenceContext persistence;
    private final Instant completedAt = Instant.parse("2026-11-14T00:00:00Z");
    private final MalResponseMapper mapper = new MalResponseMapper();

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(temporaryDirectory.resolve("refresh.db"));
        database.initialize();
        new DatabaseMigrationRunner(database).migrate();
        persistence = new PersistenceContext(database);
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    @Test
    void completeRefreshPersistsMetadataMembershipAndFetchStateAtomically() {
        Instant addedAt = Instant.parse("2025-01-02T03:04:05Z");
        persistence.ledger().add(new LedgerEntry(
                1001, "Old Title", LedgerStatus.WATCHING, addedAt, addedAt));

        CatalogueRefreshService service = service((season, year) -> new SeasonalCatalogue(
                List.of(item(1001, "New Preferred Title"), item(1002, "Second Anime")),
                1, List.of("One malformed record was skipped.")));

        CatalogueRefreshOutcome outcome = service.refresh(Season.WINTER, 2026);

        CatalogueSearch search = persistence.catalogue().findById(outcome.searchId()).orElseThrow();
        assertEquals(2, outcome.animeCount());
        assertEquals(1, outcome.skippedItems());
        assertEquals(FetchStatus.SUCCESS, search.fetchStatus());
        assertEquals(completedAt, search.lastSuccessfulFetchAt());
        assertEquals(List.of(1001L, 1002L), persistence.catalogue()
                .findEligibleAnime(search.searchId(), List.of()).stream()
                .map(CatalogueAnime::malId).toList());

        LedgerEntry synchronizedEntry = persistence.ledger().findByMalId(1001).orElseThrow();
        assertEquals("New Preferred Title", synchronizedEntry.title());
        assertEquals(LedgerStatus.WATCHING, synchronizedEntry.status());
        assertEquals(addedAt, synchronizedEntry.dateAdded());
        assertTrue(persistence.ledger().findByMalId(1002).isEmpty(),
                "A catalogue refresh must never create a ledger entry.");
    }

    @Test
    void successfulEmptyCatalogueReplacesOldMembershipWithEmptyMembership() {
        CatalogueSearch search = persistence.catalogue().findOrCreate(Season.SPRING, 2024);
        persistence.animeMetadata().upsert(item(2001, "Previously Cached"));
        persistence.catalogue().replaceMembership(search.searchId(), List.of(2001L));

        CatalogueRefreshOutcome outcome = service((season, year) ->
                new SeasonalCatalogue(List.of(), 0, List.of()))
                .refresh(Season.SPRING, 2024);

        assertEquals(0, outcome.animeCount());
        assertTrue(persistence.catalogue().findEligibleAnime(search.searchId(), List.of()).isEmpty());
        assertEquals(FetchStatus.SUCCESS,
                persistence.catalogue().findById(search.searchId()).orElseThrow().fetchStatus());
        assertTrue(persistence.animeMetadata().findByMalId(2001).isPresent());
    }

    @Test
    void gatewayFailurePreservesPreviousMembershipMetadataAndSuccessfulTimestamp() {
        CatalogueSearch search = persistence.catalogue().findOrCreate(Season.SUMMER, 2023);
        Instant previousSuccess = Instant.parse("2026-06-01T00:00:00Z");
        AnimeMetadataCache previous = item(3001, "Previous Result");
        persistence.animeMetadata().upsert(previous);
        persistence.catalogue().replaceMembership(search.searchId(), List.of(3001L));
        persistence.catalogue().markFetchSucceeded(search.searchId(), previousSuccess);

        CatalogueRefreshService service = service((season, year) -> {
            throw new IllegalStateException("Later MAL page failed.");
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.refresh(Season.SUMMER, 2023));

        assertEquals("Later MAL page failed.", failure.getMessage());
        CatalogueSearch failedSearch = persistence.catalogue().findById(search.searchId()).orElseThrow();
        assertEquals(FetchStatus.FAILED, failedSearch.fetchStatus());
        assertEquals(previousSuccess, failedSearch.lastSuccessfulFetchAt());
        assertEquals("Later MAL page failed.", failedSearch.lastError());
        assertEquals(List.of(3001L), persistence.catalogue().findEligibleAnime(search.searchId(), List.of())
                .stream().map(CatalogueAnime::malId).toList());
        assertEquals(previous.metadataJson(),
                persistence.animeMetadata().findByMalId(3001).orElseThrow().metadataJson());
    }

    @Test
    void databaseFailureRollsBackMetadataLedgerAndMembershipThenRecordsFailure() {
        CatalogueSearch search = persistence.catalogue().findOrCreate(Season.FALL, 2022);
        persistence.animeMetadata().upsert(item(4001, "Old Cache"));
        persistence.catalogue().replaceMembership(search.searchId(), List.of(4001L));

        AnimeMetadataRepository failingMetadata = new AnimeMetadataRepository() {
            @Override public void upsert(AnimeMetadataCache value) {
                persistence.animeMetadata().upsert(value);
                throw new IllegalStateException("Simulated persistence failure.");
            }
            @Override public void upsertAll(java.util.Collection<AnimeMetadataCache> values) {
                for (AnimeMetadataCache value : values) upsert(value);
            }
            @Override public java.util.Optional<AnimeMetadataCache> findByMalId(long id) {
                return persistence.animeMetadata().findByMalId(id);
            }
            @Override public void deleteAll() { persistence.animeMetadata().deleteAll(); }
        };

        CatalogueRefreshService service = new CatalogueRefreshService(
                (season, year) -> new SeasonalCatalogue(
                        List.of(item(4001, "New Cache"), item(4002, "New Anime")), 0, List.of()),
                persistence.catalogue(), failingMetadata, mapper, persistence.ledger(),
                persistence.transactions(), Clock.fixed(completedAt, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class,
                () -> service.refresh(Season.FALL, 2022));

        assertTrue(persistence.animeMetadata().findByMalId(4002).isEmpty());
        assertEquals("Old Cache", persistence.animeMetadata().findByMalId(4001).orElseThrow().preferredTitle());
        assertEquals(List.of(4001L), persistence.catalogue().findEligibleAnime(search.searchId(), List.of())
                .stream().map(CatalogueAnime::malId).toList());
        CatalogueSearch failed = persistence.catalogue().findById(search.searchId()).orElseThrow();
        assertEquals(FetchStatus.FAILED, failed.fetchStatus());
        assertNull(failed.lastSuccessfulFetchAt());
    }

    @Test
    void failureAfterLedgerSynchronizationRollsBackTheEntireRefresh() {
        CatalogueSearch search = persistence.catalogue().findOrCreate(Season.FALL, 2021);
        Instant addedAt = Instant.parse("2022-02-03T00:00:00Z");
        Instant previousSuccess = Instant.parse("2026-09-01T00:00:00Z");
        persistence.ledger().add(new LedgerEntry(4101, "Old Ledger Title", LedgerStatus.WATCHING, addedAt, addedAt));
        persistence.animeMetadata().upsert(item(4101, "Old Ledger Title"));
        persistence.catalogue().replaceMembership(search.searchId(), List.of(4101L));
        persistence.catalogue().markFetchSucceeded(search.searchId(), previousSuccess);

        CatalogueRepository failingCatalogue = new CatalogueRepository() {
            @Override public CatalogueSearch findOrCreate(Season season, int year) {
                return persistence.catalogue().findOrCreate(season, year);
            }
            @Override public java.util.Optional<CatalogueSearch> findById(long id) {
                return persistence.catalogue().findById(id);
            }
            @Override public List<CatalogueSearch> findAll() {
                return persistence.catalogue().findAll();
            }
            @Override public void markFetchSucceeded(long id, Instant at) {
                throw new IllegalStateException("Simulated failure after ledger synchronization.");
            }
            @Override public void markFetchFailed(long id, Instant at, String error) {
                persistence.catalogue().markFetchFailed(id, at, error);
            }
            @Override public void replaceMembership(long id, List<Long> ids) {
                persistence.catalogue().replaceMembership(id, ids);
            }
            @Override public List<CatalogueAnime> findEligibleAnime(long id, List<String> tokens) {
                return persistence.catalogue().findEligibleAnime(id, tokens);
            }
            @Override public void clearCache() {
                persistence.catalogue().clearCache();
            }
        };

        CatalogueRefreshService service = new CatalogueRefreshService(
                (season, year) -> new SeasonalCatalogue(List.of(item(4101, "New MAL Title")), 0, List.of()),
                failingCatalogue, persistence.animeMetadata(), mapper, persistence.ledger(),
                persistence.transactions(), Clock.fixed(completedAt, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> service.refresh(Season.FALL, 2021));

        LedgerEntry restoredLedger = persistence.ledger().findByMalId(4101).orElseThrow();
        assertEquals("Old Ledger Title", restoredLedger.title());
        assertEquals(LedgerStatus.WATCHING, restoredLedger.status());
        assertEquals(addedAt, restoredLedger.dateAdded());
        assertEquals("Old Ledger Title", persistence.animeMetadata().findByMalId(4101).orElseThrow().preferredTitle());
        assertEquals(List.of(4101L), persistence.catalogue().findEligibleAnime(search.searchId(), List.of())
                .stream().map(CatalogueAnime::malId).toList());
        CatalogueSearch failed = persistence.catalogue().findById(search.searchId()).orElseThrow();
        assertEquals(FetchStatus.FAILED, failed.fetchStatus());
        assertEquals(previousSuccess, failed.lastSuccessfulFetchAt());
    }

    @Test
    void refreshDoesNotChangeLedgerWhenPreferredTitleIsUnchanged() {
        Instant addedAt = Instant.parse("2024-02-03T00:00:00Z");
        LedgerEntry original = new LedgerEntry(5001, "Same Title", LedgerStatus.HOLD, addedAt, addedAt);
        persistence.ledger().add(original);

        service((season, year) -> new SeasonalCatalogue(
                List.of(item(5001, "Same Title")), 0, List.of()))
                .refresh(Season.WINTER, 2025);

        assertEquals(original, persistence.ledger().findByMalId(5001).orElseThrow());
    }

    @Test
    void blacklistIsStillExcludedAfterRefresh() {
        Instant now = Instant.parse("2025-03-04T00:00:00Z");
        persistence.ledger().add(new LedgerEntry(6001, "Blocked", LedgerStatus.BLACKLISTED, now, now));

        CatalogueRefreshOutcome outcome = service((season, year) -> new SeasonalCatalogue(
                List.of(item(6001, "Blocked"), item(6002, "Visible")), 0, List.of()))
                .refresh(Season.WINTER, 2026);

        assertEquals(List.of(6002L), persistence.catalogue().findEligibleAnime(outcome.searchId(), List.of())
                .stream().map(CatalogueAnime::malId).toList());
    }

    @Test
    void cachedOptionalMetadataSurvivesARefreshThatOmitsIt() throws Exception {
        var oldNode = mapperNode("""
                {"id":7001,"title":"Example","synopsis":"Previously known synopsis",
                 "genres":[{"id":1,"name":"Drama"}]}
                """);
        var newNode = mapperNode("""
                {"id":7001,"title":"Example","synopsis":null,"genres":[]}
                """);
        AnimeMetadataCache oldCache = mapper.mapToCache(oldNode);
        AnimeMetadataCache freshCache = mapper.mapToCache(newNode);
        persistence.animeMetadata().upsert(oldCache);

        service((season, year) -> new SeasonalCatalogue(List.of(freshCache), 0, List.of()))
                .refresh(Season.SPRING, 2026);

        AnimeMetadataCache merged = persistence.animeMetadata().findByMalId(7001).orElseThrow();
        assertTrue(merged.metadataJson().contains("Previously known synopsis"));
        assertTrue(merged.searchableText().contains("previously known synopsis"));
        assertTrue(merged.metadataJson().contains("Drama"));
    }

    private CatalogueRefreshService service(MalCatalogueGateway gateway) {
        return new CatalogueRefreshService(gateway, persistence.catalogue(), persistence.animeMetadata(),
                mapper, persistence.ledger(), persistence.transactions(),
                Clock.fixed(completedAt, ZoneOffset.UTC));
    }

    private static AnimeMetadataCache item(long id, String title) {
        String json = "{\"malId\":" + id + ",\"preferredTitle\":\"" + title + "\"}";
        return new AnimeMetadataCache(id, title, json, SearchTextNormalizer.normalize(title), Instant.EPOCH);
    }

    private static com.fasterxml.jackson.databind.JsonNode mapperNode(String json) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    }
}
