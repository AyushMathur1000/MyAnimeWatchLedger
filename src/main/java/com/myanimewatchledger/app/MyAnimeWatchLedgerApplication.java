package com.myanimewatchledger.app;

import com.myanimewatchledger.presentation.main.MainWindow;
import javafx.application.Application;
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
            new MainWindow().show(primaryStage);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Application startup failed.", exception);
            initializer.close();
            throw new IllegalStateException("MyAnimeWatchLedger could not initialize. Check the application log.", exception);
        }
    }

    @Override
    public void stop() {
        initializer.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
