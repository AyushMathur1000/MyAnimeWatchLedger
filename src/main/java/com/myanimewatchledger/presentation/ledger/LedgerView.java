package com.myanimewatchledger.presentation.ledger;

import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/** Table-oriented My Ledger workspace. All persistence is delegated to LedgerController. */
public final class LedgerView extends VBox {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private final LedgerController controller;
    private final ObservableList<LedgerEntry> source = FXCollections.observableArrayList();
    private final FilteredList<LedgerEntry> filtered = new FilteredList<>(source, entry -> true);
    private final TableView<LedgerEntry> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final Label countLabel = new Label();
    private final ComboBox<LedgerStatus> statusChoice = new ComboBox<>();
    private final Button applyStatusButton = new Button("Apply status");
    private final Button removeButton = new Button("Remove");
    private final EnumSet<LedgerStatus> selectedStatuses = EnumSet.noneOf(LedgerStatus.class);
    private final Label emptyLabel = new Label();
    private final Label feedbackLabel = new Label();

    public LedgerView(LedgerController controller) {
        this.controller = controller;
        getStyleClass().add("ledger-workspace");
        setSpacing(14);
        setPadding(new Insets(26));

        Label title = new Label("My Ledger");
        title.getStyleClass().add("workspace-title");
        HBox heading = new HBox(14, title, countLabel);
        heading.setAlignment(Pos.BASELINE_LEFT);

        searchField.setPromptText("Search anime titles…");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        HBox searchRow = new HBox(12, searchField, createStatusFilters());
        searchRow.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        emptyLabel.getStyleClass().add("ledger-empty");
        feedbackLabel.getStyleClass().add("ledger-feedback");
        table.setPlaceholder(emptyLabel);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(heading, searchRow, createRowActions(), feedbackLabel, table);
        load();
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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("ledger-table");

        TableColumn<LedgerEntry, String> title = new TableColumn<>("Anime");
        title.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyStringWrapper(data.getValue().title()));
        title.setComparator(String.CASE_INSENSITIVE_ORDER);
        title.setMinWidth(220);

        TableColumn<LedgerEntry, LedgerStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(data.getValue().status()));
        status.setComparator(java.util.Comparator.comparing(LedgerView::displayStatus,
                String.CASE_INSENSITIVE_ORDER));
        status.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(LedgerStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayStatus(item));
                getStyleClass().removeIf(style -> style.startsWith("status-"));
                if (!empty && item != null) getStyleClass().add("status-" + item.name().toLowerCase());
            }
        });
        status.setMinWidth(125);
        status.setMaxWidth(160);

        TableColumn<LedgerEntry, java.time.Instant> added =
                dateColumn("Date added", LedgerEntry::dateAdded);
        TableColumn<LedgerEntry, java.time.Instant> modified =
                dateColumn("Last modified", LedgerEntry::lastModified);
        table.getColumns().setAll(title, status, added, modified);

        SortedList<LedgerEntry> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSortOrder().add(title);
    }

    private TableColumn<LedgerEntry, java.time.Instant> dateColumn(
            String heading, java.util.function.Function<LedgerEntry, java.time.Instant> extractor) {
        TableColumn<LedgerEntry, java.time.Instant> column = new TableColumn<>(heading);
        column.setCellValueFactory(data ->
                new javafx.beans.property.ReadOnlyObjectWrapper<>(extractor.apply(data.getValue())));
        column.setComparator(java.util.Comparator.naturalOrder());
        column.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(java.time.Instant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DATE_FORMAT.format(
                        item.atZone(ZoneId.systemDefault()).toLocalDateTime()));
            }
        });
        column.setMinWidth(165);
        return column;
    }

    private HBox createRowActions() {
        statusChoice.getItems().setAll(LedgerStatus.values());
        statusChoice.setPromptText("Choose status");
        statusChoice.setConverter(new StringConverter<>() {
            @Override public String toString(LedgerStatus value) {
                return value == null ? "" : displayStatus(value);
            }
            @Override public LedgerStatus fromString(String value) { return null; }
        });
        statusChoice.setDisable(true);
        applyStatusButton.setDisable(true);
        removeButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
            boolean hasSelection = selected != null;
            statusChoice.setDisable(!hasSelection);
            applyStatusButton.setDisable(!hasSelection);
            removeButton.setDisable(!hasSelection);
            if (selected != null) statusChoice.setValue(selected.status());
            else statusChoice.setValue(null);
        });
        applyStatusButton.setOnAction(event -> changeSelectedStatus());
        removeButton.setOnAction(event -> removeSelected());
        removeButton.getStyleClass().add("ledger-destructive-action");
        Label label = new Label("Selected entry:");
        label.getStyleClass().add("ledger-filter-label");
        HBox row = new HBox(10, label, statusChoice, applyStatusButton, removeButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void load() {
        try {
            source.setAll(controller.loadEntries());
            feedbackLabel.setText("");
            applyFilters();
        } catch (RuntimeException exception) {
            source.clear();
            emptyLabel.setText("The ledger could not be loaded. Check the database and try again.");
            feedbackLabel.setText("Database error: " + safeMessage(exception));
            updateCount();
        }
    }

    private void applyFilters() {
        filtered.setPredicate(entry -> LedgerFilter.apply(
                java.util.List.of(entry), searchField.getText(), Set.copyOf(selectedStatuses)).size() == 1);
        emptyLabel.setText(source.isEmpty()
                ? "Your anime ledger is empty. Add anime from the Catalogue."
                : "No entries match the current search and filters.");
        updateCount();
    }

    private void clearFilters() {
        searchField.clear();
        selectedStatuses.clear();
        getStatusChecks().forEach(check -> check.setSelected(false));
        applyFilters();
    }

    private java.util.List<CheckBox> getStatusChecks() {
        return ((HBox) getChildren().get(1)).getChildren().stream()
                .filter(CheckBox.class::isInstance).map(CheckBox.class::cast).toList();
    }

    private void changeSelectedStatus() {
        LedgerEntry selected = table.getSelectionModel().getSelectedItem();
        LedgerStatus next = statusChoice.getValue();
        if (selected == null || next == null || selected.status() == next) return;
        try {
            if (!controller.changeStatus(selected.malId(), next)) {
                showFeedback("The status could not be changed. The entry may have been modified elsewhere.", true);
                return;
            }
            LedgerEntry updated = controller.find(selected.malId()).orElseThrow(
                    () -> new IllegalStateException("The updated ledger entry could not be reloaded."));
            int index = source.indexOf(selected);
            if (index >= 0) source.set(index, updated);
            table.getSelectionModel().select(updated);
            statusChoice.setValue(updated.status());
            showFeedback("Status updated to " + displayStatus(updated.status()) + ".", false);
            applyFilters();
        } catch (RuntimeException exception) {
            showFeedback("Could not change status: " + safeMessage(exception), true);
        }
    }

    private void removeSelected() {
        LedgerEntry selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Remove anime");
        confirmation.setHeaderText("Remove “" + selected.title() + "” from your ledger?");
        confirmation.setContentText("This permanently removes the ledger record. It does not delete catalogue cache data.");
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
            try {
                if (controller.remove(selected.malId())) {
                    source.remove(selected);
                    showFeedback("Removed “" + selected.title() + "” from your ledger.", false);
                    applyFilters();
                } else {
                    showFeedback("The entry was not found and could not be removed.", true);
                }
            } catch (RuntimeException exception) {
                showFeedback("Could not remove entry: " + safeMessage(exception), true);
            }
        });
    }

    private void updateCount() {
        countLabel.setText(source.size() == filtered.size()
                ? source.size() + (source.size() == 1 ? " entry" : " entries")
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
