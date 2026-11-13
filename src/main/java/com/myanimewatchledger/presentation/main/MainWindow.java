package com.myanimewatchledger.presentation.main;

import com.myanimewatchledger.app.ApplicationServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Objects;

/** Initial presentation shell; feature screens are introduced in the UI implementation phase. */
public final class MainWindow {
    private final ApplicationServices services;

    public MainWindow(ApplicationServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public void show(Stage stage) {
        Label title = new Label("MyAnimeWatchLedger");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Application services are ready.");
        subtitle.getStyleClass().add("app-subtitle");
        Label malStatus = new Label(services.isMalConfigured()
                ? "MAL API configured"
                : "MAL API not configured — add a Client ID in Settings when available");
        malStatus.getStyleClass().add("app-subtitle");

        VBox content = new VBox(10, title, subtitle, malStatus);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(32));

        BorderPane root = new BorderPane(content);
        root.getStyleClass().add("app-root");
        Scene scene = new Scene(root, 1000, 680);
        var stylesheet = getClass().getResource("/styles/application.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());

        stage.setTitle("MyAnimeWatchLedger");
        stage.setMinWidth(800);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();
    }
}
