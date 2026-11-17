package com.myanimewatchledger.presentation.settings;

import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.presentation.state.WindowStateStore;
import com.myanimewatchledger.presentation.state.CataloguePresentation;
import com.myanimewatchledger.presentation.feedback.SoundFeedback;
import com.myanimewatchledger.infrastructure.persistence.DatabaseBackupService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Application preferences and local MAL API configuration. */
public final class SettingsView extends VBox {
    private final ApplicationServices services;

    public SettingsView(
            WindowStateStore preferences,
            Consumer<Boolean> onBackgroundOpeningChanged,
            ApplicationServices services,
            Consumer<Boolean> onMalConfiguredChanged,
            Runnable onDatabaseRestored) {
        this.services = Objects.requireNonNull(services, "services");
        Objects.requireNonNull(onMalConfiguredChanged, "onMalConfiguredChanged");
        Objects.requireNonNull(onDatabaseRestored, "onDatabaseRestored");
        Objects.requireNonNull(preferences, "preferences");
        Objects.requireNonNull(onBackgroundOpeningChanged, "onBackgroundOpeningChanged");

        getStyleClass().add("settings-workspace");
        setSpacing(14);
        setPadding(new Insets(26));

        Label title = new Label("Settings");
        title.getStyleClass().add("workspace-title");

        Label subtitle = new Label("Application behaviour");
        subtitle.getStyleClass().add("settings-section-title");

        Label cataloguePresentationLabel = new Label("Catalogue presentation");
        cataloguePresentationLabel.getStyleClass().add("settings-field-label");
        ComboBox<CataloguePresentation> cataloguePresentation = new ComboBox<>();
        cataloguePresentation.getItems().setAll(CataloguePresentation.GRID,
                CataloguePresentation.SINGLE_CARD_CAROUSEL);
        cataloguePresentation.setValue(preferences.loadCataloguePresentation());
        cataloguePresentation.setMaxWidth(360);
        cataloguePresentation.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(CataloguePresentation item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : presentationLabel(item));
            }
        });
        cataloguePresentation.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(CataloguePresentation item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : presentationLabel(item));
            }
        });
        cataloguePresentation.setTooltip(new Tooltip(
                "Choose between the adaptive card grid and a single-card horizontal carousel."));
        Label cataloguePresentationFeedback = new Label();
        cataloguePresentationFeedback.getStyleClass().add("settings-feedback");
        cataloguePresentation.setOnAction(event -> {
            CataloguePresentation selected = cataloguePresentation.getValue();
            try {
                preferences.saveCataloguePresentation(selected);
                cataloguePresentationFeedback.setText("Preference saved. Reopen Catalogue to apply it.");
            } catch (IOException failure) {
                cataloguePresentation.setValue(preferences.loadCataloguePresentation());
                cataloguePresentationFeedback.setText(
                        "Could not save this preference. Check application file permissions.");
            }
        });
        CheckBox backgroundOpening = new CheckBox("Open Anime Details in Background");
        backgroundOpening.setSelected(preferences.loadOpenAnimeDetailsInBackground());
        backgroundOpening.setTooltip(new Tooltip(
                "When enabled, opening anime details does not switch away from the current workspace."));
        Label description = new Label("Open or activate detail tabs without leaving the current workspace.");
        description.getStyleClass().add("settings-description");
        Label preferenceFeedback = new Label();
        preferenceFeedback.getStyleClass().add("settings-feedback");

        backgroundOpening.setOnAction(event -> {
            boolean selected = backgroundOpening.isSelected();
            try {
                preferences.saveOpenAnimeDetailsInBackground(selected);
                onBackgroundOpeningChanged.accept(selected);
                preferenceFeedback.setText("Preference saved.");
            } catch (IOException failure) {
                backgroundOpening.setSelected(!selected);
                preferenceFeedback.setText("Could not save this preference. Check application file permissions.");
            }
        });

        Label feedbackSubtitle = new Label("Interaction feedback");
        feedbackSubtitle.getStyleClass().add("settings-section-title");
        CheckBox soundEffects = new CheckBox("Enable UI sound effects");
        soundEffects.setSelected(preferences.loadUiSoundsEnabled());
        soundEffects.setTooltip(new Tooltip("Play a subtle click sound when buttons are activated."));
        CheckBox animations = new CheckBox("Enable UI animations");
        animations.setSelected(preferences.loadUiAnimationsEnabled());
        animations.setTooltip(new Tooltip("Enable decorative transitions when switching workspaces."));
        Label feedbackPreferenceStatus = new Label();
        feedbackPreferenceStatus.getStyleClass().add("settings-feedback");

        soundEffects.setOnAction(event -> {
            boolean selected = soundEffects.isSelected();
            try {
                preferences.saveUiSoundsEnabled(selected);
                SoundFeedback.setEnabled(selected);
                feedbackPreferenceStatus.setText("Preference saved.");
            } catch (IOException failure) {
                soundEffects.setSelected(!selected);
                feedbackPreferenceStatus.setText(
                        "Could not save this preference. Check application file permissions.");
            }
        });

        animations.setOnAction(event -> {
            boolean selected = animations.isSelected();
            try {
                preferences.saveUiAnimationsEnabled(selected);
                feedbackPreferenceStatus.setText("Preference saved.");
            } catch (IOException failure) {
                animations.setSelected(!selected);
                feedbackPreferenceStatus.setText(
                        "Could not save this preference. Check application file permissions.");
            }
        });

        Label malSubtitle = new Label("MyAnimeList API");
        malSubtitle.getStyleClass().add("settings-section-title");
        Label malDescription = new Label(
                "A MAL Client ID enables seasonal catalogue retrieval and online anime details. "
                        + "It is stored locally on this device and is not part of the database.");
        malDescription.getStyleClass().add("settings-description");
        Label configuredState = new Label();
        configuredState.getStyleClass().add("settings-feedback");
        updateConfiguredState(configuredState);

        PasswordField clientIdInput = new PasswordField();
        clientIdInput.setPromptText("Enter MAL Client ID");
        clientIdInput.setMaxWidth(360);
        clientIdInput.setAccessibleText("MAL Client ID");

        Button saveButton = new Button("Save Client ID");
        Button testButton = new Button("Test Connection");
        testButton.setDisable(!services.isMalConfigured());
        Label malFeedback = new Label();
        malFeedback.getStyleClass().add("settings-feedback");

        saveButton.setOnAction(event -> {
            String clientId = clientIdInput.getText();
            if (clientId == null || clientId.isBlank()) {
                malFeedback.setText("Enter a Client ID before saving.");
                return;
            }
            setBusy(saveButton, testButton, true);
            malFeedback.setText("Saving MAL configuration...");
            services.saveMalClientIdAsync(clientId).whenComplete((ignored, failure) ->
                    Platform.runLater(() -> {
                        setBusy(saveButton, testButton, false);
                        if (failure != null) {
                            malFeedback.setText(safeMessage(failure));
                        } else {
                            clientIdInput.clear();
                            configuredState.setText("Client ID configured. The value is hidden.");
                            onMalConfiguredChanged.accept(true);
                            testButton.setDisable(false);
                            malFeedback.setText("Client ID saved. Test the connection to verify it.");
                        }
                    }));
        });

        testButton.setOnAction(event -> {
            setBusy(saveButton, testButton, true);
            malFeedback.setText("Testing connection to MyAnimeList...");
            try {
                services.testMalConnectionAsync().whenComplete((ignored, failure) ->
                        Platform.runLater(() -> {
                            setBusy(saveButton, testButton, false);
                            malFeedback.setText(failure == null
                                    ? "Connection successful. MAL API access is working."
                                    : safeMessage(failure));
                        }));
            } catch (RuntimeException failure) {
                setBusy(saveButton, testButton, false);
                malFeedback.setText(safeMessage(failure));
            }
        });

        HBox actions = new HBox(8, saveButton, testButton);
        Label databaseSubtitle = new Label("Database backup and restore");
        databaseSubtitle.getStyleClass().add("settings-section-title");
        Label databaseDescription = new Label(
                "Backups include your ledger, cached catalogue data, search history, and open-tab state. "
                        + "Backup files are ordinary, unencrypted SQLite databases; store them somewhere private.");
        databaseDescription.getStyleClass().add("settings-description");
        Button createBackupButton = new Button("Create Backup");
        Button restoreBackupButton = new Button("Restore Backup");
        Label databaseFeedback = new Label();
        databaseFeedback.getStyleClass().add("settings-feedback");

        createBackupButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Create Database Backup");
            chooser.setInitialFileName(DatabaseBackupService.defaultBackupFileName());
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite database (*.db)", "*.db"));
            File selected = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
            if (selected == null) return;
            createBackupButton.setDisable(true);
            restoreBackupButton.setDisable(true);
            databaseFeedback.setText("Creating and validating backup...");
            services.createDatabaseBackupAsync(selected.toPath()).whenComplete((saved, failure) ->
                    Platform.runLater(() -> {
                        createBackupButton.setDisable(false);
                        restoreBackupButton.setDisable(false);
                        databaseFeedback.setText(failure == null
                                ? "Backup created and validated: " + saved
                                : safeMessage(failure));
                    }));
        });

        restoreBackupButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Database Backup");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite database (*.db)", "*.db"));
            File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (selected == null) return;
            Alert confirmation = new Alert(Alert.AlertType.WARNING,
                    "Restoring replaces the current local database, including ledger records, cached metadata, "
                            + "search history, and open-tab state. This cannot be undone from within the application. "
                            + "Continue with this restore?", ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Confirm Database Restore");
            confirmation.setHeaderText("Current local data will be replaced");
            Window owner = getScene() == null ? null : getScene().getWindow();
            if (owner != null) confirmation.initOwner(owner);
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            createBackupButton.setDisable(true);
            restoreBackupButton.setDisable(true);
            databaseFeedback.setText("Validating backup and restoring database...");
            services.restoreDatabaseBackupAsync(selected.toPath()).whenComplete((ignored, failure) ->
                    Platform.runLater(() -> {
                        createBackupButton.setDisable(false);
                        restoreBackupButton.setDisable(false);
                        if (failure == null) {
                            databaseFeedback.setText("Database restored successfully.");
                            onDatabaseRestored.run();
                        } else {
                            databaseFeedback.setText(safeMessage(failure));
                        }
                    }));
        });

        HBox databaseActions = new HBox(8, createBackupButton, restoreBackupButton);

        Label cacheSubtitle = new Label("Catalogue cache");
        cacheSubtitle.getStyleClass().add("settings-section-title");
        Label cacheDescription = new Label(
                "Clear cached anime metadata, seasonal results, search history, and saved Catalogue tabs. "
                        + "Your personal ledger and Anime Details tabs are not affected.");
        cacheDescription.getStyleClass().add("settings-description");
        Button clearCacheButton = new Button("Clear Catalogue Cache");
        Label cacheFeedback = new Label();
        cacheFeedback.getStyleClass().add("settings-feedback");

        clearCacheButton.setOnAction(event -> {
            Alert confirmation = new Alert(Alert.AlertType.WARNING,
                    "This removes cached anime metadata, seasonal results, search history, and saved Catalogue tabs. "
                            + "Your personal ledger and Anime Details tabs will remain unchanged. Continue?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Clear Catalogue Cache");
            confirmation.setHeaderText("Disposable catalogue data will be removed");
            Window owner = getScene() == null ? null : getScene().getWindow();
            if (owner != null) confirmation.initOwner(owner);
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            clearCacheButton.setDisable(true);
            cacheFeedback.setText("Clearing catalogue cache...");
            services.clearCatalogueCacheAsync().whenComplete((ignored, failure) ->
                    Platform.runLater(() -> {
                        clearCacheButton.setDisable(false);
                        cacheFeedback.setText(failure == null
                                ? "Catalogue cache cleared. Your ledger and Anime Details tabs were preserved."
                                : safeMessage(failure));
                    }));
        });

        getChildren().addAll(
                title, subtitle, cataloguePresentationLabel, cataloguePresentation, cataloguePresentationFeedback,
                backgroundOpening, description, preferenceFeedback,
                feedbackSubtitle, soundEffects, animations, feedbackPreferenceStatus,
                malSubtitle, malDescription, configuredState, clientIdInput, actions, malFeedback,
                cacheSubtitle, cacheDescription, clearCacheButton, cacheFeedback,
                databaseSubtitle, databaseDescription, databaseActions, databaseFeedback);
    }

    private static String presentationLabel(CataloguePresentation presentation) {
        return switch (presentation) {
            case GRID -> "Grid";
            case SINGLE_CARD_CAROUSEL -> "Single-card carousel";
        };
    }

    private static String presentationLabel(CataloguePresentation presentation) {
        return switch (presentation) {
            case GRID -> "Grid";
            case SINGLE_CARD_CAROUSEL -> "Single-card carousel";
        };
    }

    private void updateConfiguredState(Label label) {
        label.setText(services.isMalConfigured()
                ? "Client ID configured. The value is hidden."
                : "No Client ID configured.");
    }

    private void setBusy(Button save, Button test, boolean busy) {
        save.setDisable(busy);
        test.setDisable(busy || !services.isMalConfigured());
    }

    private static String safeMessage(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause.getCause() != null)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? "The MAL operation failed. Check the connection and try again."
                : message;
    }
}
