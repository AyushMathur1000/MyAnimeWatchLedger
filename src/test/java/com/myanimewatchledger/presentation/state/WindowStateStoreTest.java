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
    void rejectsUnsupportedWindowDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new WindowState(640, 480, false, MainNavigation.MY_LEDGER));
    }
}
