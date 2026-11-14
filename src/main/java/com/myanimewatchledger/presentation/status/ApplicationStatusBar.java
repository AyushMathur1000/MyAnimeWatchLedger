package com.myanimewatchledger.presentation.status;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Persistent application, database and MAL configuration status indicators. */
public final class ApplicationStatusBar extends HBox {
    private final Label mal;

    public ApplicationStatusBar(boolean malConfigured) {
        getStyleClass().add("application-status-bar");
        setPadding(new Insets(7, 14, 7, 14));
        setSpacing(18);

        Label database = new Label("● Database ready");
        database.getStyleClass().add("status-ready");
        mal = new Label();
        setMalConfigured(malConfigured);
        Label activity = new Label("Idle");
        activity.getStyleClass().add("status-muted");
        getChildren().addAll(database, mal, activity);
    }

    public void setMalConfigured(boolean configured) {
        mal.setText(configured ? "● MAL configured" : "○ MAL not configured");
        mal.getStyleClass().removeAll("status-ready", "status-muted");
        mal.getStyleClass().add(configured ? "status-ready" : "status-muted");
    }
}
