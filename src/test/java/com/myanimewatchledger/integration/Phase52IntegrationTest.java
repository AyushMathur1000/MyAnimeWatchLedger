package com.myanimewatchledger.integration;

import com.myanimewatchledger.application.service.CatalogueService;
import com.myanimewatchledger.application.service.CatalogueTabService;
import com.myanimewatchledger.application.service.LedgerChangeEvent;
import com.myanimewatchledger.application.service.LedgerService;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.domain.catalogue.CatalogueAnime;
import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;
import com.myanimewatchledger.domain.catalogue.OpenCatalogueTab;
import com.myanimewatchledger.domain.catalogue.Season;
import com.myanimewatchledger.domain.catalogue.SearchTextNormalizer;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.infrastructure.persistence.DatabaseManager;
import com.myanimewatchledger.infrastructure.persistence.DatabaseMigrationRunner;
import com.myanimewatchledger.infrastructure.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class Phase52IntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void blacklistChangeRefreshesResultsForEveryOpenCatalogueTab() throws Exception {
        Path file = temporaryDirectory.resolve("blacklist-tabs.db");
        try (DatabaseManager database = initializedDatabase(file)) {
            PersistenceContext persistence = new PersistenceContext(database);
            CatalogueService catalogue = new CatalogueService(persistence.catalogue());
            CatalogueTabService tabs = new CatalogueTabService(
                    persistence.catalogue(), persistence.openCatalogueTabs());
            LedgerService ledger = new LedgerService(persistence.ledger());
            Instant now = Instant.parse("2026-11-14T00:00:00Z");

            long firstSearch = tabs.open(Season.WINTER, 2026, "").searchId();
            long secondSearch = tabs.open(Season.SPRING, 2026, "").searchId();
            AnimeMetadataCache shared = metadata(7101, "Shared Anime", now);
            AnimeMetadataCache other = metadata(7102, "Other Anime", now);
            persistence.animeMetadata().upsertAll(List.of(shared, other));
            persistence.catalogue().replaceMembership(firstSearch, List.of(7101L, 7102L));
            persistence.catalogue().replaceMembership(secondSearch, List.of(7101L));

            ledger.add(toCatalogueAnime(shared), LedgerStatus.WATCH_LATER);
            AtomicInteger refreshedTabCount = new AtomicInteger();
            AutoCloseable subscription = ledger.subscribe(event -> {
                if (event.type() == LedgerChangeEvent.Type.STATUS_CHANGED) {
                    for (OpenCatalogueTab tab : tabs.restoreOpenTabs()) {
                        catalogue.getAnime(tab.searchId(), tab.searchQuery());
                        refreshedTabCount.incrementAndGet();
                    }
                }
            });
            try {
                assertTrue(ledger.changeStatus(7101, LedgerStatus.BLACKLISTED));
                assertEquals(2, refreshedTabCount.get());
                assertEquals(List.of(7102L), catalogue.getAnime(firstSearch, "")
                        .stream().map(CatalogueAnime::malId).toList());
                assertTrue(catalogue.getAnime(secondSearch, "").isEmpty());

                refreshedTabCount.set(0);
                assertTrue(ledger.changeStatus(7101, LedgerStatus.WATCH_LATER));
                assertEquals(2, refreshedTabCount.get());
                assertEquals(List.of(7101L, 7102L), catalogue.getAnime(firstSearch, "")
                        .stream().map(CatalogueAnime::malId).toList());
                assertEquals(List.of(7101L), catalogue.getAnime(secondSearch, "")
                        .stream().map(CatalogueAnime::malId).toList());
            } finally {
                subscription.close();
            }
        }
    }

    @Test
    void catalogueSearchHistoryAndOpenTabStateSurviveDatabaseRestart() throws Exception {
        Path file = temporaryDirectory.resolve("catalogue-history.db");
        long searchId;
        OpenCatalogueTab expectedTab;
        try (DatabaseManager first = initializedDatabase(file)) {
            PersistenceContext persistence = new PersistenceContext(first);
            CatalogueTabService tabs = new CatalogueTabService(
                    persistence.catalogue(), persistence.openCatalogueTabs());
            expectedTab = tabs.open(Season.FALL, 2024, "  space opera  ");
            searchId = expectedTab.searchId();
            tabs.open(Season.WINTER, 2025, "mecha");
            tabs.reorder(List.of(tabs.restoreOpenTabs().get(1).searchId(), searchId));
        }

        try (DatabaseManager reopened = initializedDatabase(file)) {
            PersistenceContext persistence = new PersistenceContext(reopened);
            CatalogueTabService restored = new CatalogueTabService(
                    persistence.catalogue(), persistence.openCatalogueTabs());

            assertTrue(persistence.catalogue().findById(searchId).isPresent(),
                    "The historical catalogue search must remain available after restart.");
            assertEquals(2, persistence.catalogue().findAll().size());
            assertEquals(List.of(Season.WINTER, Season.FALL),
                    restored.restoreOpenTabs().stream().map(tab ->
                            persistence.catalogue().findById(tab.searchId()).orElseThrow().season()).toList());
            OpenCatalogueTab restoredFall = restored.restoreOpenTabs().stream()
                    .filter(tab -> tab.searchId() == searchId).findFirst().orElseThrow();
            assertEquals("space opera", restoredFall.searchQuery());
            assertEquals(1, restoredFall.tabOrder());
        }
    }

    @Test
    void clearingCatalogueCachePreservesLedgerAndAnimeDetailTabs() throws Exception {
        Path file = temporaryDirectory.resolve("cache-clear-isolation.db");
        try (DatabaseManager database = initializedDatabase(file)) {
            PersistenceContext persistence = new PersistenceContext(database);
            Instant now = Instant.parse("2026-11-14T00:00:00Z");
            persistence.ledger().add(new LedgerEntry(
                    7201, "Permanent Ledger Entry", LedgerStatus.HOLD, now, now));
            persistence.openAnimeDetailTabs().replaceAll(List.of(
                    new OpenAnimeDetailTab(7201, 0),
                    new OpenAnimeDetailTab(7202, 1)));
            long searchId = persistence.catalogue().findOrCreate(Season.SUMMER, 2023).searchId();
            persistence.animeMetadata().upsert(metadata(7201, "Permanent Ledger Entry", now));
            persistence.catalogue().replaceMembership(searchId, List.of(7201L));
            persistence.openCatalogueTabs().save(new OpenCatalogueTab(searchId, 0, "permanent"));

            new CatalogueService(persistence.catalogue()).clearCache();

            assertEquals(LedgerStatus.HOLD, persistence.ledger().findByMalId(7201).orElseThrow().status());
            assertEquals("Permanent Ledger Entry",
                    persistence.ledger().findByMalId(7201).orElseThrow().title());
            assertEquals(List.of(new OpenAnimeDetailTab(7201, 0), new OpenAnimeDetailTab(7202, 1)),
                    persistence.openAnimeDetailTabs().findAllOrdered());
            assertTrue(persistence.catalogue().findAll().isEmpty());
            assertTrue(persistence.openCatalogueTabs().findAllOrdered().isEmpty());
            assertTrue(persistence.animeMetadata().findByMalId(7201).isEmpty());
        }
    }

    private DatabaseManager initializedDatabase(Path file) throws Exception {
        DatabaseManager database = new DatabaseManager(file);
        database.initialize();
        new DatabaseMigrationRunner(database).migrate();
        return database;
    }

    private static AnimeMetadataCache metadata(long id, String title, Instant cachedAt) {
        String json = "{\"malId\":" + id + ",\"preferredTitle\":\"" + title + "\"}";
        return new AnimeMetadataCache(id, title, json, SearchTextNormalizer.normalize(title), cachedAt);
    }

    private static CatalogueAnime toCatalogueAnime(AnimeMetadataCache cache) {
        return new CatalogueAnime(cache.malId(), cache.preferredTitle(), cache.metadataJson(),
                cache.searchableText(), cache.cachedAt(), 0, null);
    }
}
