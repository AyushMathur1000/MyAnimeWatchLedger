package com.myanimewatchledger.infrastructure.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Applies immutable, ordered SQL migrations using PRAGMA user_version as the
 * sole schema-version authority.
 */
public final class DatabaseMigrationRunner {
    private static final Logger LOGGER =
            Logger.getLogger(DatabaseMigrationRunner.class.getName());
    private static final String MIGRATION_DIRECTORY = "/db/migration/";

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "V001__initial_schema.sql"),
            new Migration(2, "V002__add_watch_later_status.sql")
    );

    private final DatabaseManager databaseManager;
    private final List<Migration> migrations;

    public DatabaseMigrationRunner(DatabaseManager databaseManager) {
        this(databaseManager, MIGRATIONS);
    }

    // Package-private constructor permits isolated migration-failure tests.
    DatabaseMigrationRunner(DatabaseManager databaseManager, List<Migration> migrations) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.migrations = List.copyOf(migrations);
        validateMigrationSequence(this.migrations);
    }

    public void migrate() {
        try (Connection connection = databaseManager.getConnection()) {
            int currentVersion = readSchemaVersion(connection);
            int latestSupportedVersion = latestSupportedVersion();

            if (currentVersion < 0) {
                throw new DatabaseException(
                        "Database contains an invalid negative schema version: " + currentVersion
                );
            }
            if (currentVersion > latestSupportedVersion) {
                throw new DatabaseException(
                        "Database schema version " + currentVersion
                                + " is newer than the version supported by this application ("
                                + latestSupportedVersion + "). Refusing to downgrade."
                );
            }

            for (Migration migration : migrations) {
                if (migration.version() > currentVersion) {
                    applyMigration(connection, migration);
                }
            }

            validateDatabase(connection);
            LOGGER.info(() -> "Database schema is ready at version " + latestSupportedVersion);
        } catch (SQLException exception) {
            throw new DatabaseException("Database migration or validation failed.", exception);
        }
    }

    private static int readSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA user_version")) {
            if (!result.next()) {
                throw new SQLException("SQLite did not return PRAGMA user_version.");
            }
            return result.getInt(1);
        }
    }

    private static void applyMigration(Connection connection, Migration migration) {
        try {
            connection.setAutoCommit(false);
            try {
                String script = loadMigration(migration.resourceName());
                executeScript(connection, script);

                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA user_version = " + migration.version());
                }

                connection.commit();
                LOGGER.info(() -> "Applied database migration V" + migration.version());
            } catch (Exception failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw new DatabaseException(
                    "Failed to apply migration " + migration.resourceName()
                            + "; the migration was rolled back where possible.",
                    exception
            );
        }
    }

    private static String loadMigration(String resourceName) {
        String path = MIGRATION_DIRECTORY + resourceName;
        try (InputStream input = DatabaseMigrationRunner.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new DatabaseException("Migration resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder script = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    script.append(line).append('\n');
                }
                return script.toString();
            }
        } catch (IOException exception) {
            throw new DatabaseException("Unable to read migration resource: " + path, exception);
        }
    }

    /**
     * Executes trusted, version-controlled SQL resources. Semicolons inside
     * quoted strings and SQL comments do not split statements.
     */
    private static void executeScript(Connection connection, String script) throws SQLException {
        for (String sql : splitStatements(script)) {
            if (!sql.isBlank()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean backtickQuoted = false;
        boolean bracketQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            current.append(c);

            if (lineComment) {
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    current.append(next);
                    i++;
                    blockComment = false;
                }
                continue;
            }
            if (!singleQuoted && !doubleQuoted && !backtickQuoted && !bracketQuoted) {
                if (c == '-' && next == '-') {
                    current.append(next);
                    i++;
                    lineComment = true;
                } else if (c == '/' && next == '*') {
                    current.append(next);
                    i++;
                    blockComment = true;
                } else if (c == ';') {
                    statements.add(current.substring(0, current.length() - 1).trim());
                    current.setLength(0);
                } else if (c == '\'') {
                    singleQuoted = true;
                } else if (c == '"') {
                    doubleQuoted = true;
                } else if (c == '`') {
                    backtickQuoted = true;
                } else if (c == '[') {
                    bracketQuoted = true;
                }
            } else if (singleQuoted && c == '\'') {
                if (next == '\'') {
                    current.append(next);
                    i++;
                } else {
                    singleQuoted = false;
                }
            } else if (doubleQuoted && c == '"') {
                if (next == '"') {
                    current.append(next);
                    i++;
                } else {
                    doubleQuoted = false;
                }
            } else if (backtickQuoted && c == '`') {
                backtickQuoted = false;
            } else if (bracketQuoted && c == ']') {
                bracketQuoted = false;
            }
        }

        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }

    private static void validateDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                throw new SQLException("SQLite integrity_check did not return 'ok'.");
            }
        }

        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new SQLException(
                        "SQLite foreign_key_check found a violation in table "
                                + result.getString(1)
                );
            }
        }
    }

    private int latestSupportedVersion() {
        return migrations.isEmpty() ? 0 : migrations.getLast().version();
    }

    private static void validateMigrationSequence(List<Migration> migrations) {
        int previous = 0;
        for (Migration migration : migrations) {
            if (migration.version() <= previous) {
                throw new IllegalStateException(
                        "Migration versions must be strictly increasing."
                );
            }
            previous = migration.version();
        }
    }

    record Migration(int version, String resourceName) {
        Migration {
            if (version < 1) {
                throw new IllegalArgumentException("Migration version must be positive.");
            }
            Objects.requireNonNull(resourceName, "resourceName");
        }
    }
}
