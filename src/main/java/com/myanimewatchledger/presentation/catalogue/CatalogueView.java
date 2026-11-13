package com.myanimewatchledger.presentation.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
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

import java.awt.Desktop;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletionException;

/** Seasonal catalogue workspace with restorable tabs, local search and ledger actions. */
public final class CatalogueView extends BorderPane {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CatalogueController controller;
    private final TabPane tabs = new TabPane();
    private final ComboBox<Season> seasonChoice = new ComboBox<>();
    private final Spinner<Integer> yearChoice = new Spinner<>(1900, 9999, java.time.Year.now().getValue());
    private final Label feedback = new Label();
    private final Map<Long, SeasonTab> openTabs = new LinkedHashMap<>();
    private boolean restoring;

    public CatalogueView(CatalogueController controller) {
        this.controller = Objects.requireNonNull(controller);
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
        HBox seasonBar = new HBox(9, previous, seasonChoice, yearChoice, open, next, new Separator(), refresh);
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
        restoreOrOpenCurrent();
    }

    private void restoreOrOpenCurrent() {
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
                if (!tabs.getTabs().isEmpty()) tabs.getSelectionModel().selectLast();
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
        OpenCatalogueTab saved = controller.open(season, year, query);
        SeasonTab existing = openTabs.get(saved.searchId());
        if (existing != null) {
            if (select) tabs.getSelectionModel().select(existing.tab);
            return;
        }
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
        StackPane posterFrame = new StackPane(poster, new Label("No poster available"));
        posterFrame.getStyleClass().add("anime-poster-frame");
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
        Button ledgerAction = new Button(item.ledgerStatus() == null ? "＋ Add to ledger" : "Change status");
        ledgerAction.setOnAction(e -> manageLedger(item, ledgerAction, ledger));
        Button mal = new Button("Open MAL ↗");
        mal.setOnAction(e -> openMal(item.malId()));
        HBox actions = new HBox(7, ledgerAction, mal);
        actions.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(posterFrame, title, details, synopsis, ledger, actions);
        return card;
    }

    private void manageLedger(CatalogueAnime anime, Button action, Label ledgerLabel) {
        Optional<com.myanimewatchledger.domain.ledger.LedgerEntry> existing =
                controller.findLedgerEntry(anime.malId());
        LedgerStatus initial = existing.map(e -> e.status()).orElse(LedgerStatus.WATCH_LATER);
        ChoiceDialog<LedgerStatus> dialog = new ChoiceDialog<>(initial, List.of(LedgerStatus.values()));
        dialog.setTitle(existing.isPresent() ? "Change ledger status" : "Add to My Ledger");
        dialog.setHeaderText(anime.preferredTitle());
        dialog.setContentText("Select status:");
        dialog.setConverter(new javafx.util.StringConverter<>() {
            public String toString(LedgerStatus status) { return status == null ? "" : displayStatus(status); }
            public LedgerStatus fromString(String value) { return null; }
        });
        dialog.showAndWait().ifPresent(status -> {
            try {
                if (existing.isPresent()) controller.changeStatus(anime.malId(), status);
                else controller.add(anime, status);
                if (status == LedgerStatus.BLACKLISTED) {
                    SeasonTab selected = selectedTab();
                    if (selected != null) loadCards(selected);
                } else {
                    ledgerLabel.setText("Ledger: " + displayStatus(status));
                    action.setText("Change status");
                }
                feedback.setText("Ledger updated: " + displayStatus(status) + ".");
            } catch (RuntimeException failure) {
                feedback.setText("Could not update ledger: " + safeMessage(failure));
            }
        });
    }

    private static void openMal(long malId) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(
                    URI.create("https://myanimelist.net/anime/" + malId));
        } catch (Exception failure) {
            // External browser integration is optional; the catalogue remains usable.
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
            HBox searchRow = new HBox(12, searchField, message, progress);
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
