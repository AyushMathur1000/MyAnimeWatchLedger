package com.myanimewatchledger.infrastructure.persistence;

import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that committed ledger data survives closing and reopening the
 * SQLite database through independent application-style persistence contexts.
 */
class DatabaseRestartPersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void committedLedgerEntrySurvivesDatabaseCloseAndReinitialization() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("restart-persistence.db");
        Instant timestamp = Instant.ofEpochMilli(Instant.now().toEpochMilli());
        LedgerEntry expected = new LedgerEntry(
                9001,
                "Restart Persistence Test",
                LedgerStatus.WATCHING,
                timestamp,
                timestamp
        );

        DatabaseManager firstDatabase = new DatabaseManager(databaseFile);
        try {
            firstDatabase.initialize();
            new DatabaseMigrationRunner(firstDatabase).migrate();
            PersistenceContext firstContext = new PersistenceContext(firstDatabase);
            firstContext.ledger().add(expected);
        } finally {
            firstDatabase.close();
        }

        DatabaseManager reopenedDatabase = new DatabaseManager(databaseFile);
        try {
            reopenedDatabase.initialize();
            new DatabaseMigrationRunner(reopenedDatabase).migrate();
            PersistenceContext reopenedContext = new PersistenceContext(reopenedDatabase);

            LedgerEntry actual = reopenedContext.ledger()
                    .findByMalId(expected.malId())
                    .orElseThrow();

            assertEquals(expected, actual);
        } finally {
            reopenedDatabase.close();
        }
    }
}
