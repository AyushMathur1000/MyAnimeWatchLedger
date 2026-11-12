package com.myanimewatchledger.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesSqliteDatabaseAndEnablesForeignKeys() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("isolated-test.db");
        try (DatabaseManager manager = new DatabaseManager(databaseFile)) {
            manager.initialize();
            try (Statement statement = manager.connection().createStatement();
                 ResultSet resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }
        }
        assertTrue(java.nio.file.Files.exists(databaseFile));
    }
}
