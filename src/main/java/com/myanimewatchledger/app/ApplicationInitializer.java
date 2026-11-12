package com.myanimewatchledger.app;

import com.myanimewatchledger.config.ApplicationPaths;
import com.myanimewatchledger.infrastructure.persistence.DatabaseManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

/** Coordinates startup resources before the primary JavaFX stage is shown. */
public final class ApplicationInitializer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ApplicationInitializer.class.getName());
    private DatabaseManager databaseManager;

    public void initialize() throws IOException, SQLException {
        LOGGER.info("Initializing MyAnimeWatchLedger...");
        databaseManager = new DatabaseManager(ApplicationPaths.databaseFile());
        databaseManager.initialize();
        LOGGER.info("Application foundation initialized.");
    }

    @Override
    public void close() {
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }
        LOGGER.info("Application resources shut down.");
    }
}
