package com.myanimewatchledger.presentation.state;

import com.myanimewatchledger.presentation.main.MainNavigation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Objects;
import java.util.Properties;

/** Stores window geometry and lightweight UI preferences outside the database. */
public final class WindowStateStore {
    private static final String BACKGROUND_DETAILS_KEY = "anime-details.open-in-background";
    private final Path file;

    public WindowStateStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public WindowState load() {
        if (Files.notExists(file)) return WindowState.defaults();
        Properties properties = loadProperties();
        try {
            double width = Double.parseDouble(properties.getProperty("window.width", "1200"));
            double height = Double.parseDouble(properties.getProperty("window.height", "780"));
            boolean maximized = Boolean.parseBoolean(properties.getProperty("window.maximized", "true"));
            MainNavigation navigation = MainNavigation.valueOf(
                    properties.getProperty("window.last-navigation", MainNavigation.MY_LEDGER.name()));
            return new WindowState(width, height, maximized, navigation);
        } catch (IllegalArgumentException exception) {
            return WindowState.defaults();
        }
    }

    public boolean loadOpenAnimeDetailsInBackground() {
        return Boolean.parseBoolean(loadProperties().getProperty(BACKGROUND_DETAILS_KEY, "false"));
    }

    public void saveOpenAnimeDetailsInBackground(boolean enabled) throws IOException {
        Properties properties = loadProperties();
        properties.setProperty(BACKGROUND_DETAILS_KEY, Boolean.toString(enabled));
        saveProperties(properties);
    }

    public void save(WindowState state) throws IOException {
        Objects.requireNonNull(state, "state");
        Properties properties = loadProperties();
        properties.setProperty("window.width", Double.toString(state.width()));
        properties.setProperty("window.height", Double.toString(state.height()));
        properties.setProperty("window.maximized", Boolean.toString(state.maximized()));
        properties.setProperty("window.last-navigation", state.lastNavigation().name());
        saveProperties(properties);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (Files.notExists(file)) return properties;
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException ignored) {
            // Invalid/unreadable preferences fall back to safe defaults.
        }
        return properties;
    }

    private void saveProperties(Properties properties) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "window-", ".properties.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "MyAnimeWatchLedger preferences");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
