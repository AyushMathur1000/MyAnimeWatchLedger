package com.myanimewatchledger.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MalClientConfigurationStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingConfigurationReturnsEmptyWithoutCreatingFiles() throws Exception {
        Path file = temporaryDirectory.resolve("config").resolve("app.properties");
        MalClientConfigurationStore store = new MalClientConfigurationStore(file);

        assertEquals(Optional.empty(), store.loadClientId());
        assertFalse(Files.exists(file));
    }

    @Test
    void savesAndLoadsClientIdFromUtf8PropertiesFile() throws Exception {
        Path file = temporaryDirectory.resolve("nested").resolve("config").resolve("app.properties");
        MalClientConfigurationStore store = new MalClientConfigurationStore(file);

        store.saveClientId("  test-client-123  ");

        assertEquals(Optional.of("test-client-123"), store.loadClientId());
        assertTrue(Files.exists(file));
        assertTrue(Files.readString(file).contains("mal.client-id=test-client-123"));
    }

    @Test
    void rejectsBlankOrControlCharacterClientIds() {
        MalClientConfigurationStore store =
                new MalClientConfigurationStore(temporaryDirectory.resolve("app.properties"));

        assertThrows(IllegalArgumentException.class, () -> store.saveClientId("  "));
        assertThrows(IllegalArgumentException.class, () -> store.saveClientId("client\nforged"));
    }

    @Test
    void existingUnrelatedPropertiesArePreservedWhenClientIdChanges() throws Exception {
        Path file = temporaryDirectory.resolve("app.properties");
        Files.writeString(file, "ui.theme=dark\n");
        MalClientConfigurationStore store = new MalClientConfigurationStore(file);

        store.saveClientId("replacement-id");

        String contents = Files.readString(file);
        assertTrue(contents.contains("ui.theme=dark"));
        assertTrue(contents.contains("mal.client-id=replacement-id"));
    }
}
