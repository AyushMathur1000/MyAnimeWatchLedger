package com.myanimewatchledger.presentation.state;

import com.myanimewatchledger.presentation.main.MainNavigation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WindowStateStoreTest {
    @TempDir Path temp;

    @Test
    void returnsMaximizedDefaultsWhenNoPreferencesExist() {
        assertEquals(WindowState.defaults(), new WindowStateStore(temp.resolve("ui.properties")).load());
    }

    @Test
    void savesAndRestoresWindowGeometryAndLastWorkspace() throws Exception {
        var store = new WindowStateStore(temp.resolve("config/ui.properties"));
        var expected = new WindowState(1360, 900, false, MainNavigation.CATALOGUE);

        store.save(expected);

        assertEquals(expected, store.load());
    }

    @Test
    void defaultsBackgroundDetailOpeningToDisabled() {
        var store = new WindowStateStore(temp.resolve("ui.properties"));
        assertFalse(store.loadOpenAnimeDetailsInBackground());
    }

    @Test
    void persistsBackgroundDetailPreferenceWithoutLosingWindowState() throws Exception {
        var store = new WindowStateStore(temp.resolve("config/ui.properties"));
        store.save(new WindowState(1360, 900, false, MainNavigation.CATALOGUE));
        store.saveOpenAnimeDetailsInBackground(true);
        store.save(new WindowState(1400, 920, true, MainNavigation.ANIME_DETAILS));

        assertTrue(store.loadOpenAnimeDetailsInBackground());
        assertEquals(MainNavigation.ANIME_DETAILS, store.load().lastNavigation());
    }

    @Test
    void catalogueLedgerFilterDefaultsOffAndPersistsWithoutLosingOtherPreferences() throws Exception {
        var store = new WindowStateStore(temp.resolve("config/ui.properties"));
        assertFalse(store.loadHideLedgerEntries());
        store.saveUiSoundsEnabled(false);
        store.saveHideLedgerEntries(true);
        assertTrue(store.loadHideLedgerEntries());
        assertFalse(store.loadUiSoundsEnabled());
        store.saveHideLedgerEntries(false);
        assertFalse(store.loadHideLedgerEntries());
    }

    @Test
    void rejectsUnsupportedWindowDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new WindowState(640, 480, false, MainNavigation.MY_LEDGER));
    }
    @Test
    void cataloguePresentationDefaultsToGridForExistingInstallations() {
        var store = new WindowStateStore(temp.resolve("ui.properties"));
        assertEquals(CataloguePresentation.GRID, store.loadCataloguePresentation());
    }

    @Test
    void persistsCataloguePresentationWithoutLosingOtherPreferences() throws Exception {
        var store = new WindowStateStore(temp.resolve("config/ui.properties"));
        store.saveUiSoundsEnabled(false);
        store.saveHideLedgerEntries(true);

        store.saveCataloguePresentation(CataloguePresentation.SINGLE_CARD_CAROUSEL);

        assertEquals(CataloguePresentation.SINGLE_CARD_CAROUSEL, store.loadCataloguePresentation());
        assertFalse(store.loadUiSoundsEnabled());
        assertTrue(store.loadHideLedgerEntries());
        store.saveCataloguePresentation(CataloguePresentation.GRID);
        assertEquals(CataloguePresentation.GRID, store.loadCataloguePresentation());
    }

    @Test
    void invalidCataloguePresentationFallsBackToGrid() throws Exception {
        Path file = temp.resolve("config/ui.properties");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "catalogue.presentation=UNKNOWN\n");
        assertEquals(CataloguePresentation.GRID, new WindowStateStore(file).loadCataloguePresentation());
    }

    @Test
    void automaticBackupSettingsUseApprovedDefaultsAndPersistOutsideOtherPreferences() throws Exception {
        var store = new WindowStateStore(temp.resolve("config/ui.properties"));
        var defaults = store.loadAutomaticBackupSettings();
        assertTrue(defaults.enabled());
        assertEquals(com.myanimewatchledger.config.BackupFrequency.WEEKLY, defaults.frequency());
        assertEquals(2, defaults.retentionCount());
        assertEquals(com.myanimewatchledger.config.ApplicationPaths.dataDirectory().resolve("backups"),
                defaults.destination());

        var changed = new com.myanimewatchledger.config.AutomaticBackupSettings(false,
                com.myanimewatchledger.config.BackupFrequency.MONTHLY, 5, temp.resolve("custom-backups"));
        store.saveAutomaticBackupSettings(changed);
        store.saveLastAutomaticBackup(java.time.Instant.parse("2026-11-17T10:00:00Z"));
        assertEquals(changed, store.loadAutomaticBackupSettings());
        assertEquals(java.time.Instant.parse("2026-11-17T10:00:00Z"), store.loadLastAutomaticBackup());
        assertFalse(store.loadUiSoundsEnabled());
    }

    @Test
    void automaticBackupSettingsRejectInvalidRetentionCounts() {
        assertThrows(IllegalArgumentException.class, () ->
                new com.myanimewatchledger.config.AutomaticBackupSettings(true,
                        com.myanimewatchledger.config.BackupFrequency.WEEKLY, 0, temp));
    }

}
