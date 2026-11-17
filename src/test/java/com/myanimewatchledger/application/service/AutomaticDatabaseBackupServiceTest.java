package com.myanimewatchledger.application.service;

import com.myanimewatchledger.config.AutomaticBackupSettings;
import com.myanimewatchledger.config.BackupFrequency;
import com.myanimewatchledger.infrastructure.persistence.DatabaseBackupService;
import com.myanimewatchledger.infrastructure.persistence.DatabaseManager;
import com.myanimewatchledger.infrastructure.persistence.DatabaseMigrationRunner;
import com.myanimewatchledger.presentation.state.WindowStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AutomaticDatabaseBackupServiceTest {
    @TempDir Path temp;

    @Test
    void createsFirstBackupAndKeepsOnlyConfiguredAutomaticBackups() throws Exception {
        Path databaseFile = temp.resolve("active.db");
        Path destination = temp.resolve("backups");
        Path preferencesFile = temp.resolve("ui.properties");
        try (DatabaseManager database = initializedDatabase(databaseFile)) {
            WindowStateStore preferences = new WindowStateStore(preferencesFile);
            preferences.saveAutomaticBackupSettings(
                    new AutomaticBackupSettings(true, BackupFrequency.WEEKLY, 2, destination));
            Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
            AutomaticDatabaseBackupService service = new AutomaticDatabaseBackupService(
                    new DatabaseBackupService(database), preferences, clock);

            Path first = service.runIfDue();
            assertNotNull(first);
            assertTrue(Files.isRegularFile(first));
            assertEquals(clock.instant(), preferences.loadLastAutomaticBackup());

            Files.move(first, destination.resolve("my_anime_watch_ledger_auto_20261110_100000_000.db"));
            Files.createFile(destination.resolve("my_anime_watch_ledger_auto_20261101_100000_000.db"));
            Files.createFile(destination.resolve("my_anime_watch_ledger_auto_20261108_100000_000.db"));
            Files.createFile(destination.resolve("manual_backup.db"));

            // Force the schedule due without changing the configured destination.
            preferences.saveLastAutomaticBackup(Instant.parse("2026-11-01T00:00:00Z"));
            Path second = service.runIfDue();

            assertNotNull(second);
            try (var files = Files.list(destination)) {
                assertEquals(3, files.count()); // two automatic + manual
            }
            assertTrue(Files.exists(destination.resolve("manual_backup.db")));
        }
    }

    @Test
    void disabledOrNotDueBackupDoesNothing() throws Exception {
        try (DatabaseManager database = initializedDatabase(temp.resolve("active.db"))) {
            WindowStateStore preferences = new WindowStateStore(temp.resolve("ui.properties"));
            Path destination = temp.resolve("backups");
            preferences.saveAutomaticBackupSettings(
                    new AutomaticBackupSettings(false, BackupFrequency.WEEKLY, 2, destination));
            AutomaticDatabaseBackupService service = new AutomaticDatabaseBackupService(
                    new DatabaseBackupService(database), preferences,
                    Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC));
            assertNull(service.runIfDue());
            assertFalse(Files.exists(destination));
        }
    }

    @Test
    void frequencyUsesCalendarMonthAndElapsedDayOrWeek() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);
        assertTrue(AutomaticDatabaseBackupService.isDue(
                BackupFrequency.MONTHLY, Instant.parse("2026-01-31T00:00:00Z"), clock));
        assertFalse(AutomaticDatabaseBackupService.isDue(
                BackupFrequency.WEEKLY, Instant.parse("2026-02-23T00:00:01Z"), clock));
        assertTrue(AutomaticDatabaseBackupService.isDue(
                BackupFrequency.DAILY, Instant.parse("2026-02-28T00:00:00Z"), clock));
    }

    private DatabaseManager initializedDatabase(Path file) throws Exception {
        DatabaseManager manager = new DatabaseManager(file);
        manager.initialize();
        new DatabaseMigrationRunner(manager).migrate();
        return manager;
    }
}
