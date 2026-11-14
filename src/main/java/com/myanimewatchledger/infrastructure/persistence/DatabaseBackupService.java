package com.myanimewatchledger.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Creates and restores validated, standalone SQLite database backups. */
public final class DatabaseBackupService {
    public static final int LATEST_SUPPORTED_SCHEMA_VERSION = DatabaseMigrationRunner.latestSupportedSchemaVersion();
    private static final List<TableRequirement> REQUIRED_TABLES = List.of(
            new TableRequirement("ledger_entry", List.of("mal_id", "title", "status", "date_added", "last_modified")),
            new TableRequirement("catalogue_search", List.of("search_id", "season", "year", "fetch_status")),
            new TableRequirement("anime_metadata_cache", List.of("mal_id", "preferred_title", "metadata_json", "searchable_text", "cached_at")),
            new TableRequirement("catalogue_search_anime", List.of("search_id", "mal_id", "display_order")),
            new TableRequirement("open_catalogue_tab", List.of("search_id", "tab_order", "search_query"))
    );

    private final DatabaseManager databaseManager;

    public DatabaseBackupService(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    /** Writes a consistent snapshot without overwriting an existing file. */
    public Path createBackup(Path destination) throws SQLException, IOException {
        Path target = normalize(destination);
        if (target.equals(databaseManager.getDatabasePath())) throw new IOException("Backup destination cannot be the active database.");
        if (Files.exists(target)) throw new IOException("A file already exists at the selected backup destination.");
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Backup destination must have a parent directory.");
        Files.createDirectories(parent);
        String sql = "VACUUM INTO ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, target.toString());
            statement.execute();
        } catch (SQLException | RuntimeException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
        try {
            validateDatabase(target, true);
            return target;
        } catch (SQLException | IOException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }
    }

    /**
     * Validates the selected backup, migrates an isolated staging copy if needed,
     * then delegates atomic replacement and rollback protection to DatabaseManager.
     */
    public void restoreBackup(Path source) throws SQLException, IOException {
        Path backup = normalize(source);
        if (backup.equals(databaseManager.getDatabasePath())) throw new IOException("The active database cannot be used as a restore source.");
        if (!Files.isRegularFile(backup) || !Files.isReadable(backup)) throw new IOException("Selected backup is not a readable file.");
        validateDatabase(backup, true);

        Path active = databaseManager.getDatabasePath();
        Path staging = active.resolveSibling(active.getFileName() + ".restore-" + UUID.randomUUID());
        try {
            Files.copy(backup, staging, StandardCopyOption.COPY_ATTRIBUTES);
            DatabaseManager stagedManager = new DatabaseManager(staging);
            try {
                stagedManager.initialize();
                new DatabaseMigrationRunner(stagedManager).migrate();
            } finally {
                stagedManager.close();
            }
            validateDatabase(staging, true);
            databaseManager.replaceDatabase(staging);
        } finally {
            Files.deleteIfExists(staging);
            Files.deleteIfExists(staging.resolveSibling(staging.getFileName() + "-wal"));
            Files.deleteIfExists(staging.resolveSibling(staging.getFileName() + "-shm"));
        }
    }

    public static String defaultBackupFileName() {
        return "my_anime_watch_ledger_backup_" +
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".db";
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static void validateDatabase(Path file, boolean validateSchema) throws SQLException, IOException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            int version;
            try (Statement statement = connection.createStatement();
                 var result = statement.executeQuery("PRAGMA user_version")) {
                if (!result.next()) throw new SQLException("Backup has no readable schema version.");
                version = result.getInt(1);
            }
            if (version < 1) throw new SQLException("Backup has an unknown or uninitialized schema version.");
            if (version > LATEST_SUPPORTED_SCHEMA_VERSION) {
                throw new SQLException("Backup schema version " + version + " is newer than this application supports ("
                        + LATEST_SUPPORTED_SCHEMA_VERSION + ").");
            }
            try (Statement statement = connection.createStatement();
                 var result = statement.executeQuery("PRAGMA integrity_check")) {
                if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                    throw new SQLException("SQLite integrity_check failed for the selected database.");
                }
            }
            try (Statement statement = connection.createStatement();
                 var result = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (result.next()) throw new SQLException("Backup contains a foreign-key violation in table " + result.getString(1) + ".");
            }
            if (validateSchema) validateRequiredSchema(connection, version);
        } catch (SQLException exception) {
            throw new SQLException("The selected file is not a compatible MyAnimeWatchLedger backup: " + exception.getMessage(), exception);
        }
    }

    private static void validateRequiredSchema(Connection connection, int version) throws SQLException {
        for (TableRequirement table : REQUIRED_TABLES) {
            if (table.name().equals("open_catalogue_tab") && version < 1) continue;
            try (Statement statement = connection.createStatement();
                 var result = statement.executeQuery("PRAGMA table_info('" + table.name() + "')")) {
                java.util.Set<String> columns = new java.util.HashSet<>();
                while (result.next()) columns.add(result.getString("name"));
                if (columns.isEmpty()) throw new SQLException("Required table is missing: " + table.name());
                for (String column : table.columns()) {
                    if (!columns.contains(column)) throw new SQLException("Required column is missing: " + table.name() + "." + column);
                }
            }
        }
        if (version >= 3) {
            try (Statement statement = connection.createStatement();
                 var result = statement.executeQuery("PRAGMA table_info('open_anime_detail_tab')")) {
                java.util.Set<String> columns = new java.util.HashSet<>();
                while (result.next()) columns.add(result.getString("name"));
                if (!columns.containsAll(List.of("mal_id", "tab_order")))
                    throw new SQLException("Backup is missing the open_anime_detail_tab schema.");
            }
        }
    }

    private record TableRequirement(String name, List<String> columns) { }
}
