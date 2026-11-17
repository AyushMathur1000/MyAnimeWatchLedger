package com.myanimewatchledger.presentation.ledger;

import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.domain.ledger.AnimePreference;
import com.myanimewatchledger.application.service.LedgerChangeEvent;
import com.myanimewatchledger.presentation.DarkDialogStyler;
import com.myanimewatchledger.presentation.feedback.SoundFeedback;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;
import java.util.Objects;
import java.util.function.LongConsumer;

/** Table-oriented My Ledger workspace. Row actions update entries directly. */
public final class LedgerView extends VBox {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private final LedgerController controller;
    private final LongConsumer onOpenDetails;
    private final Runnable onOpenCurrentSeason;
    private final ObservableList<LedgerEntry> source = FXCollections.observableArrayList();
    private final FilteredList<LedgerEntry> filtered = new FilteredList<>(source, entry -> true);
    private final TableView<LedgerEntry> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final Label countLabel = new Label();
    private final EnumSet<LedgerStatus> selectedStatuses = EnumSet.noneOf(LedgerStatus.class);
    private final java.util.List<CheckBox> statusChecks = new java.util.ArrayList<>();
    private final Label emptyLabel = new Label();
    private final Label feedbackLabel = new Label();
    private AutoCloseable ledgerSubscription;

    public LedgerView(LedgerController controller) {
        this(controller, malId -> { }, () -> { });
    }

    public LedgerView(LedgerController controller, LongConsumer onOpenDetails) {
        this(controller, onOpenDetails, () -> { });
    }

    public LedgerView(LedgerController controller, LongConsumer onOpenDetails, Runnable onOpenCurrentSeason) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.onOpenDetails = Objects.requireNonNull(onOpenDetails, "onOpenDetails");
        this.onOpenCurrentSeason = Objects.requireNonNull(onOpenCurrentSeason, "onOpenCurrentSeason");
        getStyleClass().add("ledger-workspace");
        setSpacing(14);
        setPadding(new Insets(18, 20, 18, 20));
        Label title = new Label("My Ledger");
        title.getStyleClass().add("workspace-title");
        Button currentSeason = new Button("Add anime…");
        currentSeason.getStyleClass().add("ledger-current-season-action");
        currentSeason.setTooltip(new Tooltip("Open the current season in Catalogue"));
        currentSeason.setAccessibleText("Open the current season in Catalogue");
        currentSeason.setOnAction(event -> this.onOpenCurrentSeason.run());
        HBox heading = new HBox(12, title, currentSeason, countLabel);
        heading.setAlignment(Pos.BASELINE_LEFT);
        searchField.setPromptText("Search anime titles…");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        HBox searchRow = new HBox(10, searchField, createStatusFilters());
        searchRow.setAlignment(Pos.CENTER_LEFT);
        configureTable();
        emptyLabel.getStyleClass().add("ledger-empty");
        feedbackLabel.getStyleClass().add("ledger-feedback");
        table.setPlaceholder(emptyLabel);
        table.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (selected != null && previous != selected && table.isFocused()) SoundFeedback.playClick();
        });
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(heading, searchRow, feedbackLabel, table);
        load();
        ledgerSubscription = controller.subscribeToLedgerChanges(this::ledgerChanged);
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) closeLedgerSubscription();
        });
    }

    private HBox createStatusFilters() {
        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label("Status:");
        label.getStyleClass().add("ledger-filter-label");
        filters.getChildren().add(label);
        for (LedgerStatus status : LedgerStatus.values()) {
            CheckBox check = new CheckBox(displayStatus(status));
            check.getStyleClass().add("ledger-status-filter");
            check.setOnAction(event -> {
                if (check.isSelected()) selectedStatuses.add(status);
                else selectedStatuses.remove(status);
                applyFilters();
            });
            statusChecks.add(check);
            filters.getChildren().add(check);
        }
        Button clear = new Button("Clear filters");
        clear.getStyleClass().add("ledger-clear-filter");
        clear.setOnAction(event -> clearFilters());
        filters.getChildren().add(clear);
        return filters;
    }

    private void configureTable() {
        table.setId("ledger-table");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("ledger-table");

        TableColumn<LedgerEntry, String> title = new TableColumn<>("Anime");
        title.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(data.getValue().title()));
        title.setComparator(String.CASE_INSENSITIVE_ORDER);
        title.setMinWidth(280);
        title.setPrefWidth(520);

        TableColumn<LedgerEntry, LedgerStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(data.getValue().status()));
        status.setComparator(java.util.Comparator.comparing(LedgerView::displayStatus, String.CASE_INSENSITIVE_ORDER));
        status.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<LedgerStatus> choice = statusCombo();
            {
                choice.setPrefWidth(132);
                choice.setMinWidth(132);
                choice.setMaxWidth(132);
                choice.setOnAction(event -> {
                    LedgerEntry entry = getTableRow() == null ? null : getTableRow().getItem();
                    LedgerStatus next = choice.getValue();
                    if (entry != null && next != null && next != entry.status()) updateStatus(entry, next);
                });
            }
            @Override protected void updateItem(LedgerStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else { choice.setValue(item); applyStatusStyle(choice, item); setGraphic(choice); }
            }
        });
        status.setMinWidth(155);
        status.setPrefWidth(175);

        TableColumn<LedgerEntry, String> preference = new TableColumn<>("Preference");
        preference.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(
                data.getValue().preference() == null ? "NONE" : data.getValue().preference().name()));
        preference.setCellFactory(column -> new TableCell<>() {
            private final Button like = new Button("👍");
            private final Button dislike = new Button("👎");
            private final HBox buttons = new HBox(4, like, dislike);
            {
                like.setTooltip(new Tooltip("Like this anime"));
                like.setAccessibleText("Like this anime");
                dislike.setTooltip(new Tooltip("Dislike this anime"));
                dislike.setAccessibleText("Dislike this anime");
                like.getStyleClass().add("ledger-preference-toggle");
                dislike.getStyleClass().add("ledger-preference-toggle");
                like.setOnAction(event -> {
                    LedgerEntry entry = getTableRow() == null ? null : getTableRow().getItem();
                    if (entry != null) updatePreference(entry, AnimePreference.LIKE);
                });
                dislike.setOnAction(event -> {
                    LedgerEntry entry = getTableRow() == null ? null : getTableRow().getItem();
                    if (entry != null) updatePreference(entry, AnimePreference.DISLIKE);
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                like.getStyleClass().remove("ledger-preference-active");
                dislike.getStyleClass().remove("ledger-preference-active");
                if ("LIKE".equals(item)) like.getStyleClass().add("ledger-preference-active");
                if ("DISLIKE".equals(item)) dislike.getStyleClass().add("ledger-preference-active");
                setGraphic(buttons);
            }
        });
        preference.setMinWidth(88);
        preference.setPrefWidth(94);

        TableColumn<LedgerEntry, String> priority = new TableColumn<>("Priority");
        priority.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(
                data.getValue().status() != LedgerStatus.WATCH_LATER ? "N/A"
                        : data.getValue().watchLaterPriority() == null ? "Unassigned"
                        : data.getValue().watchLaterPriority().toString()));
        priority.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<String> choice = new ComboBox<>();
            private boolean rendering;
            {
                choice.getItems().add("Unassigned");
                for (int i = 1; i <= 10; i++) choice.getItems().add(Integer.toString(i));
                choice.setPrefWidth(70);
                choice.setMinWidth(66);
                choice.setMaxWidth(74);
                choice.setOnAction(event -> {
                    if (rendering) return;
                    LedgerEntry entry = getTableRow() == null ? null : getTableRow().getItem();
                    if (entry != null && entry.status() == LedgerStatus.WATCH_LATER) {
                        updatePriority(entry, "Unassigned".equals(choice.getValue())
                                ? null : Integer.valueOf(choice.getValue()));
                    }
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else if ("N/A".equals(item)) {
                    setGraphic(new Label("—"));
                } else {
                    rendering = true;
                    choice.setValue(item);
                    rendering = false;
                    setGraphic(choice);
                }
            }
        });
        priority.setMinWidth(78);
        priority.setPrefWidth(82);
        priority.setMaxWidth(88);
        priority.setComparator((left, right) -> {
            if ("Unassigned".equals(left)) return "Unassigned".equals(right) ? 0 : 1;
            if ("Unassigned".equals(right)) return -1;
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        });
        priority.setMinWidth(105);

        TableColumn<LedgerEntry, java.time.Instant> added = dateColumn("Date added", LedgerEntry::dateAdded);
        TableColumn<LedgerEntry, java.time.Instant> modified = dateColumn("Last modified", LedgerEntry::lastModified);

        TableColumn<LedgerEntry, LedgerEntry> actions = new TableColumn<>("Actions");
        actions.setSortable(false);
        actions.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(data.getValue()));
        actions.setCellFactory(column -> new TableCell<>() {
            private final Button details = new Button("ⓘ");
            private final Button mal = new Button("↗");
            private final Button remove = new Button("×");
            private final HBox buttons = new HBox(5, details, mal, remove);
            {
                details.setOnAction(event -> {
                    LedgerEntry entry = getItem();
                    if (entry != null) onOpenDetails.accept(entry.malId());
                });
                details.getStyleClass().add("ledger-icon-action");
                details.setTooltip(new Tooltip("Open anime details"));
                details.setAccessibleText("Open anime details");
                mal.getStyleClass().add("ledger-icon-action");
                mal.setTooltip(new Tooltip("Open on MyAnimeList"));
                mal.setAccessibleText("Open on MyAnimeList");
                mal.setOnAction(event -> {
                    LedgerEntry entry = getItem();
                    if (entry != null) {
                        try {
                            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(
                                    java.net.URI.create("https://myanimelist.net/anime/" + entry.malId()));
                        } catch (Exception failure) {
                            showFeedback("Could not open the MyAnimeList page: " + safeMessage(new RuntimeException(failure)), true);
                        }
                    }
                });
                remove.setTooltip(new Tooltip("Remove from My Ledger"));
                remove.setAccessibleText("Remove from My Ledger");
                remove.getStyleClass().addAll("ledger-destructive-action", "ledger-icon-action");
                remove.setOnAction(event -> {
                    LedgerEntry entry = getItem();
                    if (entry != null) removeEntry(entry);
                });
            }
            @Override protected void updateItem(LedgerEntry item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : buttons);
            }
        });
        actions.setMinWidth(125);
        actions.setPrefWidth(135);
        actions.setMaxWidth(150);
        table.getColumns().setAll(title, status, preference, priority, added, modified, actions);
        modified.setSortType(TableColumn.SortType.DESCENDING);
        title.setSortType(TableColumn.SortType.ASCENDING);
        table.widthProperty().addListener((observable, oldWidth, newWidth) ->
                applyColumnWidths(newWidth.doubleValue(), title, status, preference, priority, added, modified, actions));
        table.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) javafx.application.Platform.runLater(() ->
                    applyColumnWidths(table.getWidth(), title, status, preference, priority, added, modified, actions));
        });
        SortedList<LedgerEntry> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSortOrder().setAll(modified, title);
    }

    private void applyColumnWidths(double tableWidth,
                                   TableColumn<LedgerEntry, String> title,
                                   TableColumn<LedgerEntry, LedgerStatus> status,
                                   TableColumn<LedgerEntry, String> preference,
                                   TableColumn<LedgerEntry, String> priority,
                                   TableColumn<LedgerEntry, java.time.Instant> added,
                                   TableColumn<LedgerEntry, java.time.Instant> modified,
                                   TableColumn<LedgerEntry, LedgerEntry> actions) {
        if (tableWidth <= 0) return;
        double usableWidth = Math.max(0, tableWidth - 18);
        title.setPrefWidth(usableWidth * 0.34);
        status.setPrefWidth(usableWidth * 0.11);
        preference.setPrefWidth(Math.min(94, usableWidth * 0.09));
        priority.setPrefWidth(Math.min(82, usableWidth * 0.07));
        added.setPrefWidth(usableWidth * 0.11);
        modified.setPrefWidth(usableWidth * 0.13);
        actions.setPrefWidth(Math.max(125, usableWidth * 0.10));
    }

    private ComboBox<LedgerStatus> statusCombo() {
        ComboBox<LedgerStatus> choice = new ComboBox<>(FXCollections.observableArrayList(LedgerStatus.values()));
        choice.getStyleClass().add("ledger-status-choice");
        choice.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(LedgerStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayStatus(item));
                getStyleClass().removeIf(style -> style.startsWith("status-"));
                if (!empty && item != null) getStyleClass().add(statusStyle(item));
            }
        });
        choice.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(LedgerStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayStatus(item));
                getStyleClass().removeIf(style -> style.startsWith("status-"));
                if (!empty && item != null) getStyleClass().add(statusStyle(item));
            }
        });
        choice.setConverter(new StringConverter<>() {
            @Override public String toString(LedgerStatus value) { return value == null ? "" : displayStatus(value); }
            @Override public LedgerStatus fromString(String value) { return null; }
        });
        return choice;
    }

    private static void applyStatusStyle(ComboBox<LedgerStatus> choice, LedgerStatus status) {
        choice.getStyleClass().removeIf(style -> style.startsWith("status-"));
        choice.getStyleClass().add(statusStyle(status));
    }

    private static String statusStyle(LedgerStatus status) {
        return "status-" + status.name().toLowerCase(java.util.Locale.ROOT);
    }

    private TableColumn<LedgerEntry, java.time.Instant> dateColumn(
            String heading, java.util.function.Function<LedgerEntry, java.time.Instant> extractor) {
        TableColumn<LedgerEntry, java.time.Instant> column = new TableColumn<>(heading);
        column.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(extractor.apply(data.getValue())));
        column.setComparator(java.util.Comparator.naturalOrder());
        column.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(java.time.Instant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DATE_FORMAT.format(item.atZone(ZoneId.systemDefault()).toLocalDateTime()));
            }
        });
        column.setMinWidth(175);
        column.setPrefWidth(205);
        return column;
    }

    private void ledgerChanged(LedgerChangeEvent event) {
        javafx.application.Platform.runLater(this::load);
    }

    private void closeLedgerSubscription() {
        if (ledgerSubscription == null) return;
        try { ledgerSubscription.close(); }
        catch (Exception ignored) { }
        ledgerSubscription = null;
    }

    private void load() {
        try {
            source.setAll(controller.loadEntries());
            feedbackLabel.setText("");
            applyFilters();
        } catch (RuntimeException exception) {
            source.clear();
            emptyLabel.setText("The ledger could not be loaded. Check the database and try again.");
            showFeedback("Database error: " + safeMessage(exception), true);
            updateCount();
        }
    }

    private void applyFilters() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        Set<LedgerStatus> statuses = Set.copyOf(selectedStatuses);
        filtered.setPredicate(entry -> (query.isEmpty() || entry.title().toLowerCase(java.util.Locale.ROOT).contains(query))
                && (statuses.isEmpty() || statuses.contains(entry.status())));
        emptyLabel.setText(source.isEmpty() ? "Your anime ledger is empty. Add anime from the Catalogue."
                : "No entries match the current search and filters.");
        updateCount();
    }

    private void clearFilters() {
        searchField.clear();
        selectedStatuses.clear();
        statusChecks.forEach(check -> check.setSelected(false));
        applyFilters();
    }

    private void updateStatus(LedgerEntry entry, LedgerStatus next) {
        try {
            if (!controller.changeStatus(entry.malId(), next)) {
                showFeedback("The status could not be changed. The entry may have been modified elsewhere.", true);
                table.refresh();
                return;
            }
            LedgerEntry updated = controller.find(entry.malId()).orElseThrow();
            int index = source.indexOf(entry);
            if (index >= 0) source.set(index, updated);
            showFeedback("Status updated to " + displayStatus(updated.status()) + ".", false);
            applyFilters();
        } catch (RuntimeException exception) {
            showFeedback("Could not change status: " + safeMessage(exception), true);
            table.refresh();
        }
    }

    private void updatePreference(LedgerEntry entry, AnimePreference preference) {
        try {
            if (!controller.changePreference(entry.malId(), preference)) {
                showFeedback("The preference could not be changed.", true);
                table.refresh();
                return;
            }
            int index = source.indexOf(entry);\n            if (index >= 0) source.set(index, controller.find(entry.malId()).orElseThrow());
            showFeedback("Anime preference updated.", false);
        } catch (RuntimeException failure) {
            showFeedback("Could not update preference: " + safeMessage(failure), true);
            table.refresh();
        }
    }

    private void updatePriority(LedgerEntry entry, Integer priority) {
        try {
            if (!controller.changeWatchLaterPriority(entry.malId(), priority)) {
                showFeedback("Priority can only be assigned to Watch Later entries.", true);
                table.refresh();
                return;
            }
            source.set(source.indexOf(entry), controller.find(entry.malId()).orElseThrow());
            showFeedback(priority == null ? "Watch Later priority cleared." : "Watch Later priority set to " + priority + ".", false);
        } catch (RuntimeException failure) {
            showFeedback("Could not update priority: " + safeMessage(failure), true);
            table.refresh();
        }
    }

    private void removeEntry(LedgerEntry entry) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Remove anime");
        confirmation.setHeaderText("Remove “" + entry.title() + "” from your ledger?");
        confirmation.setContentText("This permanently removes the ledger record. It does not delete catalogue cache data.");
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        DarkDialogStyler.apply(confirmation);
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
            try {
                if (controller.remove(entry.malId())) {
                    source.remove(entry);
                    showFeedback("Removed “" + entry.title() + "” from your ledger.", false);
                    applyFilters();
                } else showFeedback("The entry was not found and could not be removed.", true);
            } catch (RuntimeException exception) {
                showFeedback("Could not remove entry: " + safeMessage(exception), true);
            }
        });
    }

    private void updateCount() {
        countLabel.setText(source.size() == filtered.size() ? source.size() + (source.size() == 1 ? " entry" : " entries")
                : filtered.size() + " of " + source.size() + " entries");
    }
    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("feedback-error", "feedback-success");
        feedbackLabel.getStyleClass().add(error ? "feedback-error" : "feedback-success");
    }
    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }
    static String displayStatus(LedgerStatus status) {
        String lower = status.name().toLowerCase().replace('_', ' ');
        return java.util.Arrays.stream(lower.split(" "))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
