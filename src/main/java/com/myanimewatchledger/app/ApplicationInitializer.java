package com.myanimewatchledger.app;

import com.myanimewatchledger.application.service.*;
import com.myanimewatchledger.config.*;
import com.myanimewatchledger.infrastructure.mal.*;
import com.myanimewatchledger.infrastructure.persistence.*;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Coordinates startup resources and application service wiring. */
public final class ApplicationInitializer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ApplicationInitializer.class.getName());

    private final Path databaseFile;
    private final MalClientConfigurationStore configStore;
    private DatabaseManager database;
    private PersistenceContext persistence;
    private BackgroundTaskExecutor background;
    private ApplicationServices services;

    public ApplicationInitializer() {
        this(ApplicationPaths.databaseFile(), new MalClientConfigurationStore());
    }

    ApplicationInitializer(Path db, MalClientConfigurationStore config) {
        databaseFile = db;
        configStore = config;
    }

    public synchronized void initialize() throws IOException, SQLException {
        if (services != null) return;
        LOG.info("Initializing MyAnimeWatchLedger...");
        database = new DatabaseManager(databaseFile);
        try {
            database.initialize();
            new DatabaseMigrationRunner(database).migrate();
            persistence = new PersistenceContext(database);
            background = new BackgroundTaskExecutor(2);

            LedgerService ledger = new LedgerService(persistence.ledger());
            CatalogueService catalogue = new CatalogueService(persistence.catalogue());
            CatalogueTabService tabs = new CatalogueTabService(
                    persistence.catalogue(), persistence.openCatalogueTabs());
            AnimeDetailTabService detailTabs = new AnimeDetailTabService(persistence.openAnimeDetailTabs());

            SwitchableMalApiTransport transport = new SwitchableMalApiTransport();
            MalApiConnection malConnection = new MalApiConnection(configStore, transport);
            try {
                malConnection.load();
            } catch (IllegalArgumentException invalid) {
                LOG.log(Level.WARNING,
                        "MAL configuration is invalid; online MAL features are unavailable.", invalid);
            }
            if (!malConnection.isConfigured()) {
                LOG.info("MAL Client ID is not configured; online MAL features are unavailable.");
            }

            MalResponseMapper mapper = new MalResponseMapper();
            CatalogueRefreshService refresh = new CatalogueRefreshService(
                    new MalApiCatalogueGateway(transport, MalApiConfiguration.DEFAULT_BASE_URI),
                    persistence.catalogue(), persistence.animeMetadata(), mapper,
                    persistence.ledger(), persistence.transactions());
            AnimeDetailService details = new AnimeDetailService(
                    persistence.animeMetadata(), new MalApiAnimeDetailGateway(transport, mapper), mapper);

            services = new ApplicationServices(
                    ledger, catalogue, tabs, refresh, details, detailTabs, background, malConnection);
            LOG.info("Application services initialized. MAL configured: " + services.isMalConfigured());
        } catch (IOException | SQLException | RuntimeException failure) {
            close();
            throw failure;
        }
    }

    public synchronized PersistenceContext persistence() {
        if (persistence == null) throw new IllegalStateException("Application has not been initialized.");
        return persistence;
    }

    public synchronized ApplicationServices services() {
        if (services == null) throw new IllegalStateException("Application has not been initialized.");
        return services;
    }

    @Override
    public synchronized void close() {
        services = null;
        persistence = null;
        try {
            if (background != null) {
                background.close();
                background = null;
            }
        } finally {
            if (database != null) {
                database.close();
                database = null;
            }
        }
        LOG.info("Application resources shut down.");
    }
}
