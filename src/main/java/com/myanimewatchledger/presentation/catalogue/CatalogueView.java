package com.myanimewatchledger.presentation.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.AnimePreference;
import com.myanimewatchledger.presentation.image.MalImageLoader;
import com.myanimewatchledger.presentation.image.PosterPreviewDialog;
import com.myanimewatchledger.presentation.feedback.ToastNotifications;
import com.myanimewatchledger.presentation.state.CataloguePresentation;
import com.myanimewatchledger.application.service.LedgerChangeEvent;
import com.myanimewatchledger.application.service.SeasonNavigator;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ComboBoxBase;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Window;

import java.time.Clock;
import java.awt.Desktop;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.LongConsumer;

/** Seasonal catalogue workspace with restorable tabs, local search and ledger actions. */
public final class CatalogueView extends BorderPane {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CatalogueController controller;
    private final LongConsumer onOpenDetails;
    private final boolean carouselMode;
    private final TabPane tabs = new TabPane();
    private final ComboBox<Season> seasonChoice = new ComboBox<>();
    private final Spinner<Integer> yearChoice = new Spinner<>(1900, 9999, java.time.Year.now().getValue());
    private final Map<Long, SeasonTab> openTabs = new LinkedHashMap<>();
    private boolean restoring;
    private boolean hideLedgerEntries;
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
        this.carouselMode = controller.cataloguePresentation() == CataloguePresentation.SINGLE_CARD_CAROUSEL;
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
        Button clearCache = new Button("Clear Cache");
        CheckMenuItem hideLedgerItem = new CheckMenuItem("Hide anime already in My Ledger");
        hideLedgerEntries = controller.hideLedgerEntries();
        hideLedgerItem.setSelected(hideLedgerEntries);
        MenuButton filter = new MenuButton("Filter", null, hideLedgerItem);
        filter.getStyleClass().add("catalogue-filter-button");
        filter.setTooltip(new Tooltip("Filter catalogue results"));
        hideLedgerItem.setOnAction(event -> {
            boolean selected = hideLedgerItem.isSelected();
            try {
                controller.saveHideLedgerEntries(selected);
                hideLedgerEntries = selected;
                for (SeasonTab tab : openTabs.values()) loadCards(tab);
            } catch (RuntimeException failure) {
                hideLedgerItem.setSelected(hideLedgerEntries);
                ToastNotifications.show(this, safeMessage(failure), true);
            }
        });
        clearCache.setTooltip(new Tooltip("Remove cached catalogue data and search history; preserve My Ledger."));
        clearCache.setAccessibleText("Clear Catalogue Cache");
        previous.setOnAction(e -> shiftSeason(-1));
        next.setOnAction(e -> shiftSeason(1));
        open.setOnAction(e -> openSelectedSeason());
        refresh.setOnAction(e -> refreshSelected());
        clearCache.setOnAction(e -> clearCache());
        HBox seasonBar = new HBox(7, previous, next, seasonChoice, yearChoice, open,
                new Separator(), refresh, clearCache, filter);
        seasonBar.setAlignment(Pos.CENTER_LEFT);
        seasonBar.getStyleClass().add("catalogue-season-bar");
        VBox top = new VBox(5, seasonBar);
        setTop(top);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getUserData() instanceof SeasonTab seasonTab) {
                seasonChoice.setValue(seasonTab.search.season());
                yearChoice.getValueFactory().setValue(seasonTab.search.year());
            }
        });
        setCenter(tabs);
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleCarouselNavigationKey);
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

    private void clearCache() {
        Alert confirmation = new Alert(Alert.AlertType.WARNING,
                "This removes cached anime metadata, seasonal results, search history, and saved Catalogue tabs. "
                        + "Your personal ledger and Anime Details tabs will remain unchanged. Continue?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setTitle("Clear Catalogue Cache");
        confirmation.setHeaderText("Disposable catalogue data will be removed");
        Window owner = getScene() == null ? null : getScene().getWindow();
        if (owner != null) confirmation.initOwner(owner);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        setDisable(true);
        ToastNotifications.show(this, "Clearing catalogue cache…", false);
        controller.clearCacheAsync().whenComplete((ignored, failure) ->
                Platform.runLater(() -> {
                    setDisable(false);
                    if (failure != null) {
                        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                ? failure.getCause() : failure;
                        ToastNotifications.show(this, "Could not clear catalogue cache: " + safeMessage(cause), true);
                    } else {
                        tabs.getTabs().clear();
                        openTabs.clear();
                        ToastNotifications.show(this, "Catalogue cache cleared.", false);
                    }
                }));
    }

    private void refresh(SeasonTab state, boolean manual) {
        if (!controller.isMalConfigured()) {
            ToastNotifications.show(this, "Configure a MAL Client ID in Settings before retrieving seasonal anime.", true);
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
                        ToastNotifications.show(this, "Could not refresh " + label(state.search) + ": " + safeMessage(cause), true);
                    } else {
                        state.message.setText(outcome.animeCount() + " anime loaded"
                                + (outcome.skippedItems() > 0 ? " · " + outcome.skippedItems() + " skipped" : ""));
                        if (manual) ToastNotifications.show(this, outcome.warnings().isEmpty()
                                ? "Catalogue refreshed successfully."
                                : "Catalogue refreshed with " + outcome.warnings().size() + " warning(s).",
                                !outcome.warnings().isEmpty());
                        controller.findSearch(state.search.searchId()).ifPresent(search -> {
                            state.search = search;
                            state.tab.setText(label(search));
                        });
                        loadCards(state);
                    }
                }));
    }

    /**
     * Reconciles the visible result set by MAL ID. Existing card nodes are retained when their
     * displayed data is unchanged; only changed/new cards are created and only obsolete nodes
     * are detached. This keeps unrelated poster ImageViews alive during ledger changes, searches,
     * and successful catalogue refreshes.
     */
    private void loadCards(SeasonTab state) {
        try {
            List<CatalogueAnime> anime = controller.loadAnime(
                    state.search.searchId(), state.searchField.getText(), hideLedgerEntries);
            List<Node> desiredNodes = new ArrayList<>(anime.isEmpty() ? 1 : anime.size());
            Long previouslySelected = state.selectedMalId();
            state.visibleIds.clear();

            for (CatalogueAnime item : anime) {
                AnimePreference preference = controller.findLedgerEntry(item.malId())
                        .map(LedgerEntry::preference).orElse(null);
                CardCacheEntry cached = state.cardCache.get(item.malId());
                if (cached == null || !cached.matches(item, preference)) {
                    cached = new CardCacheEntry(item.metadataJson(), item.preferredTitle(),
                            item.ledgerStatus(), preference,
                            carouselMode ? null : createCard(item, preference, false),
                            carouselMode ? createCarouselCard(item, preference, state) : null);
                    state.cardCache.put(item.malId(), cached);
                }
                if (!carouselMode) desiredNodes.add(cached.card());
                state.visibleIds.add(item.malId());
            }

            if (anime.isEmpty()) {
                state.emptyState.setText(state.search.lastSuccessfulFetchAt() == null
                        ? "No cached results yet. Refresh this season to retrieve anime."
                        : "No anime match this search or the active filters.");
                desiredNodes.add(state.emptyState);
            }

            reconcileChildren(state.cards, desiredNodes);
            if (previouslySelected != null && state.visibleIds.contains(previouslySelected)) {
                state.currentIndex = state.visibleIds.indexOf(previouslySelected);
            } else if (state.currentIndex >= state.visibleIds.size()) {
                state.currentIndex = Math.max(0, state.visibleIds.size() - 1);
            }
            renderCarousel(state);
            state.message.setText(anime.size() + " anime");
        } catch (RuntimeException failure) {
            state.message.setText("Could not load cached catalogue: " + safeMessage(failure));
        }
    }

    /** Applies the smallest possible child-list edits while preserving the identity of retained nodes. */
    private static void reconcileChildren(Pane container, List<Node> desiredNodes) {
        List<Node> children = container.getChildren();
        for (int index = 0; index < desiredNodes.size(); index++) {
            Node desired = desiredNodes.get(index);
            if (index < children.size() && children.get(index) == desired) continue;

            int existingIndex = children.indexOf(desired);
            if (existingIndex >= 0) children.remove(existingIndex);
            children.add(Math.min(index, children.size()), desired);
        }
        while (children.size() > desiredNodes.size()) {
            children.remove(children.size() - 1);
        }
    }

    private VBox createCard(CatalogueAnime item, AnimePreference currentPreference, boolean includePreferenceActions) {
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
        Label noPoster = new Label("No poster available");
        StackPane posterFrame = new StackPane(poster, noPoster);
        posterFrame.getStyleClass().add("anime-poster-frame");
        MalImageLoader.load(poster, noPoster, metadata.pictureMedium(), metadata.pictureLarge(), 190, 250);
        if (MalImageLoader.selectUrl(metadata.pictureLarge(), metadata.pictureMedium()) != null) {
            posterFrame.setCursor(javafx.scene.Cursor.HAND);
            posterFrame.setFocusTraversable(true);
            posterFrame.setAccessibleText("Enlarge cover image");
            posterFrame.setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    PosterPreviewDialog.show(metadata.pictureLarge(), metadata.pictureMedium(),
                            item.preferredTitle(), posterFrame.getScene() == null
                                    ? null : posterFrame.getScene().getWindow());
                }
            });
        }
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
        HBox preferenceActions = new HBox(6);
        preferenceActions.setAlignment(Pos.CENTER_LEFT);
        Button likeAction = new Button("👍");
        Button dislikeAction = new Button("👎");
        likeAction.getStyleClass().add("anime-card-preference-action");
        dislikeAction.getStyleClass().add("anime-card-preference-action");
        likeAction.setTooltip(new Tooltip("Like this anime"));
        dislikeAction.setTooltip(new Tooltip("Dislike this anime"));
        likeAction.setAccessibleText("Like this anime");
        dislikeAction.setAccessibleText("Dislike this anime");
        if (currentPreference == AnimePreference.LIKE) likeAction.getStyleClass().add("ledger-preference-active");
        if (currentPreference == AnimePreference.DISLIKE) dislikeAction.getStyleClass().add("ledger-preference-active");
        likeAction.setDisable(item.ledgerStatus() == null);
        dislikeAction.setDisable(item.ledgerStatus() == null);
        likeAction.setOnAction(event -> updateCardPreference(item, currentPreference == AnimePreference.LIKE ? null : AnimePreference.LIKE));
        dislikeAction.setOnAction(event -> updateCardPreference(item, currentPreference == AnimePreference.DISLIKE ? null : AnimePreference.DISLIKE));
        preferenceActions.getChildren().addAll(likeAction, dislikeAction);
        Button malAction = new Button("↗");
        malAction.getStyleClass().addAll("anime-card-icon-action", "anime-card-mal-action");
        malAction.setTooltip(new Tooltip("Open on MyAnimeList"));
        malAction.setAccessibleText("Open on MyAnimeList");
        malAction.setOnAction(e -> openMal(item.malId()));

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
        ledgerAction.setOnAction(e -> addToLedger(item, LedgerStatus.WATCH_LATER, ledgerAction, ledger));

        Button blacklistAction = new Button("⊘");
        blacklistAction.getStyleClass().addAll("anime-card-icon-action", "anime-card-blacklist-action");
        blacklistAction.setTooltip(new Tooltip(item.ledgerStatus() == null
                ? "Add to My Ledger as Blacklisted" : "Already in My Ledger"));
        blacklistAction.setAccessibleText(item.ledgerStatus() == null
                ? "Add to My Ledger as Blacklisted" : "Already in My Ledger");
        blacklistAction.setDisable(item.ledgerStatus() != null);
        blacklistAction.setOnAction(e -> addToLedger(item, LedgerStatus.BLACKLISTED, blacklistAction, ledger));

        HBox actions = new HBox(8, malAction, detailsAction, ledgerAction, blacklistAction);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Region cardSpacer = new Region();
        VBox.setVgrow(cardSpacer, Priority.ALWAYS);
        card.setMinHeight(510);
        card.setPrefHeight(510);
        if (includePreferenceActions) {
            card.getChildren().addAll(posterFrame, title, details, synopsis, ledger,
                    preferenceActions, cardSpacer, actions);
        } else {
            card.getChildren().addAll(posterFrame, title, details, synopsis, ledger, cardSpacer, actions);
        }
        return card;
    }

    private HBox createCarouselCard(CatalogueAnime item, AnimePreference preference, SeasonTab state) {
        VBox source = createCard(item, preference, true);
        List<Node> parts = new ArrayList<>(source.getChildren());
        source.getChildren().clear();

        Node posterNode = parts.remove(0);
        // The grid card's fixed dimensions and spacer must not leak into the
        // full-width carousel layout.
        source.setPrefWidth(-1);
        source.setMinWidth(0);
        source.setMaxWidth(Double.MAX_VALUE);

        VBox posterColumn = new VBox(posterNode);
        posterColumn.setAlignment(Pos.TOP_CENTER);
        posterColumn.setMinWidth(320);
        posterColumn.setPrefWidth(320);
        posterColumn.setMaxWidth(320);
        posterColumn.setMinHeight(430);
        posterColumn.setPrefHeight(430);
        HBox.setHgrow(posterColumn, Priority.NEVER);

        if (posterNode instanceof StackPane posterFrame) {
            // Override the grid's 190px CSS minimum width. The ImageView
            // otherwise grows beyond its StackPane and overlaps the details.
            posterFrame.setMinWidth(300);
            posterFrame.setPrefWidth(300);
            posterFrame.setMaxWidth(300);
            posterFrame.setMinHeight(430);
            posterFrame.setPrefHeight(430);
            posterFrame.setMaxHeight(430);
            posterFrame.setStyle("-fx-min-width: 300px; -fx-pref-width: 300px; -fx-max-width: 300px;");
            for (Node child : posterFrame.getChildren()) {
                if (child instanceof ImageView image) {
                    image.setFitWidth(280);
                    image.setFitHeight(420);
                    image.setPreserveRatio(true);
                }
            }
        }

        parts.removeIf(part -> part instanceof Region region
                && part.getClass() == Region.class);
        for (Node part : parts) {
            if (part instanceof Label label) {
                label.setMaxWidth(Double.MAX_VALUE);
                label.setWrapText(true);
                if (label.getStyleClass().contains("anime-card-synopsis")) {
                    // Keep the full synopsis in the scrollable details column.
                    label.setMaxHeight(Double.MAX_VALUE);
                }
                if (label.getStyleClass().contains("anime-card-title")) {
                    label.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
                }
            }
            if (part instanceof HBox actions) {
                actions.setMaxWidth(Double.MAX_VALUE);
            }
        }

        VBox information = new VBox(16);
        information.getStyleClass().add("anime-carousel-information");
        information.setFillWidth(true);
        information.setMinWidth(0);
        information.setMaxWidth(Double.MAX_VALUE);
        // Let long synopses scroll inside the information column instead of
        // increasing the card beyond the available catalogue viewport.
        ScrollPane informationScroll = new ScrollPane(information);
        informationScroll.setFitToWidth(true);
        informationScroll.setFitToHeight(true);
        informationScroll.setPrefViewportHeight(420);
        informationScroll.prefViewportHeightProperty().bind(
                Bindings.max(240, state.carouselCardHost.heightProperty().subtract(88)));
        informationScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        informationScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        informationScroll.getStyleClass().add("anime-carousel-information-scroll");
        informationScroll.setMinWidth(0);
        informationScroll.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(informationScroll, Priority.ALWAYS);
        VBox.setVgrow(informationScroll, Priority.ALWAYS);

        // The status selector is deliberately specific to carousel mode;
        // grid cards keep their existing compact quick-action layout.
        ComboBox<LedgerStatus> statusChoice = new ComboBox<>();
        statusChoice.getItems().setAll(LedgerStatus.values());
        statusChoice.setMaxWidth(260);
        statusChoice.setPromptText("Add to My Ledger…");
        statusChoice.setValue(item.ledgerStatus());
        statusChoice.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(LedgerStatus status, boolean empty) {
                super.updateItem(status, empty);
                setText(empty || status == null ? null : displayStatus(status));
            }
        });
        statusChoice.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(LedgerStatus status, boolean empty) {
                super.updateItem(status, empty);
                setText(empty || status == null ? "Add to My Ledger…" : displayStatus(status));
            }
        });
        statusChoice.setTooltip(new Tooltip("Add this anime to your ledger or change its current status."));
        statusChoice.setOnAction(event -> {
            LedgerStatus selected = statusChoice.getValue();
            if (selected == null || selected == item.ledgerStatus()) return;
            try {
                if (item.ledgerStatus() == null) controller.add(item, selected);
                else if (!controller.changeStatus(item.malId(), selected)) {
                    throw new IllegalStateException("The ledger status could not be changed.");
                }
                ToastNotifications.show(this, "Ledger status set to " + displayStatus(selected) + ".", false);
            } catch (RuntimeException failure) {
                statusChoice.setValue(item.ledgerStatus());
                ToastNotifications.show(this, "Could not update ledger status: " + safeMessage(failure), true);
            }
        });
        Label ledgerHeading = new Label("My Ledger status");
        ledgerHeading.getStyleClass().add("anime-card-ledger");
        VBox statusEditor = new VBox(6, ledgerHeading, statusChoice);
        statusEditor.setMaxWidth(Double.MAX_VALUE);
        information.getChildren().addAll(parts);
        information.getChildren().add(statusEditor);

        HBox card = new HBox(24, posterColumn, informationScroll);
        card.prefHeightProperty().bind(
                Bindings.max(490, state.carouselCardHost.heightProperty().subtract(8)));
        card.prefWidthProperty().bind(state.carouselCardHost.widthProperty());
        card.getStyleClass().addAll("anime-card", "anime-carousel-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(30));
        card.setMinSize(0, 0);
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return card;
    }

    private void renderCarousel(SeasonTab state) {
        if (!carouselMode) return;
        if (state.visibleIds.isEmpty()) {
            state.carouselCardHost.getChildren().setAll(state.carouselEmptyState);
            state.carouselPosition.setText("0 / 0");
            state.previousCard.setDisable(true);
            state.nextCard.setDisable(true);
            return;
        }
        state.currentIndex = Math.max(0, Math.min(state.currentIndex, state.visibleIds.size() - 1));
        long malId = state.visibleIds.get(state.currentIndex);
        CardCacheEntry entry = state.cardCache.get(malId);
        if (entry != null) state.carouselCardHost.getChildren().setAll(entry.carouselCard());
        state.carouselPosition.setText((state.currentIndex + 1) + " / " + state.visibleIds.size());
        state.previousCard.setDisable(state.currentIndex == 0);
        state.nextCard.setDisable(state.currentIndex >= state.visibleIds.size() - 1);
    }

    private void handleCarouselNavigationKey(KeyEvent event) {
        if (!carouselMode || (event.getCode() != KeyCode.LEFT && event.getCode() != KeyCode.RIGHT)) return;
        // Preserve caret movement and popup navigation in editable/search controls.
        if (event.getTarget() instanceof TextInputControl
                || event.getTarget() instanceof ComboBoxBase<?>) return;
        SeasonTab selected = selectedTab();
        if (selected == null || selected.visibleIds.isEmpty()) return;

        if (event.getCode() == KeyCode.LEFT) {
            if (selected.currentIndex > 0) selected.currentIndex--;
        } else if (selected.currentIndex < selected.visibleIds.size() - 1) {
            selected.currentIndex++;
        }
        renderCarousel(selected);
        event.consume();
    }

    private void updateCardPreference(CatalogueAnime anime, AnimePreference preference) {
        try {
            if (!controller.changePreference(anime.malId(), preference)) {
                ToastNotifications.show(this, "Could not update anime preference.", true);
            } else {
                ToastNotifications.show(this, preference == null ? "Anime preference cleared."
                        : preference == AnimePreference.LIKE ? "Anime liked." : "Anime disliked.", false);
            }
        } catch (RuntimeException failure) {
            ToastNotifications.show(this, "Could not update preference: " + safeMessage(failure), true);
        }
    }

    private static void openMal(long malId) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create("https://myanimelist.net/anime/" + malId));
            }
        } catch (Exception ignored) {
            // Browser integration is optional; the catalogue remains usable.
        }
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

    private void addToLedger(CatalogueAnime anime, LedgerStatus status, Button action, Label ledgerLabel) {
        try {
            controller.add(anime, status);
            ledgerLabel.setText("Ledger: " + displayStatus(status));
            action.setText("✓");
            action.setTooltip(new Tooltip("Already in My Ledger"));
            action.setAccessibleText("Already in My Ledger");
            action.setDisable(true);
            String confirmation = "Added “" + anime.preferredTitle() + "” to My Ledger as "
                    + displayStatus(status) + ".";
            if (status == LedgerStatus.BLACKLISTED) {
                ToastNotifications.showWarning(this, confirmation);
            } else {
                ToastNotifications.show(this, confirmation, false);
            }
        } catch (RuntimeException failure) {
            ToastNotifications.show(this, "Could not add anime: " + safeMessage(failure), true);
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
        private final StackPane carouselCardHost = new StackPane();
        private final VBox carouselPane = new VBox(12);
        private final Label carouselEmptyState = new Label("No anime match this search or the active filters.");
        private final Button previousCard = new Button("← Previous");
        private final Button nextCard = new Button("Next →");
        private final Label carouselPosition = new Label("0 / 0");
        private final List<Long> visibleIds = new ArrayList<>();
        private int currentIndex;
        private final StackPane viewArea = new StackPane();
        private final Label message = new Label();
        private final ProgressIndicator progress = new ProgressIndicator();
        private final VBox content = new VBox(6);
        private final Map<Long, CardCacheEntry> cardCache = new HashMap<>();
        private final Label emptyState = new Label();
        private boolean refreshing;

        private Long selectedMalId() {
            return visibleIds.isEmpty() ? null : visibleIds.get(Math.max(0,
                    Math.min(currentIndex, visibleIds.size() - 1)));
        }

        private SeasonTab(CatalogueSearch search) {
            this.search = search;
            searchField.setPromptText("Search this season… (AND across words and metadata)");
            searchField.setPrefWidth(360);
            cards.setPadding(new Insets(5, 6, 8, 6));
            cards.setPrefWrapLength(1050);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("catalogue-scroll");

            carouselCardHost.setAlignment(Pos.CENTER);
            carouselCardHost.setMinSize(0, 0);
            carouselCardHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            carouselPane.setPadding(new Insets(14, 10, 10, 10));
            carouselPane.setAlignment(Pos.TOP_CENTER);
            carouselPane.setFillWidth(true);
            VBox.setVgrow(carouselCardHost, Priority.ALWAYS);
            carouselEmptyState.getStyleClass().add("catalogue-empty-state");
            previousCard.setOnAction(event -> {
                currentIndex = Math.max(0, currentIndex - 1);
                renderCarousel(this);
            });
            nextCard.setOnAction(event -> {
                currentIndex = Math.min(visibleIds.size() - 1, currentIndex + 1);
                renderCarousel(this);
            });
            HBox carouselNavigation = new HBox(12, previousCard, carouselPosition, nextCard);
            carouselNavigation.setAlignment(Pos.CENTER);
            carouselPane.getChildren().addAll(carouselCardHost, carouselNavigation);
            viewArea.getChildren().addAll(scroll, carouselPane);
            scroll.setVisible(!carouselMode);
            scroll.setManaged(!carouselMode);
            carouselPane.setVisible(carouselMode);
            carouselPane.setManaged(carouselMode);

            Button clearSearch = new Button("Clear search");
            clearSearch.setOnAction(event -> searchField.clear());
            HBox searchRow = new HBox(8, searchField, clearSearch, message, progress);
            searchRow.setAlignment(Pos.CENTER_LEFT);
            progress.setPrefSize(22, 22);
            progress.setVisible(false);
            progress.setManaged(false);
            VBox.setVgrow(viewArea, Priority.ALWAYS);
            content.setPadding(new Insets(6, 2, 0, 2));
            content.getChildren().addAll(searchRow, viewArea);
        }
    }

    private record CardCacheEntry(String metadataJson, String preferredTitle,
                                  LedgerStatus ledgerStatus, AnimePreference preference,
                                  VBox card, HBox carouselCard) {
        private boolean matches(CatalogueAnime item, AnimePreference currentPreference) {
            return Objects.equals(metadataJson, item.metadataJson())
                    && Objects.equals(preferredTitle, item.preferredTitle())
                    && ledgerStatus == item.ledgerStatus()
                    && preference == currentPreference;
        }
    }
}
