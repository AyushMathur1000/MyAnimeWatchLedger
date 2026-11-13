package com.myanimewatchledger.presentation.main;

import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.presentation.ledger.LedgerController;
import com.myanimewatchledger.presentation.ledger.LedgerView;
import com.myanimewatchledger.presentation.catalogue.CatalogueController;
import com.myanimewatchledger.presentation.catalogue.CatalogueView;
import com.myanimewatchledger.presentation.navigation.NavigationSidebar;
import com.myanimewatchledger.presentation.state.WindowState;
import com.myanimewatchledger.presentation.state.WindowStateStore;
import com.myanimewatchledger.presentation.status.ApplicationStatusBar;
import com.myanimewatchledger.presentation.toolbar.ContextualToolbar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Main application shell with persistent global regions and replaceable workspace. */
public final class MainWindow {
    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());
    private final ApplicationServices services;
    private final WindowStateStore windowStateStore;
    private final BorderPane root = new BorderPane();
    private final ContextualToolbar toolbar;
    private final NavigationSidebar sidebar = new NavigationSidebar(this::navigate);
    private MainNavigation activeNavigation = MainNavigation.MY_LEDGER;
    private double lastWindowedWidth = WindowState.defaults().width();
    private double lastWindowedHeight = WindowState.defaults().height();

    public MainWindow(ApplicationServices services) {
        this(services, new WindowStateStore(
                com.myanimewatchledger.config.ApplicationPaths.dataDirectory()
                        .resolve("config").resolve("ui.properties")));
    }

    public MainWindow(ApplicationServices services, WindowStateStore windowStateStore) {
        this.services = Objects.requireNonNull(services, "services");
        this.windowStateStore = Objects.requireNonNull(windowStateStore, "windowStateStore");
        this.toolbar = new ContextualToolbar(this::navigate);
    }

    public void show(Stage stage) {
        root.getStyleClass().add("app-root");
        root.setLeft(sidebar);
        root.setTop(createHeaderAndToolbar());
        root.setBottom(new ApplicationStatusBar(services.isMalConfigured()));

        Scene scene = new Scene(root);
        var stylesheet = getClass().getResource("/styles/application.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());

        WindowState saved = windowStateStore.load();
        activeNavigation = saved.lastNavigation();
        sidebar.select(activeNavigation);
        toolbar.showFor(activeNavigation);
        renderWorkspace();
        lastWindowedWidth = saved.width();
        lastWindowedHeight = saved.height();
        stage.setTitle("MyAnimeWatchLedger");
        stage.setMinWidth(800);
        stage.setMinHeight(560);
        stage.setWidth(saved.width());
        stage.setHeight(saved.height());
        stage.widthProperty().addListener((observable, oldValue, newValue) -> {
            if (!stage.isMaximized() && newValue.doubleValue() >= 800) lastWindowedWidth = newValue.doubleValue();
        });
        stage.heightProperty().addListener((observable, oldValue, newValue) -> {
            if (!stage.isMaximized() && newValue.doubleValue() >= 560) lastWindowedHeight = newValue.doubleValue();
        });
        stage.setScene(scene);
        stage.setOnHiding(event -> saveWindowState(stage));
        stage.show();
        stage.setMaximized(saved.maximized());
    }

    private VBox createHeaderAndToolbar() {
        Label title = new Label("MyAnimeWatchLedger");
        title.getStyleClass().add("app-title");
        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 18, 10, 18));
        header.getStyleClass().add("application-header");
        return new VBox(header, toolbar);
    }

    private void navigate(MainNavigation navigation) {
        if (activeNavigation == navigation) return;
        activeNavigation = navigation;
        sidebar.select(navigation);
        toolbar.showFor(navigation);
        renderWorkspace();
    }

    private void renderWorkspace() {
        if (activeNavigation == MainNavigation.CATALOGUE) {
            root.setCenter(new CatalogueView(new CatalogueController(services)));
            return;
        }
        if (activeNavigation == MainNavigation.MY_LEDGER) {
            root.setCenter(new LedgerView(new LedgerController(services.ledger())));
            return;
        }
        Label title = new Label(activeNavigation.label());
        title.getStyleClass().add("workspace-title");
        Label message = new Label(activeNavigation.label()
                + " workspace will be available in its implementation milestone.");
        message.getStyleClass().add("workspace-message");
        VBox workspace = new VBox(10, title, message);
        workspace.setAlignment(Pos.TOP_LEFT);
        workspace.setPadding(new Insets(26));
        workspace.getStyleClass().add("main-workspace");
        root.setCenter(workspace);
    }

    private void saveWindowState(Stage stage) {
        try {
            windowStateStore.save(new WindowState(
                    lastWindowedWidth, lastWindowedHeight, stage.isMaximized(), activeNavigation));
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "Could not save main window preferences.", exception);
        }
    }
}
