package com.myanimewatchledger.integration;

import com.myanimewatchledger.application.service.CatalogueRefreshService;
import com.myanimewatchledger.application.service.CatalogueService;
import com.myanimewatchledger.application.service.SeasonNavigator;
import com.myanimewatchledger.config.ApplicationPaths;
import com.myanimewatchledger.config.MalApiConfiguration;
import com.myanimewatchledger.config.MalClientConfigurationStore;
import com.myanimewatchledger.infrastructure.mal.MalApiCatalogueGateway;
import com.myanimewatchledger.infrastructure.mal.MalApiClient;
import com.myanimewatchledger.infrastructure.mal.MalResponseMapper;
import com.myanimewatchledger.infrastructure.persistence.DatabaseManager;
import com.myanimewatchledger.infrastructure.persistence.DatabaseMigrationRunner;
import com.myanimewatchledger.infrastructure.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in end-to-end smoke test. Reads the Client ID from the normal local
 * application configuration and never embeds credentials in source or logs.
 *
 * Enable explicitly with: mvn -Dmal.live=true -Dtest=MalCatalogueLiveSmokeTest test
 */
@EnabledIfSystemProperty(named = "mal.live", matches = "true")
class MalCatalogueLiveSmokeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void retrievesPersistsAndReloadsARealSeasonalCatalogue() throws Exception {
        var clientId = new MalClientConfigurationStore().loadClientId();
        assertTrue(clientId.isPresent(), "Configure a MAL Client ID in the application settings file first.");

        var configuration = MalApiConfiguration.defaults(clientId.orElseThrow());
        var client = new MalApiClient(configuration);
        var mapper = new MalResponseMapper();
        var current = SeasonNavigator.current(Clock.systemDefaultZone());
        Path databaseFile = temporaryDirectory.resolve("live-smoke.db");

        DatabaseManager firstDatabase = new DatabaseManager(databaseFile);
        long searchId;
        int fetchedCount;
        try {
            firstDatabase.initialize();
            new DatabaseMigrationRunner(firstDatabase).migrate();
            PersistenceContext persistence = new PersistenceContext(firstDatabase);
            var refresh = new CatalogueRefreshService(
                    new MalApiCatalogueGateway(client, client.baseUri()),
                    persistence.catalogue(), persistence.animeMetadata(), mapper,
                    persistence.ledger(), persistence.transactions());

            var outcome = refresh.refresh(current.season(), current.year());
            searchId = outcome.searchId();
            fetchedCount = outcome.animeCount();
            assertTrue(fetchedCount > 0, "MAL returned no anime for the current season.");
        } finally {
            firstDatabase.close();
        }

        // A fresh database manager simulates an application restart.
        DatabaseManager restartedDatabase = new DatabaseManager(databaseFile);
        try {
            restartedDatabase.initialize();
            new DatabaseMigrationRunner(restartedDatabase).migrate();
            PersistenceContext restarted = new PersistenceContext(restartedDatabase);
            var search = restarted.catalogue().findById(searchId).orElseThrow();
            assertEquals(com.myanimewatchledger.domain.catalogue.FetchStatus.SUCCESS, search.fetchStatus());
            var cached = new CatalogueService(restarted.catalogue()).getAnime(searchId, "");
            assertEquals(fetchedCount, cached.size());
            assertFalse(cached.isEmpty(), "Persisted catalogue should be available without another API call.");
        } finally {
            restartedDatabase.close();
        }
    }
}
