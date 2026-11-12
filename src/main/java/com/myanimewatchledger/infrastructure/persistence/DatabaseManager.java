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

/** Owns the initial SQLite connection lifecycle and connection-level pragmas. */
public final class DatabaseManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private final Path databasePath;
    private Connection connection;

    public DatabaseManager(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath();
    }

    public synchronized void initialize() throws SQLException, IOException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        Path parent = databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        } catch (SQLException exception) {
            close();
            throw exception;
        }
        LOGGER.info(() -> "SQLite database initialized at " + databasePath);
    }

    /** Returns a connection for bootstrap and smoke-test use; repositories will receive managed connections later. */
    public synchronized Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database has not been initialized.");
        }
        return connection;
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
                LOGGER.info("SQLite database connection closed.");
            }
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Unable to close SQLite connection cleanly.", exception);
        } finally {
            connection = null;
        }
    }
}
