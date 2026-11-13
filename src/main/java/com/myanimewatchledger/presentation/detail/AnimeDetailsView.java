package com.myanimewatchledger.presentation.detail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.application.service.AnimeDetailResult;
import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletionException;

/** Shared, tabbed Anime Details primary workspace. */
public final class AnimeDetailsView extends BorderPane {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AnimeDetailsController controller;
    private final TabPane tabs = new TabPane();
    private final Label feedback = new Label();
    private final StackPane emptyState = emptyPlaceholder();
    private final Map<Long, DetailTab> openTabs = new LinkedHashMap<>();
    private final Set<Long> openingIds = new HashSet<>();
    private final Set<Long> closingIds = new HashSet<>();
    private final Set<Long> pendingOpenIds = new LinkedHashSet<>();
    private boolean restorationStarted;
    private boolean restorationFinished;

    public AnimeDetailsView(AnimeDetailsController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        getStyleClass().add("anime-details-workspace");
        setPadding(new Insets(18, 22, 20, 22));

        Label title = new Label("Anime Details");
        title.getStyleClass().add("workspace-title");
        Button moveLeft = new Button("← Move tab");
        Button moveRight = new Button("Move tab →");
        moveLeft.setOnAction(event -> moveSelectedTab(-1));
        moveRight.setOnAction(event -> moveSelectedTab(1));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, title, spacer, moveLeft, moveRight);
        actions.setAlignment(Pos.CENTER_LEFT);

        feedback.getStyleClass().add("anime-details-feedback");
        setTop(new VBox(9, actions, feedback));
        StackPane center = new StackPane(tabs, emptyState);
        tabs.getTabs().addListener((ListChangeListener<Tab>) change ->
                emptyState.setVisible(tabs.getTabs().isEmpty()));
        emptyState.setVisible(true);
        setCenter(center);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
    }

    /** Starts one asynchronous restoration pass. Calls to openAnime are queued until it completes. */
    public void restoreTabs() {
        if (restorationStarted) return;
        restorationStarted = true;
        feedback.setText("Restoring open anime tabs…");
        controller.restoreTabs().whenComplete((saved, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                feedback.setText("Could not restore Anime Details tabs: " + safeMessage(unwrap(failure)));
            } else {
                for (OpenAnimeDetailTab tab : saved) createTab(tab.malId(), false);
                if (!tabs.getTabs().isEmpty()) tabs.getSelectionModel().selectFirst();
                feedback.setText(saved.isEmpty() ? "Open an anime from Catalogue or My Ledger to view its details."
                        : saved.size() + " anime detail tab(s) restored.");
            }
            restorationFinished = true;
            for (long malId : List.copyOf(pendingOpenIds)) openAnimeAfterRestore(malId);
            pendingOpenIds.clear();
        }));
    }

    /** Opens or activates one tab. Duplicate requests during persistence are coalesced. */
    public void openAnime(long malId) {
        if (malId <= 0) {
            feedback.setText("A valid MAL ID is required to open Anime Details.");
            return;
        }
        if (!restorationStarted) restoreTabs();
        if (!restorationFinished) {
            pendingOpenIds.add(malId);
            return;
        }
        openAnimeAfterRestore(malId);
    }

    private void openAnimeAfterRestore(long malId) {
        if (closingIds.contains(malId)) {
            pendingOpenIds.add(malId);
            return;
        }
        DetailTab existing = openTabs.get(malId);
        if (existing != null) {
            tabs.getSelectionModel().select(existing.tab);
            return;
        }
        if (!openingIds.add(malId)) return;
        feedback.setText("Opening Anime Details…");
        controller.openTab(malId).whenComplete((saved, failure) -> Platform.runLater(() -> {
            openingIds.remove(malId);
            if (failure != null) {
                feedback.setText("Could not open anime details: " + safeMessage(unwrap(failure)));
                return;
            }
            DetailTab tab = openTabs.get(malId);
            if (tab == null) tab = createTab(saved.malId(), true);
            else tabs.getSelectionModel().select(tab.tab);
        }));
    }

    private DetailTab createTab(long malId, boolean select) {
        DetailTab state = new DetailTab(malId);
        state.tab.setText("Anime #" + malId);
        state.tab.setUserData(malId);
        state.tab.setOnClosed(event -> {
            openTabs.remove(malId);
            closingIds.add(malId);
            controller.closeTab(malId).whenComplete((closed, failure) -> Platform.runLater(() -> {
                closingIds.remove(malId);
                if (failure != null) feedback.setText("Could not save the closed tab: " + safeMessage(unwrap(failure)));
                if (pendingOpenIds.remove(malId)) openAnimeAfterRestore(malId);
            }));
        });
        openTabs.put(malId, state);
        tabs.getTabs().add(state.tab);
        if (select) tabs.getSelectionModel().select(state.tab);
        loadDetails(state);
        return state;
    }

    private void loadDetails(DetailTab state) {
        state.message.setText("Loading cached metadata and checking MyAnimeList…");
        controller.loadDetails(state.malId).whenComplete((result, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                state.message.setText("Could not load anime details: " + safeMessage(unwrap(failure)));
                return;
            }
            renderDetails(state, result);
        }));
    }

    private void renderDetails(DetailTab state, AnimeDetailResult result) {
        state.body.getChildren().clear();
        if (result.metadata().isEmpty()) {
            state.tab.setText("Anime #" + state.malId);
            state.message.setText(result.warning() == null
                    ? "No metadata is available for this anime." : result.warning());
            return;
        }

        AnimeMetadataCache cache = result.metadata().orElseThrow();
        AnimeMetadata metadata;
        try {
            metadata = JSON.readValue(cache.metadataJson(), AnimeMetadata.class);
        } catch (Exception invalid) {
            state.message.setText("Cached metadata could not be read. The cache has been left unchanged.");
            return;
        }

        state.tab.setText(cache.preferredTitle());
        state.message.setText(result.warning() == null
                ? (result.refreshed() ? "Details updated from MyAnimeList." : "Showing locally cached details.")
                : "Showing cached details. " + result.warning());

        ImageView poster = new ImageView();
        poster.setFitWidth(210);
        poster.setFitHeight(300);
        poster.setPreserveRatio(true);
        String picture = firstNonBlank(metadata.pictureLarge(), metadata.pictureMedium());
        Label noPoster = new Label("No poster available");
        noPoster.getStyleClass().add("anime-details-no-poster");
        StackPane posterFrame = new StackPane(poster, noPoster);
        posterFrame.getStyleClass().add("anime-details-poster");
        posterFrame.setMinSize(220, 310);
        posterFrame.setPrefSize(220, 310);
        if (picture != null) {
            try {
                Image image = new Image(picture, 210, 300, true, true, true);
                poster.setImage(image);
                noPoster.visibleProperty().bind(image.errorProperty());
            } catch (IllegalArgumentException ignored) {
                noPoster.setVisible(true);
            }
        }

        VBox heading = new VBox(7);
        Label preferredTitle = new Label(cache.preferredTitle());
        preferredTitle.getStyleClass().add("anime-details-title");
        preferredTitle.setWrapText(true);
        Label canonical = new Label(value(metadata.canonicalTitle()));
        canonical.getStyleClass().add("anime-details-canonical");
        canonical.setWrapText(true);
        Button openMal = new Button("Open MyAnimeList ↗");
        openMal.setOnAction(event -> openMal(state.malId));
        heading.getChildren().addAll(preferredTitle, canonical, openMal);

        GridPane facts = new GridPane();
        facts.setHgap(18);
        facts.setVgap(9);
        facts.getStyleClass().add("anime-details-facts");
        int row = 0;
        row = addFact(facts, row, "MAL ID", Long.toString(metadata.malId()));
        row = addFact(facts, row, "Type", metadata.mediaType());
        row = addFact(facts, row, "Status", metadata.airingStatus());
        row = addFact(facts, row, "Season", season(metadata));
        row = addFact(facts, row, "Episodes", metadata.episodeCount() == null ? null : metadata.episodeCount().toString());
        row = addFact(facts, row, "Score", metadata.meanScore() == null ? null : metadata.meanScore().toString());
        row = addFact(facts, row, "Rank", metadata.rank() == null ? null : "#" + metadata.rank());
        row = addFact(facts, row, "Popularity", metadata.popularity() == null ? null : "#" + metadata.popularity());
        row = addFact(facts, row, "Start date", metadata.startDate());
        row = addFact(facts, row, "End date", metadata.endDate());
        row = addFact(facts, row, "Rating", metadata.contentRating());
        row = addFact(facts, row, "Source", metadata.source());
        row = addFact(facts, row, "Broadcast", broadcast(metadata));
        row = addFact(facts, row, "Average episode", duration(metadata.averageEpisodeDurationSeconds()));
        row = addFact(facts, row, "Studios", names(metadata.studios()));
        addFact(facts, row, "Genres", names(metadata.genres()));

        VBox summary = new VBox(14, heading, facts);
        summary.getStyleClass().add("anime-details-summary");
        HBox top = new HBox(22, posterFrame, summary);
        top.setAlignment(Pos.TOP_LEFT);
        state.body.getChildren().add(top);
        addTextSection(state.body, "Alternative titles", alternatives(metadata));
        addTextSection(state.body, "Synopsis", metadata.synopsis());
        addTextSection(state.body, "Background", metadata.background());

        controller.findLedgerEntry(state.malId).whenComplete((entry, failure) -> Platform.runLater(() -> {
            if (failure != null || !openTabs.containsKey(state.malId)) return;
            String status = entry.map(value -> "In My Ledger · " + displayStatus(value.status()))
                    .orElse("Not currently in My Ledger");
            state.ledgerStatus.setText(status);
            if (!state.body.getChildren().contains(state.ledgerStatus)) state.body.getChildren().add(0, state.ledgerStatus);
        }));
    }

    private static int addFact(GridPane grid, int row, String label, String value) {
        if (value == null || value.isBlank()) return row;
        Label key = new Label(label);
        key.getStyleClass().add("anime-details-fact-label");
        Label content = new Label(value);
        content.getStyleClass().add("anime-details-fact-value");
        content.setWrapText(true);
        grid.add(key, 0, row);
        grid.add(content, 1, row);
        return row + 1;
    }

    private static void addTextSection(VBox parent, String heading, String text) {
        if (text == null || text.isBlank()) return;
        Label title = new Label(heading);
        title.getStyleClass().add("anime-details-section-title");
        Label content = new Label(text);
        content.getStyleClass().add("anime-details-long-text");
        content.setWrapText(true);
        VBox section = new VBox(6, title, content);
        section.getStyleClass().add("anime-details-section");
        parent.getChildren().add(section);
    }

    private static String alternatives(AnimeMetadata metadata) {
        List<String> values = new ArrayList<>();
        add(values, metadata.englishTitle());
        add(values, metadata.romanizedTitle());
        add(values, metadata.japaneseTitle());
        values.addAll(metadata.synonyms());
        return values.isEmpty() ? null : String.join("\n", new LinkedHashSet<>(values));
    }

    private static String season(AnimeMetadata metadata) {
        if (metadata.season() == null && metadata.seasonYear() == null) return null;
        return (metadata.season() == null ? "" : metadata.season())
                + (metadata.seasonYear() == null ? "" : " " + metadata.seasonYear());
    }

    private static String broadcast(AnimeMetadata metadata) {
        if (metadata.broadcastDay() != null && metadata.broadcastStartTime() != null) {
            return metadata.broadcastDay() + " · " + metadata.broadcastStartTime();
        }
        return firstNonBlank(metadata.broadcastDay(), metadata.broadcastStartTime());
    }

    private static String duration(Integer seconds) {
        if (seconds == null || seconds <= 0) return null;
        int minutes = seconds / 60;
        return minutes + " min" + (seconds % 60 == 0 ? "" : " " + seconds % 60 + " sec");
    }

    private static String names(List<AnimeMetadata.NamedValue> values) {
        return values == null || values.isEmpty() ? null
                : String.join(", ", values.stream().map(AnimeMetadata.NamedValue::name).toList());
    }

    private static void add(List<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null || second.isBlank() ? null : second;
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    private static String displayStatus(com.myanimewatchledger.domain.ledger.LedgerStatus status) {
        String lower = status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Arrays.stream(lower.split(" "))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(lower);
    }

    private void moveSelectedTab(int direction) {
        int index = tabs.getSelectionModel().getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= tabs.getTabs().size()) return;
        Collections.swap(tabs.getTabs(), index, target);
        tabs.getSelectionModel().select(target);
        List<Long> order = tabs.getTabs().stream().map(tab -> (Long) tab.getUserData()).toList();
        controller.reorderTabs(order).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (failure != null) feedback.setText("Could not save tab order: " + safeMessage(unwrap(failure)));
        }));
    }

    private static StackPane emptyPlaceholder() {
        Label empty = new Label("No anime details are open. Open an anime from Catalogue or My Ledger.");
        empty.getStyleClass().add("anime-details-empty");
        return new StackPane(empty);
    }

    private static void openMal(long malId) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create("https://myanimelist.net/anime/" + malId));
            }
        } catch (Exception ignored) {
            // Browser integration is optional; the local detail workspace remains usable.
        }
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static final class DetailTab {
        private final long malId;
        private final Tab tab = new Tab();
        private final Label message = new Label();
        private final Label ledgerStatus = new Label();
        private final VBox body = new VBox(16);
        private final VBox content = new VBox(10);

        private DetailTab(long malId) {
            this.malId = malId;
            message.getStyleClass().add("anime-details-message");
            ledgerStatus.getStyleClass().add("anime-details-ledger-status");
            body.setPadding(new Insets(12));
            content.setPadding(new Insets(10, 2, 2, 2));
            content.getChildren().addAll(message, body);
            VBox.setVgrow(body, Priority.ALWAYS);
            ScrollPane scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("anime-details-scroll");
            VBox.setVgrow(scroll, Priority.ALWAYS);
            content.setFillWidth(true);
            tab.setClosable(true);
            tab.setContent(scroll);
        }
    }
}
