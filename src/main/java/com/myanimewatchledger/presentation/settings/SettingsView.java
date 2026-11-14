package com.myanimewatchledger.presentation.settings;

import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.presentation.state.WindowStateStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
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
            Consumer<Boolean> onMalConfiguredChanged) {
        this.services = Objects.requireNonNull(services, "services");
        Objects.requireNonNull(onMalConfiguredChanged, "onMalConfiguredChanged");
        Objects.requireNonNull(preferences, "preferences");
        Objects.requireNonNull(onBackgroundOpeningChanged, "onBackgroundOpeningChanged");

        getStyleClass().add("settings-workspace");
        setSpacing(14);
        setPadding(new Insets(26));

        Label title = new Label("Settings");
        title.getStyleClass().add("workspace-title");

        Label subtitle = new Label("Application behaviour");
        subtitle.getStyleClass().add("settings-section-title");
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
        getChildren().addAll(
                title, subtitle, backgroundOpening, description, preferenceFeedback,
                malSubtitle, malDescription, configuredState, clientIdInput, actions, malFeedback);
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
