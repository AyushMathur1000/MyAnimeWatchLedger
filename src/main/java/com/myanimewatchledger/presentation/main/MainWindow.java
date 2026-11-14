package com.myanimewatchledger.presentation.main;

import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.config.ApplicationPaths;
import com.myanimewatchledger.application.service.SeasonNavigator;
import com.myanimewatchledger.presentation.catalogue.CatalogueController;
import com.myanimewatchledger.presentation.catalogue.CatalogueView;
import com.myanimewatchledger.presentation.detail.AnimeDetailsController;
import com.myanimewatchledger.presentation.detail.AnimeDetailsView;
import com.myanimewatchledger.presentation.feedback.SoundFeedback;
import com.myanimewatchledger.presentation.history.SearchHistoryController;
import com.myanimewatchledger.presentation.history.SearchHistoryView;
import com.myanimewatchledger.presentation.ledger.LedgerController;
import com.myanimewatchledger.presentation.ledger.LedgerView;
import com.myanimewatchledger.presentation.navigation.NavigationSidebar;
import com.myanimewatchledger.presentation.settings.SettingsView;
import com.myanimewatchledger.presentation.state.WindowState;
import com.myanimewatchledger.presentation.state.WindowStateStore;
import com.myanimewatchledger.presentation.status.ApplicationStatusBar;
import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.Clock;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Main application shell; long-lived workspaces are retained while navigation changes. */
public final class MainWindow {
    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());

    private final ApplicationServices services;
    private final WindowStateStore windowStateStore;
    private final BorderPane root = new BorderPane();
    private final NavigationSidebar sidebar = new NavigationSidebar(this::navigate);
    private AnimeDetailsView animeDetailsView;
    private final ApplicationStatusBar statusBar;
    private MainNavigation activeNavigation = MainNavigation.MY_LEDGER;
    private Long requestedCatalogueSearchId;
    private boolean openAnimeDetailsInBackground;
    private double lastWindowedWidth = WindowState.defaults().width();
    private double lastWindowedHeight = WindowState.defaults().height();

    public MainWindow(ApplicationServices services) {
        this(services, new WindowStateStore(
                ApplicationPaths.dataDirectory().resolve("config").resolve("ui.properties")));
    }

    public MainWindow(ApplicationServices services, WindowStateStore store) {
        this.services = Objects.requireNonNull(services);
        windowStateStore = Objects.requireNonNull(store);
        animeDetailsView = new AnimeDetailsView(new AnimeDetailsController(services));
        statusBar = new ApplicationStatusBar(services.isMalConfigured());
    }

    public void show(Stage stage) {
        root.getStyleClass().add("app-root");
        root.setLeft(sidebar);
        root.setTop(createHeader());
        root.setBottom(statusBar);
        Scene scene = new Scene(root);
        scene.addEventFilter(ActionEvent.ACTION, event -> {
            if (event.getTarget() instanceof ButtonBase) {
                SoundFeedback.playClick();
            }
        });
        var stylesheet = getClass().getResource("/styles/application.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        var phase6Stylesheet = getClass().getResource("/styles/phase6.css");
        if (phase6Stylesheet != null) scene.getStylesheets().add(phase6Stylesheet.toExternalForm());

        WindowState saved = windowStateStore.load();
        openAnimeDetailsInBackground = windowStateStore.loadOpenAnimeDetailsInBackground();
        activeNavigation = saved.lastNavigation();
        sidebar.select(activeNavigation);
        renderWorkspace();
        animeDetailsView.restoreTabs();
        lastWindowedWidth = saved.width();
        lastWindowedHeight = saved.height();

        stage.setTitle("MyAnimeWatchLedger");
        stage.setMinWidth(800);
        stage.setMinHeight(560);
        stage.setWidth(saved.width());
        stage.setHeight(saved.height());
        stage.widthProperty().addListener((observable, oldValue, value) -> {
            if (!stage.isMaximized() && value.doubleValue() >= 800) lastWindowedWidth = value.doubleValue();
        });
        stage.heightProperty().addListener((observable, oldValue, value) -> {
            if (!stage.isMaximized() && value.doubleValue() >= 560) lastWindowedHeight = value.doubleValue();
        });
        stage.setScene(scene);
        stage.setOnHiding(event -> saveWindowState(stage));
        stage.show();
        stage.setMaximized(saved.maximized());
    }

    private HBox createHeader() {
        Label title = new Label("MyAnimeWatchLedger");
        title.getStyleClass().add("app-title");
        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 18, 10, 18));
        header.getStyleClass().add("application-header");
        return header;
    }

    private void navigate(MainNavigation navigation) {
        if (activeNavigation == navigation) return;
        activeNavigation = navigation;
        sidebar.select(navigation);
        renderWorkspace();
    }

    private void openHistoricalSearch(long id) {
        services.catalogueTabs().openSearch(id);
        requestedCatalogueSearchId = id;
        navigate(MainNavigation.CATALOGUE);
    }

    private void openCurrentSeason() {
        SeasonNavigator.SeasonYear current = SeasonNavigator.current(Clock.systemDefaultZone());
        var tab = services.catalogueTabs().open(current.season(), current.year(), "");
        requestedCatalogueSearchId = tab.searchId();
        navigate(MainNavigation.CATALOGUE);
    }

    private void openAnimeDetails(long malId) {
        if (!openAnimeDetailsInBackground) navigate(MainNavigation.ANIME_DETAILS);
        animeDetailsView.openAnime(malId);
    }

    private void renderWorkspace() {
        switch (activeNavigation) {
            case CATALOGUE -> {
                Long requestedId = requestedCatalogueSearchId;
                requestedCatalogueSearchId = null;
                root.setCenter(new CatalogueView(
                        new CatalogueController(services), requestedId, this::openAnimeDetails));
            }
            case MY_LEDGER -> root.setCenter(
                    new LedgerView(new LedgerController(services.ledger()),
                            this::openAnimeDetails, this::openCurrentSeason));
            case ANIME_DETAILS -> root.setCenter(animeDetailsView);
            case SEARCH_HISTORY -> root.setCenter(
                    new SearchHistoryView(new SearchHistoryController(services), this::openHistoricalSearch));
            case SETTINGS -> root.setCenter(new SettingsView(
                    windowStateStore, enabled -> openAnimeDetailsInBackground = enabled,
                    services, statusBar::setMalConfigured, this::refreshAfterDatabaseRestore));
        }
        Node workspace = root.getCenter();
        if (workspace != null) {
            workspace.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(150), workspace);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
    }

    private void refreshAfterDatabaseRestore() {
        animeDetailsView = new AnimeDetailsView(new AnimeDetailsController(services));
        animeDetailsView.restoreTabs();
        renderWorkspace();
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
