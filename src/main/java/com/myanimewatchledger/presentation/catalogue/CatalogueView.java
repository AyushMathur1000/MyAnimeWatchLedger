package com.myanimewatchledger.presentation.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.application.service.LedgerChangeEvent;
import com.myanimewatchledger.application.service.SeasonNavigator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Window;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.LongConsumer;

/** Seasonal catalogue workspace with restorable tabs, local search and ledger actions. */
public final class CatalogueView extends BorderPane {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CatalogueController controller;
    private final LongConsumer onOpenDetails;
    private final TabPane tabs = new TabPane();
    private final ComboBox<Season> seasonChoice = new ComboBox<>();
    private final Spinner<Integer> yearChoice = new Spinner<>(1900, 9999, java.time.Year.now().getValue());
    private final Label feedback = new Label();
    private final Map<Long, SeasonTab> openTabs = new LinkedHashMap<>();
    private boolean restoring;
    private AutoCloseable ledgerSubscription;

    public CatalogueView(CatalogueController controller) {
        this(controller, null, malId -> { });
    }

    public CatalogueView(CatalogueController controller, Long selectedSearchId) {
        this(controller, selectedSearchId, malId -> { });
    }

    /** Creates the catalogue and routes detail actions to the shared Anime Details workspace. */
    public CatalogueView(CatalogueController controller, Long selectedSearchId, LongConsumer onOpenDetails) {
        this.controller = Objects.requireNonNull(controller);
        this.onOpenDetails = Objects.requireNonNull(onOpenDetails);
        getStyleClass().add("catalogue-workspace");
        setPadding(new Insets(18, 22, 20, 22));
        seasonChoice.getItems().setAll(Season.values());
        SeasonNavigator.SeasonYear current = SeasonNavigator.current(Clock.systemDefaultZone());
        seasonChoice.setValue(current.season());
        yearChoice.getValueFactory().setValue(current.year());
        Button previous = new Button("← Previous");
        Button open = new Button("Open season");
        Button next = new Button("Next →");
        Button refresh = new Button("⟳ Refresh");
        previous.setOnAction(e -> shiftSeason(-1));
        next.setOnAction(e -> shiftSeason(1));
        open.setOnAction(e -> openSelectedSeason());
        refresh.setOnAction(e -> refreshSelected());
        HBox seasonBar = new HBox(9, previous, next, seasonChoice, yearChoice, open, new Separator(), refresh);
        seasonBar.setAlignment(Pos.CENTER_LEFT);
        seasonBar.getStyleClass().add("catalogue-season-bar");
        feedback.getStyleClass().add("catalogue-feedback");
        VBox top = new VBox(8, seasonBar, feedback);
        setTop(top);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getUserData() instanceof SeasonTab seasonTab) {
                seasonChoice.setValue(seasonTab.search.season());
                yearChoice.getValueFactory().setValue(seasonTab.search.year());
            }
        });
        setCenter(tabs);
        restoreOrOpenCurrent(selectedSearchId);
        ledgerSubscription = controller.subscribeToLedgerChanges(this::ledgerChanged);
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) closeLedgerSubscription();
        });
    }

    private void restoreOrOpenCurrent(Long selectedSearchId) {
        restoring = true;
        try {
            List<OpenCatalogueTab> saved = controller.restoreTabs();
            if (saved.isEmpty()) {
                SeasonNavigator.SeasonYear current = SeasonNavigator.current(Clock.systemDefaultZone());
                openSeason(current.season(), current.year(), "", true);
            } else {
                for (OpenCatalogueTab tab : saved) {
                    controller.findSearch(tab.searchId()).ifPresent(search ->
                            createTab(search, tab.searchQuery(), false));
                }
                if (selectedSearchId != null) {
                    SeasonTab requested = openTabs.get(selectedSearchId);
                    if (requested != null) tabs.getSelectionModel().select(requested.tab);
                    else if (!tabs.getTabs().isEmpty()) tabs.getSelectionModel().selectFirst();
                } else if (!tabs.getTabs().isEmpty()) {
                    tabs.getSelectionModel().selectFirst();
                }
            }
        } finally {
            restoring = false;
        }
        for (SeasonTab tab : openTabs.values()) {
            if (tab.search.lastSuccessfulFetchAt() == null) refresh(tab, false);
            else loadCards(tab);
        }
    }

    private void openSelectedSeason() {
        openSeason(seasonChoice.getValue(), yearChoice.getValue(), "", true);
    }

    private void openSeason(Season season, int year, String query, boolean select) {
        if (season == null) return;
        SeasonTab existing = openTabs.values().stream()
                .filter(tab -> tab.search.season() == season && tab.search.year() == year)
                .findFirst().orElse(null);
        if (existing != null) {
            if (select) tabs.getSelectionModel().select(existing.tab);
            return;
        }
        OpenCatalogueTab saved = controller.open(season, year, query);
        CatalogueSearch search = controller.findSearch(saved.searchId()).orElseThrow();
        SeasonTab created = createTab(search, query, select);
        if (search.lastSuccessfulFetchAt() == null) refresh(created, false);
        else loadCards(created);
    }

    private SeasonTab createTab(CatalogueSearch search, String query, boolean select) {
        SeasonTab state = new SeasonTab(search);
        state.searchField.setText(query == null ? "" : query);
        state.searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            controller.updateQuery(search.searchId(), newValue);
            loadCards(state);
        });
        state.tab.setText(label(search));
        state.tab.setClosable(true);
        state.tab.setUserData(state);
        state.tab.setContent(state.content);
        state.tab.setOnClosed(event -> {
            openTabs.remove(search.searchId());
            controller.close(search.searchId());
        });
        openTabs.put(search.searchId(), state);
        tabs.getTabs().add(state.tab);
        if (select) tabs.getSelectionModel().select(state.tab);
        return state;
    }

    private void shiftSeason(int direction) {
        SeasonTab selected = selectedTab();
        SeasonNavigator.SeasonYear from = selected == null
                ? new SeasonNavigator.SeasonYear(seasonChoice.getValue(), yearChoice.getValue())
                : new SeasonNavigator.SeasonYear(selected.search.season(), selected.search.year());
        SeasonNavigator.SeasonYear target = direction < 0
                ? SeasonNavigator.previous(from) : SeasonNavigator.next(from);
        seasonChoice.setValue(target.season());
        yearChoice.getValueFactory().setValue(target.year());
        openSeason(target.season(), target.year(), "", true);
    }

    private void refreshSelected() {
        SeasonTab selected = selectedTab();
        if (selected != null) refresh(selected, true);
        else openSelectedSeason();
    }

    private void refresh(SeasonTab state, boolean manual) {
        if (!controller.isMalConfigured()) {
            feedback.setText("Configure a MAL Client ID in Settings before retrieving seasonal anime.");
            return;
        }
        if (state.refreshing) return;
        state.refreshing = true;
        state.progress.setVisible(true);
        state.progress.setManaged(true);
        state.message.setText("Retrieving " + label(state.search) + " from MyAnimeList…");
        controller.refresh(state.search.season(), state.search.year()).whenComplete((outcome, failure) ->
                Platform.runLater(() -> {
                    state.refreshing = false;
                    state.progress.setVisible(false);
                    state.progress.setManaged(false);
                    if (failure != null) {
                        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                ? failure.getCause() : failure;
                        state.message.setText("Refresh failed. Previously cached results have been preserved.");
                        feedback.setText("Could not refresh " + label(state.search) + ": " + safeMessage(cause));
                    } else {
                        state.message.setText(outcome.animeCount() + " anime loaded"
                                + (outcome.skippedItems() > 0 ? " · " + outcome.skippedItems() + " skipped" : ""));
                        feedback.setText(outcome.warnings().isEmpty() ? "Catalogue refreshed successfully."
                                : "Catalogue refreshed with " + outcome.warnings().size() + " warning(s).");
                        controller.findSearch(state.search.searchId()).ifPresent(search -> {
                            state.search = search;
                            state.tab.setText(label(search));
                        });
                        loadCards(state);
                    }
                }));
    }

    private void loadCards(SeasonTab state) {
        try {
            List<CatalogueAnime> anime = controller.loadAnime(state.search.searchId(), state.searchField.getText());
            state.cards.getChildren().clear();
            if (anime.isEmpty()) {
                Label empty = new Label(state.search.lastSuccessfulFetchAt() == null
                        ? "No cached results yet. Refresh this season to retrieve anime."
                        : "No anime match this search, or all results are blacklisted.");
                empty.getStyleClass().add("catalogue-empty");
                state.cards.getChildren().add(empty);
            } else {
                for (CatalogueAnime item : anime) state.cards.getChildren().add(createCard(item));
            }
            state.message.setText(anime.size() + " anime");
        } catch (RuntimeException failure) {
            state.message.setText("Could not load cached catalogue: " + safeMessage(failure));
        }
    }

    private VBox createCard(CatalogueAnime item) {
        AnimeMetadata metadata;
        try { metadata = JSON.readValue(item.metadataJson(), AnimeMetadata.class); }
        catch (Exception invalid) {
            VBox broken = new VBox(6, new Label(item.preferredTitle()),
                    new Label("Cached metadata could not be displayed."));
            broken.getStyleClass().add("anime-card");
            return broken;
        }
        VBox card = new VBox(8);
        card.getStyleClass().add("anime-card");
        card.setPrefWidth(230);
        card.setMaxWidth(260);
        ImageView poster = new ImageView();
        poster.setFitWidth(190);
        poster.setFitHeight(250);
        poster.setPreserveRatio(true);
        poster.setSmooth(true);
        if (metadata.pictureMedium() != null) {
            try { poster.setImage(new Image(metadata.pictureMedium(), 190, 250, true, true, true)); }
            catch (IllegalArgumentException ignored) { }
        }
        Label noPoster = new Label("No poster available");
        StackPane posterFrame = new StackPane(poster, noPoster);
        posterFrame.getStyleClass().add("anime-poster-frame");
        noPoster.setVisible(metadata.pictureMedium() == null || metadata.pictureMedium().isBlank());
        posterFrame.setMinHeight(250);
        posterFrame.setMaxHeight(250);
        Label title = new Label(item.preferredTitle());
        title.getStyleClass().add("anime-card-title");
        title.setWrapText(true);
        Label details = new Label(joinDetails(metadata));
        details.getStyleClass().add("anime-card-details");
        details.setWrapText(true);
        String synopsisText = metadata.synopsis() == null || metadata.synopsis().isBlank()
                ? "No synopsis available." : metadata.synopsis();
        Label synopsis = new Label(synopsisText);
        synopsis.getStyleClass().add("anime-card-synopsis");
        synopsis.setWrapText(true);
        synopsis.setMaxHeight(88);
        String ledgerText = item.ledgerStatus() == null ? "Not in your ledger"
                : "Ledger: " + displayStatus(item.ledgerStatus());
        Label ledger = new Label(ledgerText);
        ledger.getStyleClass().add("anime-card-ledger");
        Button detailsAction = new Button("ⓘ");
        detailsAction.getStyleClass().addAll("anime-card-icon-action", "anime-card-details-action");
        detailsAction.setTooltip(new Tooltip("View anime details"));
        detailsAction.setAccessibleText("View anime details");
        detailsAction.setOnAction(e -> onOpenDetails.accept(item.malId()));

        Button ledgerAction = new Button(item.ledgerStatus() == null ? "＋" : "✓");
        ledgerAction.getStyleClass().add("anime-card-icon-action");
        ledgerAction.setTooltip(new Tooltip(item.ledgerStatus() == null
                ? "Add to My Ledger as Watch Later" : "Already in My Ledger"));
        ledgerAction.setAccessibleText(item.ledgerStatus() == null
                ? "Add to My Ledger as Watch Later" : "Already in My Ledger");
        ledgerAction.setDisable(item.ledgerStatus() != null);
        ledgerAction.setOnAction(e -> addToLedger(item, ledgerAction, ledger));

        HBox actions = new HBox(8, detailsAction, ledgerAction);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Region cardSpacer = new Region();
        VBox.setVgrow(cardSpacer, Priority.ALWAYS);
        card.setMinHeight(510);
        card.setPrefHeight(510);
        card.getChildren().addAll(posterFrame, title, details, synopsis, ledger, cardSpacer, actions);
        return card;
    }

    private void ledgerChanged(LedgerChangeEvent event) {
        Platform.runLater(() -> {
            for (SeasonTab tab : List.copyOf(openTabs.values())) loadCards(tab);
        });
    }

    private void closeLedgerSubscription() {
        if (ledgerSubscription == null) return;
        try { ledgerSubscription.close(); }
        catch (Exception ignored) { }
        ledgerSubscription = null;
    }

    private void addToLedger(CatalogueAnime anime, Button action, Label ledgerLabel) {
        try {
            controller.add(anime, LedgerStatus.WATCH_LATER);
            ledgerLabel.setText("Ledger: Watch Later");
            action.setText("✓");
            action.setTooltip(new Tooltip("Already in My Ledger"));
            action.setAccessibleText("Already in My Ledger");
            action.setDisable(true);
            feedback.setText("Added “" + anime.preferredTitle() + "” to My Ledger as Watch Later.");
        } catch (RuntimeException failure) {
            feedback.setText("Could not add anime: " + safeMessage(failure));
        }
    }

    private static String joinDetails(AnimeMetadata data) {
        List<String> parts = new ArrayList<>();
        if (data.meanScore() != null) parts.add("★ " + data.meanScore());
        if (data.mediaType() != null) parts.add(data.mediaType().toUpperCase(Locale.ROOT));
        if (!data.genres().isEmpty()) parts.add(String.join(", ", data.genres().stream()
                .map(AnimeMetadata.NamedValue::name).toList()));
        return String.join(" · ", parts);
    }

    private static String label(CatalogueSearch search) {
        return search.season().name().charAt(0) + search.season().name().substring(1).toLowerCase(Locale.ROOT)
                + " " + search.year();
    }
    private static String displayStatus(LedgerStatus status) {
        String value = status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Arrays.stream(value.split(" ")).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(value);
    }
    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }
    private SeasonTab selectedTab() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return selected != null && selected.getUserData() instanceof SeasonTab state ? state : null;
    }

    private final class SeasonTab {
        private CatalogueSearch search;
        private final Tab tab = new Tab();
        private final TextField searchField = new TextField();
        private final FlowPane cards = new FlowPane(14, 14);
        private final ScrollPane scroll = new ScrollPane(cards);
        private final Label message = new Label();
        private final ProgressIndicator progress = new ProgressIndicator();
        private final VBox content = new VBox(10);
        private boolean refreshing;
        private SeasonTab(CatalogueSearch search) {
            this.search = search;
            searchField.setPromptText("Search this season… (AND across words and metadata)");
            searchField.setPrefWidth(360);
            cards.setPadding(new Insets(8));
            cards.setPrefWrapLength(1050);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("catalogue-scroll");
            Button clearSearch = new Button("Clear search");
            clearSearch.setOnAction(event -> searchField.clear());
            HBox searchRow = new HBox(12, searchField, clearSearch, message, progress);
            searchRow.setAlignment(Pos.CENTER_LEFT);
            progress.setPrefSize(22, 22);
            progress.setVisible(false);
            progress.setManaged(false);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            content.setPadding(new Insets(10, 2, 2, 2));
            content.getChildren().addAll(searchRow, scroll);
        }
    }
}
