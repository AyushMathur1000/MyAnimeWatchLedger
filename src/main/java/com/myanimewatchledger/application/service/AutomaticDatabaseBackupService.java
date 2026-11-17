package com.myanimewatchledger.application.service;

import com.myanimewatchledger.config.ApplicationPaths;
import com.myanimewatchledger.config.AutomaticBackupSettings;
import com.myanimewatchledger.config.BackupFrequency;
import com.myanimewatchledger.infrastructure.persistence.DatabaseBackupService;
import com.myanimewatchledger.presentation.state.WindowStateStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Coordinates due checks, validated automatic backups, and automatic-only retention. */
public final class AutomaticDatabaseBackupService {
    private static final String PREFIX = "my_anime_watch_ledger_auto_";
    private static final Pattern AUTOMATIC_NAME =
            Pattern.compile("my_anime_watch_ledger_auto_\\d{8}_\\d{6}_\\d{3}\\.db");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private final DatabaseBackupService backups;
    private final WindowStateStore preferences;
    private final Clock clock;

    public AutomaticDatabaseBackupService(DatabaseBackupService backups, WindowStateStore preferences) {
        this(backups, preferences, Clock.systemDefaultZone());
    }

    AutomaticDatabaseBackupService(DatabaseBackupService backups, WindowStateStore preferences, Clock clock) {
        this.backups = Objects.requireNonNull(backups);
        this.preferences = Objects.requireNonNull(preferences);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Returns null when disabled or not due; otherwise returns the validated backup path. */
    public Path runIfDue() throws IOException, java.sql.SQLException {
        AutomaticBackupSettings settings = preferences.loadAutomaticBackupSettings();
        if (!settings.enabled() || !isDue(settings.frequency(), preferences.loadLastAutomaticBackup())) return null;
        Files.createDirectories(settings.destination());
        String stamp = ZonedDateTime.now(clock).format(FILE_TIME);
        Path target = settings.destination().resolve(PREFIX + stamp + ".db");
        Path created = backups.createBackup(target);
        // Timestamp is recorded only after the backup has been fully validated.
        preferences.saveLastAutomaticBackup(clock.instant());
        applyRetention(settings.destination(), settings.retentionCount());
        return created;
    }

    static boolean isDue(BackupFrequency frequency, Instant lastSuccessful, Clock clock) {
        if (lastSuccessful == null) return true;
        ZonedDateTime last = lastSuccessful.atZone(clock.getZone());
        ZonedDateTime now = ZonedDateTime.now(clock);
        return switch (frequency) {
            case DAILY -> !now.isBefore(last.plusDays(1));
            case WEEKLY -> !now.isBefore(last.plusWeeks(1));
            case MONTHLY -> !now.isBefore(last.plusMonths(1));
        };
    }

    private boolean isDue(BackupFrequency frequency, Instant lastSuccessful) {
        return isDue(frequency, lastSuccessful, clock);
    }

    private static void applyRetention(Path directory, int keep) throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.list(directory)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> AUTOMATIC_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (int i = keep; i < files.size(); i++) Files.deleteIfExists(files.get(i));
    }
}
