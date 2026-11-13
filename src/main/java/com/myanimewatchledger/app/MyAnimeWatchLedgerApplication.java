package com.myanimewatchledger.app;

import com.myanimewatchledger.presentation.main.MainWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class MyAnimeWatchLedgerApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(MyAnimeWatchLedgerApplication.class.getName());
    private final ApplicationInitializer initializer = new ApplicationInitializer();

    @Override
    public void start(Stage primaryStage) {
        try {
            initializer.initialize();
            new MainWindow(initializer.services()).show(primaryStage);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Application startup failed.", exception);
            initializer.close();
            showStartupFailure(primaryStage);
            Platform.exit();
        }
    }

    private static void showStartupFailure(Stage owner) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Startup failed");
        alert.setHeaderText("MyAnimeWatchLedger could not start");
        alert.setContentText("""
                The application could not initialize its local database.

                No application data has been intentionally changed. Please check the
                application log for technical details, resolve the reported problem,
                and try starting MyAnimeWatchLedger again.
                """);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        initializer.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
