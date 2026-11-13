package com.myanimewatchledger.presentation.state;

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
    void savesAndRestoresWindowGeometry() throws Exception {
        var store = new WindowStateStore(temp.resolve("config/ui.properties"));
        var expected = new WindowState(1360, 900, false);

        store.save(expected);

        assertEquals(expected, store.load());
    }

    @Test
    void rejectsUnsupportedWindowDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new WindowState(640, 480, false));
    }
}
