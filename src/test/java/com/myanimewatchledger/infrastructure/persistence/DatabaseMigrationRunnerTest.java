package com.myanimewatchledger.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appliesInitialSchemaAndSetsUserVersion() throws Exception {
        DatabaseManager manager = initializedManager();

        try (manager) {
            new DatabaseMigrationRunner(manager).migrate();

            try (Connection connection = manager.getConnection();
                 Statement statement = connection.createStatement()) {
                assertEquals(1, pragmaInt(statement, "PRAGMA user_version"));

                Set<String> expectedTables = Set.of(
                        "ledger_entry",
                        "catalogue_search",
                        "anime_metadata_cache",
                        "catalogue_search_anime",
                        "open_catalogue_tab"
                );
                Set<String> actualTables = new java.util.HashSet<>();

                try (ResultSet result = statement.executeQuery("""
                        SELECT name
                        FROM sqlite_master
                        WHERE type = 'table'
                        """)) {
                    while (result.next()) {
                        actualTables.add(result.getString("name"));
                    }
                }

                assertTrue(actualTables.containsAll(expectedTables));
                assertEquals(0, countRows(statement, "PRAGMA foreign_key_check"));
            }
        }
    }

    @Test
    void doesNotReapplyAnAlreadyAppliedMigration() throws Exception {
        DatabaseManager manager = initializedManager();

        try (manager) {
            DatabaseMigrationRunner runner = new DatabaseMigrationRunner(manager);
            runner.migrate();
            runner.migrate();

            try (Connection connection = manager.getConnection();
                 Statement statement = connection.createStatement()) {
                assertEquals(1, pragmaInt(statement, "PRAGMA user_version"));
                assertEquals(1, countRows(statement,
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='ledger_entry'"));
            }
        }
    }

    @Test
    void refusesDatabaseCreatedByANewerApplicationVersion() throws Exception {
        DatabaseManager manager = initializedManager();

        try (manager;
             Connection connection = manager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");

            assertThrows(DatabaseException.class,
                    () -> new DatabaseMigrationRunner(manager).migrate());
        }
    }

    private DatabaseManager initializedManager() throws Exception {
        DatabaseManager manager =
                new DatabaseManager(temporaryDirectory.resolve("ledger.db"));
        manager.initialize();
        return manager;
    }

    private static int pragmaInt(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static int countRows(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
