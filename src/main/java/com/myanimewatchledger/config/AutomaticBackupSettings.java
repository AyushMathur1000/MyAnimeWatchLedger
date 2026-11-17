package com.myanimewatchledger.config;

import java.nio.file.Path;
import java.util.Objects;

/** Validated automatic-backup preferences. */
public record AutomaticBackupSettings(boolean enabled, BackupFrequency frequency, int retentionCount, Path destination) {
    public AutomaticBackupSettings {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(destination, "destination");
        if (retentionCount < 1 || retentionCount > 1000) {
            throw new IllegalArgumentException("Retention count must be between 1 and 1000.");
        }
        destination = destination.toAbsolutePath().normalize();
    }
}
