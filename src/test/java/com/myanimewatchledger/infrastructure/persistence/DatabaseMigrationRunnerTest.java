package com.myanimewatchledger.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
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
                assertEquals(2, pragmaInt(statement, "PRAGMA user_version"));

                Set<String> expectedTables = Set.of(
                        "ledger_entry",
                        "catalogue_search",
                        "anime_metadata_cache",
                        "catalogue_search_anime",
                        "open_catalogue_tab"
                );
                Set<String> actualTables = new java.util.HashSet<>();

                try (ResultSet result = statement.executeQuery("""
                        SELECT name FROM sqlite_master WHERE type = 'table'
                        """)) {
                    while (result.next()) {
                        actualTables.add(result.getString("name"));
                    }
                }

                assertTrue(actualTables.containsAll(expectedTables));
                assertEquals(0, rowCount(statement, "PRAGMA foreign_key_check"));
            }
        }
    }

    @Test
    void upgradesVersionOneDatabaseAndPreservesExistingLedgerRows() throws Exception {
        DatabaseManager manager = initializedManager();

        try (manager) {
            new DatabaseMigrationRunner(manager, List.of(
                    new DatabaseMigrationRunner.Migration(1, "V001__initial_schema.sql")
            )).migrate();

            try (Connection connection = manager.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO ledger_entry
                            (mal_id, title, status, date_added, last_modified)
                        VALUES (8123, 'Existing Anime', 'HOLD', 111, 222)
                        """);
            }

            new DatabaseMigrationRunner(manager).migrate();

            try (Connection connection = manager.getConnection();
                 Statement statement = connection.createStatement()) {
                assertEquals(2, pragmaInt(statement, "PRAGMA user_version"));
                try (ResultSet result = statement.executeQuery("""
                        SELECT mal_id, title, status, date_added, last_modified
                        FROM ledger_entry WHERE mal_id = 8123
                        """)) {
                    assertTrue(result.next());
                    assertEquals(8123, result.getLong("mal_id"));
                    assertEquals("Existing Anime", result.getString("title"));
                    assertEquals("HOLD", result.getString("status"));
                    assertEquals(111, result.getLong("date_added"));
                    assertEquals(222, result.getLong("last_modified"));
                }

                statement.executeUpdate("""
                        INSERT INTO ledger_entry
                            (mal_id, title, status, date_added, last_modified)
                        VALUES (8124, 'Watch Later Anime', 'WATCH_LATER', 333, 444)
                        """);
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM ledger_entry WHERE status = 'WATCH_LATER'"));
                assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("""
                        INSERT INTO ledger_entry
                            (mal_id, title, status, date_added, last_modified)
                        VALUES (8125, 'Invalid Status', 'PLANNED', 555, 666)
                        """));
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
                assertEquals(2, pragmaInt(statement, "PRAGMA user_version"));
                assertEquals(1, scalarInt(statement,
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

    @Test
    void rollsBackSchemaChangesAndVersionWhenMigrationFails() throws Exception {
        DatabaseManager manager = initializedManager();

        try (manager) {
            DatabaseMigrationRunner runner = new DatabaseMigrationRunner(
                    manager,
                    List.of(new DatabaseMigrationRunner.Migration(
                            1, "V999__failing_test_migration.sql"))
            );

            assertThrows(DatabaseException.class, runner::migrate);

            try (Connection connection = manager.getConnection();
                 Statement statement = connection.createStatement()) {
                assertEquals(0, pragmaInt(statement, "PRAGMA user_version"));
                assertEquals(0, scalarInt(statement,
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='should_rollback'"));
            }
        }
    }

    @Test
    void cacheTablesCanBeClearedWithoutAffectingLedger() throws Exception {
        DatabaseManager manager = initializedManager();

        try (manager) {
            new DatabaseMigrationRunner(manager).migrate();

            try (Connection connection = manager.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO ledger_entry
                            (mal_id, title, status, date_added, last_modified)
                        VALUES (101, 'Test Anime', 'WATCHING', 1, 1)
                        """);
                statement.executeUpdate("""
                        INSERT INTO catalogue_search (search_id, season, year, fetch_status)
                        VALUES (1, 'WINTER', 2026, 'SUCCESS')
                        """);
                statement.executeUpdate("""
                        INSERT INTO anime_metadata_cache
                            (mal_id, preferred_title, metadata_json, searchable_text, cached_at)
                        VALUES (101, 'Test Anime', '{}', 'test anime', 1)
                        """);
                statement.executeUpdate("""
                        INSERT INTO catalogue_search_anime (search_id, mal_id, display_order)
                        VALUES (1, 101, 0)
                        """);
                statement.executeUpdate("""
                        INSERT INTO open_catalogue_tab (search_id, tab_order, search_query)
                        VALUES (1, 0, '')
                        """);

                statement.executeUpdate("DELETE FROM open_catalogue_tab");
                statement.executeUpdate("DELETE FROM catalogue_search_anime");
                statement.executeUpdate("DELETE FROM catalogue_search");
                statement.executeUpdate("DELETE FROM anime_metadata_cache");

                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM ledger_entry WHERE mal_id = 101"));
            }
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

    private static int scalarInt(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static int rowCount(Statement statement, String sql) throws Exception {
        int count = 0;
        try (ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                count++;
            }
        }
        return count;
    }
}
