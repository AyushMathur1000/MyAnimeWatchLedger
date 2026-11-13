package com.myanimewatchledger.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns SQLite database initialization and creation of configured JDBC connections.
 * Connections are short-lived and owned by the caller.
 */
public final class DatabaseManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;

    private final Path databasePath;
    private volatile boolean initialized;

    public DatabaseManager(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Creates the database directory/file and configures persistent journal mode.
     * Must complete before migrations or repositories are used.
     */
    public synchronized void initialize() throws SQLException, IOException {
        if (initialized) {
            return;
        }

        Path parent = databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (Connection connection = openUnconfiguredConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            configureConnection(connection);
            verifyForeignKeysEnabled(connection);
        } catch (SQLException exception) {
            throw new SQLException(
                    "Unable to initialize SQLite database at " + databasePath,
                    exception
            );
        }

        initialized = true;
        LOGGER.info(() -> "SQLite database initialized at " + databasePath);
    }

    /**
     * Opens a new configured connection. The caller must close it.
     */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            throw new SQLException("Database has not been initialized.");
        }

        Connection connection = openUnconfiguredConnection();
        try {
            configureConnection(connection);
            verifyForeignKeysEnabled(connection);
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw new SQLException("Unable to configure SQLite connection.", exception);
        }
    }

    private Connection openUnconfiguredConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private static void configureConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MILLIS);
            statement.execute("PRAGMA synchronous = NORMAL");
        }
    }

    private static void verifyForeignKeysEnabled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA foreign_keys")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new SQLException("SQLite foreign-key enforcement is not enabled.");
            }
        }
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    /**
     * Connections are not retained by this manager. Checkpoint the WAL on orderly shutdown.
     */
    @Override
    public synchronized void close() {
        if (!initialized) {
            return;
        }

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Unable to checkpoint SQLite WAL during shutdown.", exception);
        } finally {
            initialized = false;
        }
    }
}
