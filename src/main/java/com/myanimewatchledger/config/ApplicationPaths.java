package com.myanimewatchledger.config;

import java.nio.file.Path;

/** Resolves application-owned files independently of the process working directory. */
public final class ApplicationPaths {
    private static final String APPLICATION_DIRECTORY = "MyAnimeWatchLedger";
    private static final String DATABASE_FILE = "my_anime_watch_ledger.db";

    private ApplicationPaths() { }

    public static Path dataDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, APPLICATION_DIRECTORY);
        }
        return Path.of(System.getProperty("user.home"), ".myanimewatchledger");
    }

    public static Path databaseFile() {
        return dataDirectory().resolve(DATABASE_FILE);
    }
}
