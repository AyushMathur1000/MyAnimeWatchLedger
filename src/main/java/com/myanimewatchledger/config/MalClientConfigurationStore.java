package com.myanimewatchledger.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Reads and writes the user's MAL Client ID without exposing it through logs or exceptions. */
public final class MalClientConfigurationStore {
    private static final String CLIENT_ID_PROPERTY = "mal.client-id";
    private final Path configurationFile;

    public MalClientConfigurationStore() {
        this(ApplicationPaths.configurationFile());
    }

    public MalClientConfigurationStore(Path configurationFile) {
        this.configurationFile = Objects.requireNonNull(configurationFile, "configurationFile")
                .toAbsolutePath().normalize();
    }

    public Optional<String> loadClientId() throws IOException {
        if (Files.notExists(configurationFile)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configurationFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String value = properties.getProperty(CLIENT_ID_PROPERTY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(validateClientId(value));
    }

    public void saveClientId(String clientId) throws IOException {
        String normalized = validateClientId(clientId);
        Path parent = configurationFile.getParent();
        Files.createDirectories(parent);

        Properties properties = new Properties();
        if (Files.exists(configurationFile)) {
            try (Reader reader = Files.newBufferedReader(configurationFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        properties.setProperty(CLIENT_ID_PROPERTY, normalized);

        Path temporaryFile = Files.createTempFile(parent, "app-", ".properties.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "MyAnimeWatchLedger local configuration");
            }
            try {
                Files.move(temporaryFile, configurationFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, configurationFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static String validateClientId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MAL Client ID must not be blank.");
        }
        String normalized = value.trim();
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("MAL Client ID contains invalid control characters.");
        }
        return normalized;
    }
}
