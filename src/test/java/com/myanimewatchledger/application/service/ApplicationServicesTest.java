package com.myanimewatchledger.application.service;

import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.infrastructure.persistence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationServicesTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseManager database;
    private PersistenceContext persistence;
    private LedgerService ledger;
    private CatalogueService catalogue;
    private CatalogueTabService tabs;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(temporaryDirectory.resolve("services.db"));
        database.initialize();
        new DatabaseMigrationRunner(database).migrate();
        persistence = new PersistenceContext(database);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
        ledger = new LedgerService(persistence.ledger(), fixedClock);
        catalogue = new CatalogueService(persistence.catalogue());
        tabs = new CatalogueTabService(persistence.catalogue(), persistence.openCatalogueTabs());
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void ledgerServiceAddsAnimeAndPreventsDuplicates() {
        CatalogueAnime anime = anime(1001, "Example Anime");
        var added = ledger.add(anime, LedgerStatus.WATCHING);

        assertEquals(1001, added.malId());
        assertEquals("Example Anime", added.title());
        assertEquals(LedgerStatus.WATCHING, added.status());
        assertEquals(Instant.parse("2026-11-13T00:00:00Z"), added.dateAdded());
        assertEquals(added, ledger.find(1001).orElseThrow());
        assertThrows(IllegalStateException.class, () -> ledger.add(anime, LedgerStatus.HOLD));
    }

    @Test
    void ledgerServiceSupportsStatusRenameSearchAndRemoval() {
        ledger.add(anime(1002, "Original Title"), LedgerStatus.HOLD);

        assertTrue(ledger.changeStatus(1002, LedgerStatus.WATCHED));
        assertTrue(ledger.rename(1002, "Renamed Title"));
        assertEquals(1, ledger.search("renamed").size());
        assertEquals(LedgerStatus.WATCHED, ledger.find(1002).orElseThrow().status());
        assertTrue(ledger.remove(1002));
        assertTrue(ledger.find(1002).isEmpty());
    }

    @Test
    void catalogueSearchUsesDistinctWhitespaceTokensWithAndSemantics() {
        var season = catalogue.getOrCreateSeason(Season.WINTER, 2026);
        persistence.animeMetadata().upsert(new AnimeMetadataCache(
                2001, "Alpha Beta", "{\"title\":\"Alpha Beta\"}", "alpha beta fantasy", Instant.now()));
        persistence.animeMetadata().upsert(new AnimeMetadataCache(
                2002, "Alpha Gamma", "{\"title\":\"Alpha Gamma\"}", "alpha gamma science fiction", Instant.now()));
        persistence.catalogue().replaceMembership(season.searchId(), List.of(2001L, 2002L));

        assertEquals(List.of(2001L), catalogue.getAnime(season.searchId(), " ALPHA   fantasy alpha ")
                .stream().map(CatalogueAnime::malId).toList());
        assertTrue(catalogue.getAnime(season.searchId(), "alpha absent").isEmpty());
        assertEquals(List.of(), CatalogueSearchService.tokenize("   "));
    }

    @Test
    void searchTokenizationNormalizesUnicodeCompatibilityCharacters() {
        assertEquals(List.of("alpha", "beta"), CatalogueSearchService.tokenize("ＡLPHA  Beta"));
    }

    @Test
    void seasonNavigationHandlesBoundariesAndYearChanges() {
        assertEquals(new SeasonNavigator.SeasonYear(Season.WINTER, 2026),
                SeasonNavigator.fromDate(LocalDate.of(2026, 3, 31)));
        assertEquals(new SeasonNavigator.SeasonYear(Season.SPRING, 2026),
                SeasonNavigator.fromDate(LocalDate.of(2026, 4, 1)));
        assertEquals(new SeasonNavigator.SeasonYear(Season.WINTER, 2027),
                SeasonNavigator.next(new SeasonNavigator.SeasonYear(Season.FALL, 2026)));
        assertEquals(new SeasonNavigator.SeasonYear(Season.FALL, 2025),
                SeasonNavigator.previous(new SeasonNavigator.SeasonYear(Season.WINTER, 2026)));
    }

    @Test
    void catalogueTabsCanBeRestoredUpdatedReorderedAndClosed() {
        var winter = tabs.open(Season.WINTER, 2026, "  mecha ");
        var spring = tabs.open(Season.SPRING, 2026, "fantasy");
        var reopened = tabs.open(Season.WINTER, 2026, "  action ");

        assertEquals(winter.searchId(), reopened.searchId());
        assertEquals(2, tabs.restoreOpenTabs().size());
        assertEquals("action", tabs.restoreOpenTabs().getFirst().searchQuery());

        tabs.reorder(List.of(spring.searchId(), winter.searchId()));
        assertEquals(List.of(spring.searchId(), winter.searchId()),
                tabs.restoreOpenTabs().stream().map(OpenCatalogueTab::searchId).toList());
        assertThrows(IllegalArgumentException.class, () -> tabs.reorder(List.of(winter.searchId())));
        assertTrue(tabs.close(spring.searchId()));
        assertEquals(0, tabs.restoreOpenTabs().getFirst().tabOrder());
        assertFalse(tabs.close(spring.searchId()));
    }

    private static CatalogueAnime anime(long id, String title) {
        return new CatalogueAnime(id, title, "{\"title\":\"" + title + "\"}",
                title.toLowerCase(), Instant.parse("2026-11-13T00:00:00Z"), 0, null);
    }
}
