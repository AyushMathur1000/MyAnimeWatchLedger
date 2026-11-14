package com.myanimewatchledger.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBackupServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void backupIsStandaloneAndRestoreReplacesDatabaseContents() throws Exception {
        Path activeFile = temporaryDirectory.resolve("active.db");
        Path backupFile = temporaryDirectory.resolve("backup.db");
        try (DatabaseManager manager = new DatabaseManager(activeFile)) {
            manager.initialize();
            new DatabaseMigrationRunner(manager).migrate();
            insertLedgerEntry(manager, "Original title");

            DatabaseBackupService backups = new DatabaseBackupService(manager);
            assertEquals(backupFile, backups.createBackup(backupFile));
            assertTrue(Files.isRegularFile(backupFile));

            updateLedgerTitle(manager, "Changed title");
            backups.restoreBackup(backupFile);

            assertEquals("Original title", readLedgerTitle(manager));
        }
    }

    @Test
    void rejectsCorruptBackupWithoutChangingActiveDatabase() throws Exception {
        Path activeFile = temporaryDirectory.resolve("active.db");
        Path corruptFile = temporaryDirectory.resolve("corrupt.db");
        try (DatabaseManager manager = new DatabaseManager(activeFile)) {
            manager.initialize();
            new DatabaseMigrationRunner(manager).migrate();
            insertLedgerEntry(manager, "Keep this title");
            Files.writeString(corruptFile, "not a sqlite database");

            DatabaseBackupService backups = new DatabaseBackupService(manager);
            assertThrows(Exception.class, () -> backups.restoreBackup(corruptFile));
            assertEquals("Keep this title", readLedgerTitle(manager));
        }
    }

    @Test
    void rejectsBackupWithNewerSchemaWithoutChangingActiveDatabase() throws Exception {
        Path activeFile = temporaryDirectory.resolve("active-newer.db");
        Path backupFile = temporaryDirectory.resolve("newer-schema.db");
        try (DatabaseManager manager = initializedManager(activeFile, "Keep active data")) {
            DatabaseBackupService backups = new DatabaseBackupService(manager);
            backups.createBackup(backupFile);
            executeSql(backupFile, "PRAGMA user_version = " + (DatabaseBackupService.LATEST_SUPPORTED_SCHEMA_VERSION + 1));

            assertThrows(java.sql.SQLException.class, () -> backups.restoreBackup(backupFile));
            assertEquals("Keep active data", readLedgerTitle(manager));
        }
    }

    @Test
    void rejectsBackupMissingRequiredTableWithoutChangingActiveDatabase() throws Exception {
        Path activeFile = temporaryDirectory.resolve("active-missing-table.db");
        Path backupFile = temporaryDirectory.resolve("missing-table.db");
        try (DatabaseManager manager = initializedManager(activeFile, "Keep active data")) {
            DatabaseBackupService backups = new DatabaseBackupService(manager);
            backups.createBackup(backupFile);
            executeSql(backupFile, "DROP TABLE open_catalogue_tab");

            assertThrows(java.sql.SQLException.class, () -> backups.restoreBackup(backupFile));
            assertEquals("Keep active data", readLedgerTitle(manager));
        }
    }

    @Test
    void rejectsBackupMissingRequiredColumnWithoutChangingActiveDatabase() throws Exception {
        Path activeFile = temporaryDirectory.resolve("active-missing-column.db");
        Path backupFile = temporaryDirectory.resolve("missing-column.db");
        try (DatabaseManager manager = initializedManager(activeFile, "Keep active data")) {
            DatabaseBackupService backups = new DatabaseBackupService(manager);
            backups.createBackup(backupFile);
            executeSql(backupFile, """
                    DROP TABLE open_catalogue_tab;
                    CREATE TABLE open_catalogue_tab (
                        search_id INTEGER PRIMARY KEY,
                        tab_order INTEGER NOT NULL
                    )
                    """);

            assertThrows(java.sql.SQLException.class, () -> backups.restoreBackup(backupFile));
            assertEquals("Keep active data", readLedgerTitle(manager));
        }
    }

    @Test
    void rejectsForeignKeyInvalidBackupWithoutChangingActiveDatabase() throws Exception {
        Path activeFile = temporaryDirectory.resolve("active-invalid-fk.db");
        Path backupFile = temporaryDirectory.resolve("invalid-fk.db");
        try (DatabaseManager manager = initializedManager(activeFile, "Keep active data")) {
            DatabaseBackupService backups = new DatabaseBackupService(manager);
            backups.createBackup(backupFile);
            executeSql(backupFile, """
                    INSERT INTO catalogue_search_anime(search_id, mal_id, display_order)
                    VALUES (999999, 999999, 0)
                    """);

            assertThrows(java.sql.SQLException.class, () -> backups.restoreBackup(backupFile));
            assertEquals("Keep active data", readLedgerTitle(manager));
        }
    }

    @Test
    void refusesToOverwriteExistingBackup() throws Exception {
        Path activeFile = temporaryDirectory.resolve("active.db");
        Path backupFile = temporaryDirectory.resolve("backup.db");
        try (DatabaseManager manager = new DatabaseManager(activeFile)) {
            manager.initialize();
            new DatabaseMigrationRunner(manager).migrate();
            Files.writeString(backupFile, "preserve");
            DatabaseBackupService backups = new DatabaseBackupService(manager);

            assertThrows(java.io.IOException.class, () -> backups.createBackup(backupFile));
            assertEquals("preserve", Files.readString(backupFile));
        }
    }

    private DatabaseManager initializedManager(Path activeFile, String title) throws Exception {
        DatabaseManager manager = new DatabaseManager(activeFile);
        manager.initialize();
        new DatabaseMigrationRunner(manager).migrate();
        insertLedgerEntry(manager, title);
        return manager;
    }

    private static void executeSql(Path file, String sql) throws Exception {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.executeUpdate(command);
            }
        }
    }

    private static void insertLedgerEntry(DatabaseManager manager, String title) throws Exception {
        try (Connection connection = manager.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO ledger_entry(mal_id,title,status,date_added,last_modified) "
                    + "VALUES(1,'" + title + "','WATCHING',1,1)");
        }
    }

    private static void updateLedgerTitle(DatabaseManager manager, String title) throws Exception {
        try (Connection connection = manager.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE ledger_entry SET title='" + title + "' WHERE mal_id=1");
        }
    }

    private static String readLedgerTitle(DatabaseManager manager) throws Exception {
        try (Connection connection = manager.getConnection(); Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT title FROM ledger_entry WHERE mal_id=1")) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
