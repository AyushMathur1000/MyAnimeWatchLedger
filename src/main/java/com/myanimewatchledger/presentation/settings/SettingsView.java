package com.myanimewatchledger.presentation.settings;

import com.myanimewatchledger.presentation.state.WindowStateStore;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/** Small application-preference surface for settings approved in the current milestone. */
public final class SettingsView extends VBox {
    public SettingsView(WindowStateStore preferences, Consumer<Boolean> onBackgroundOpeningChanged) {
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
        Label feedback = new Label();
        feedback.getStyleClass().add("settings-feedback");

        backgroundOpening.setOnAction(event -> {
            boolean selected = backgroundOpening.isSelected();
            try {
                preferences.saveOpenAnimeDetailsInBackground(selected);
                onBackgroundOpeningChanged.accept(selected);
                feedback.setText("Preference saved.");
            } catch (IOException failure) {
                backgroundOpening.setSelected(!selected);
                feedback.setText("Could not save this preference. Check application file permissions.");
            }
        });

        getChildren().addAll(title, subtitle, backgroundOpening, description, feedback);
    }
}
