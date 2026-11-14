package com.myanimewatchledger.app;

import com.myanimewatchledger.config.MalClientConfigurationStore;
import com.myanimewatchledger.infrastructure.mal.SwitchableMalApiTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MalApiConnectionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndActivatesClientIdWithoutExposingItThroughConnectionState() throws Exception {
        MalClientConfigurationStore store =
                new MalClientConfigurationStore(temporaryDirectory.resolve("config/app.properties"));
        SwitchableMalApiTransport transport = new SwitchableMalApiTransport();
        MalApiConnection connection = new MalApiConnection(store, transport);

        assertFalse(connection.isConfigured());
        connection.saveClientId("client-secret-value");

        assertTrue(connection.isConfigured());
        assertEquals(Optional.of("client-secret-value"), store.loadClientId());
    }

    @Test
    void loadsPreviouslySavedClientIdWithoutMakingNetworkRequest() throws Exception {
        MalClientConfigurationStore store =
                new MalClientConfigurationStore(temporaryDirectory.resolve("config/app.properties"));
        store.saveClientId("saved-client-id");
        SwitchableMalApiTransport transport = new SwitchableMalApiTransport();
        MalApiConnection connection = new MalApiConnection(store, transport);

        connection.load();

        assertTrue(connection.isConfigured());
    }

    @Test
    void rejectsInvalidClientIdWithoutReplacingActiveTransport() throws Exception {
        MalClientConfigurationStore store =
                new MalClientConfigurationStore(temporaryDirectory.resolve("config/app.properties"));
        SwitchableMalApiTransport transport = new SwitchableMalApiTransport();
        MalApiConnection connection = new MalApiConnection(store, transport);
        connection.saveClientId("valid-client-id");

        assertThrows(IllegalArgumentException.class, () -> connection.saveClientId("  "));
        assertEquals(Optional.of("valid-client-id"), store.loadClientId());
        assertTrue(connection.isConfigured());
    }

    @Test
    void connectionTestUsesLightweightAnimeLookupAndValidatesResponse() {
        SwitchableMalApiTransport transport = new SwitchableMalApiTransport();
        RecordingTransport fake = new RecordingTransport("{\"id\":1}");
        transport.setDelegate(fake);
        MalApiConnection connection = new MalApiConnection(
                new MalClientConfigurationStore(temporaryDirectory.resolve("unused.properties")), transport);

        connection.testConnection();

        assertEquals("anime/1?fields=id", fake.lastPath);
    }

    @Test
    void connectionTestRejectsUnexpectedAnimeId() {
        SwitchableMalApiTransport transport = new SwitchableMalApiTransport();
        transport.setDelegate(new RecordingTransport("{\"id\":2}"));
        MalApiConnection connection = new MalApiConnection(
                new MalClientConfigurationStore(temporaryDirectory.resolve("unused.properties")), transport);

        assertThrows(IllegalStateException.class, connection::testConnection);
    }

    private static final class RecordingTransport
            implements com.myanimewatchledger.infrastructure.mal.MalApiTransport {
        private final String response;
        private String lastPath;

        private RecordingTransport(String response) {
            this.response = response;
        }

        @Override
        public String get(String relativePathAndQuery) {
            lastPath = relativePathAndQuery;
            return response;
        }

        @Override
        public String getNextPage(URI nextPageUri) {
            throw new AssertionError("Pagination is not used by the connection test.");
        }
    }
}
