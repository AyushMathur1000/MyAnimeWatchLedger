package com.myanimewatchledger.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.config.MalApiConfiguration;
import com.myanimewatchledger.config.MalClientConfigurationStore;
import com.myanimewatchledger.infrastructure.mal.MalApiClient;
import com.myanimewatchledger.infrastructure.mal.SwitchableMalApiTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Owns the persisted MAL Client ID and the replaceable runtime API connection. */
public final class MalApiConnection {
    private static final long CONNECTION_TEST_ANIME_ID = 1L;

    private final MalClientConfigurationStore configurationStore;
    private final SwitchableMalApiTransport transport;
    private final ObjectMapper json = new ObjectMapper();

    public MalApiConnection(
            MalClientConfigurationStore configurationStore,
            SwitchableMalApiTransport transport) {
        this.configurationStore = Objects.requireNonNull(configurationStore, "configurationStore");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /** Loads existing credentials without making a network request. */
    public void load() throws IOException {
        Optional<String> clientId = configurationStore.loadClientId();
        clientId.ifPresent(this::installClient);
    }

    /** Persists and activates a new Client ID. Construct the client before persisting it. */
    public void saveClientId(String clientId) throws IOException {
        MalApiClient candidate = new MalApiClient(MalApiConfiguration.defaults(clientId));
        configurationStore.saveClientId(clientId);
        transport.setDelegate(candidate);
    }

    public boolean isConfigured() {
        return transport.isConfigured();
    }

    /**
     * Performs an explicit, read-only request and validates the returned MAL ID.
     * This method is blocking and must only be called by a background worker.
     */
    public void testConnection() {
        String response = transport.get("anime/" + CONNECTION_TEST_ANIME_ID + "?fields=id");
        try {
            JsonNode root = json.readTree(response);
            if (root == null || !root.isObject()
                    || root.path("id").asLong(-1) != CONNECTION_TEST_ANIME_ID) {
                throw new IllegalStateException("MAL returned an unexpected response to the connection test.");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("MAL returned unreadable JSON during the connection test.", failure);
        }
    }

    private void installClient(String clientId) {
        transport.setDelegate(new MalApiClient(MalApiConfiguration.defaults(clientId)));
    }
}
