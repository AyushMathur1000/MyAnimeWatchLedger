package com.myanimewatchledger.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesDatabaseAndCreatesParentDirectory() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("nested").resolve("ledger.db");

        try (DatabaseManager manager = new DatabaseManager(databaseFile)) {
            manager.initialize();

            assertTrue(Files.exists(databaseFile));
            assertEquals(databaseFile.toAbsolutePath().normalize(), manager.getDatabasePath());
        }
    }

    @Test
    void opensIndependentConnectionsWithForeignKeysEnabled() throws Exception {
        try (DatabaseManager manager =
                     new DatabaseManager(temporaryDirectory.resolve("ledger.db"))) {
            manager.initialize();

            try (Connection first = manager.getConnection();
                 Connection second = manager.getConnection()) {
                assertNotSame(first, second);
                assertEquals(1, pragmaInt(first, "PRAGMA foreign_keys"));
                assertEquals(1, pragmaInt(second, "PRAGMA foreign_keys"));
                assertEquals(5000, pragmaInt(first, "PRAGMA busy_timeout"));
            }
        }
    }

    private static int pragmaInt(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
