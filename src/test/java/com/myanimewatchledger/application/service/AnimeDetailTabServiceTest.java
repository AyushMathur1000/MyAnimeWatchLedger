package com.myanimewatchledger.application.service;

import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;
import com.myanimewatchledger.infrastructure.persistence.DatabaseManager;
import com.myanimewatchledger.infrastructure.persistence.DatabaseMigrationRunner;
import com.myanimewatchledger.infrastructure.persistence.JdbcOpenAnimeDetailTabRepository;
import com.myanimewatchledger.infrastructure.persistence.DatabaseTransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimeDetailTabServiceTest {
    @TempDir Path temporaryDirectory;
    private DatabaseManager database;
    private AnimeDetailTabService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(temporaryDirectory.resolve("detail-tabs.db"));
        database.initialize();
        new DatabaseMigrationRunner(database).migrate();
        service = new AnimeDetailTabService(
                new JdbcOpenAnimeDetailTabRepository(new DatabaseTransactionManager(database)));
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    @Test
    void persistsTabsAndOrderAcrossServiceRecreation() {
        OpenAnimeDetailTab first = service.open(1001);
        OpenAnimeDetailTab second = service.open(1002);
        assertEquals(0, first.tabOrder());
        assertEquals(1, second.tabOrder());

        AnimeDetailTabService afterRestart = new AnimeDetailTabService(
                new JdbcOpenAnimeDetailTabRepository(new DatabaseTransactionManager(database)));
        assertEquals(List.of(first, second), afterRestart.restoreOpenTabs());
    }

    @Test
    void openingSameAnimeDoesNotCreateDuplicateTab() {
        OpenAnimeDetailTab first = service.open(2001);
        OpenAnimeDetailTab reopened = service.open(2001);
        assertEquals(first, reopened);
        assertEquals(List.of(first), service.restoreOpenTabs());
    }

    @Test
    void closingTabOnlyRemovesTabStateAndCompactsOrder() {
        service.open(3001);
        service.open(3002);
        service.open(3003);

        assertTrue(service.close(3002));
        assertEquals(List.of(new OpenAnimeDetailTab(3001, 0), new OpenAnimeDetailTab(3003, 1)),
                service.restoreOpenTabs());
        assertFalse(service.close(3002));
    }

    @Test
    void reorderRequiresEveryOpenAnimeExactlyOnce() {
        service.open(4001);
        service.open(4002);
        service.open(4003);

        service.reorder(List.of(4003L, 4001L, 4002L));
        assertEquals(List.of(4003L, 4001L, 4002L),
                service.restoreOpenTabs().stream().map(OpenAnimeDetailTab::malId).toList());
        assertThrows(IllegalArgumentException.class, () -> service.reorder(List.of(4001L, 4001L)));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(List.of(4001L, 4002L)));
    }

    @Test
    void rejectsInvalidAnimeIds() {
        assertThrows(IllegalArgumentException.class, () -> service.open(0));
        assertThrows(IllegalArgumentException.class, () -> service.close(-1));
    }
}
