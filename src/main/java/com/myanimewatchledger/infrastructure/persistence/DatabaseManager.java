package com.myanimewatchledger.infrastructure.persistence;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns SQLite initialization and configured JDBC connections. Open connections are leased. */
public final class DatabaseManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;
    private final Path databasePath;
    private boolean initialized;
    private boolean closing;
    private int activeConnections;

    public DatabaseManager(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    public synchronized void initialize() throws SQLException, IOException {
        if (initialized) return;
        Path parent = databasePath.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (Connection connection = openUnconfiguredConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            configureConnection(connection);
            verifyForeignKeysEnabled(connection);
        } catch (SQLException exception) {
            throw new SQLException("Unable to initialize SQLite database at " + databasePath, exception);
        }
        initialized = true;
        closing = false;
        LOGGER.info(() -> "SQLite database initialized at " + databasePath);
    }

    public synchronized Connection getConnection() throws SQLException {
        if (!initialized || closing) throw new SQLException("Database is not accepting connections.");
        Connection connection = openUnconfiguredConnection();
        try {
            configureConnection(connection);
            verifyForeignKeysEnabled(connection);
            activeConnections++;
            return leasedConnection(connection);
        } catch (SQLException exception) {
            try { connection.close(); } catch (SQLException closeFailure) { exception.addSuppressed(closeFailure); }
            throw new SQLException("Unable to configure SQLite connection.", exception);
        }
    }

    private Connection leasedConnection(Connection delegate) {
        AtomicBoolean released = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close") && method.getParameterCount() == 0) {
                        if (released.compareAndSet(false, true)) {
                            synchronized (DatabaseManager.this) {
                                try { delegate.close(); }
                                finally {
                                    activeConnections--;
                                    DatabaseManager.this.notifyAll();
                                }
                            }
                        }
                        return null;
                    }
                    try { return method.invoke(delegate, args); }
                    catch (InvocationTargetException exception) { throw exception.getCause(); }
                });
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
        try (Statement statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA foreign_keys")) {
            if (!result.next() || result.getInt(1) != 1) throw new SQLException("SQLite foreign-key enforcement is not enabled.");
        }
    }

    public Path getDatabasePath() { return databasePath; }

    /** Replace the active database with a validated staging file, retaining rollback until reopen checks pass. */
    public synchronized void replaceDatabase(Path stagedDatabase) throws SQLException, IOException {
        Path staged = Objects.requireNonNull(stagedDatabase).toAbsolutePath().normalize();
        if (staged.equals(databasePath)) throw new IllegalArgumentException("Staging database must differ from the active database.");
        if (!Files.isRegularFile(staged)) throw new IOException("Staging database does not exist.");
        close();
        Files.deleteIfExists(databasePath.resolveSibling(databasePath.getFileName() + "-wal"));
        Files.deleteIfExists(databasePath.resolveSibling(databasePath.getFileName() + "-shm"));
        Path rollback = databasePath.resolveSibling(databasePath.getFileName() + ".rollback-" + UUID.randomUUID());
        boolean oldMoved = false;
        try {
            move(databasePath, rollback);
            oldMoved = true;
            move(staged, databasePath);
            initialize();
            verifyActiveDatabase();
        } catch (Exception failure) {
            try {
                if (initialized) close();
                Files.deleteIfExists(databasePath);
                if (oldMoved && Files.exists(rollback)) move(rollback, databasePath);
                initialize();
                verifyActiveDatabase();
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                throw new SQLException("Replacement failed and rollback could not reopen the previous database. Preserve " + rollback, failure);
            }
            if (failure instanceof SQLException sql) throw sql;
            if (failure instanceof IOException io) throw io;
            throw new SQLException("Replacement failed; the previous database was restored.", failure);
        }
        try {
            Files.deleteIfExists(rollback);
        } catch (IOException cleanupFailure) {
            LOGGER.log(Level.WARNING, "Database restored, but the rollback file could not be deleted: " + rollback, cleanupFailure);
        } finally {
            Files.deleteIfExists(staged.resolveSibling(staged.getFileName() + "-wal"));
            Files.deleteIfExists(staged.resolveSibling(staged.getFileName() + "-shm"));
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("Atomic replacement is unsupported by this filesystem; active data was not overwritten.", unsupported);
        }
    }

    private void verifyActiveDatabase() throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement();
             var integrity = statement.executeQuery("PRAGMA integrity_check")) {
            if (!integrity.next() || !"ok".equalsIgnoreCase(integrity.getString(1)))
                throw new SQLException("Replacement database failed SQLite integrity_check.");
        }
        try (Connection connection = getConnection(); Statement statement = connection.createStatement();
             var foreignKeys = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (foreignKeys.next()) throw new SQLException("Replacement database contains foreign-key violations.");
        }
    }

    @Override
    public synchronized void close() {
        if (!initialized) return;
        closing = true;
        boolean interrupted = false;
        try {
            while (activeConnections > 0) {
                try { wait(TimeUnit.SECONDS.toMillis(1)); }
                catch (InterruptedException exception) { interrupted = true; }
            }
            try (Connection connection = openUnconfiguredConnection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException exception) {
                LOGGER.log(Level.WARNING, "Unable to checkpoint SQLite WAL during shutdown.", exception);
            }
        } finally {
            initialized = false;
            closing = false;
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
