package com.myanimewatchledger.presentation.history;

import com.myanimewatchledger.domain.catalogue.CatalogueSearch;
import com.myanimewatchledger.domain.catalogue.FetchStatus;
import com.myanimewatchledger.domain.catalogue.Season;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.LongConsumer;

/** Sortable history of cached seasonal catalogue searches. */
public final class SearchHistoryView extends BorderPane {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault());

    private final SearchHistoryController controller;
    private final LongConsumer onReopen;
    private final TableView<CatalogueSearch> table = new TableView<>();
    private final Label feedback = new Label();
    private final Button reload = new Button("↻");
    private final ProgressIndicator progress = new ProgressIndicator();
    private long reloadGeneration;

    public SearchHistoryView(SearchHistoryController controller, LongConsumer onReopen) {
        this.controller = Objects.requireNonNull(controller);
        this.onReopen = Objects.requireNonNull(onReopen);
        getStyleClass().add("history-workspace");
        setPadding(new Insets(22, 26, 24, 26));

        Label title = new Label("Search History");
        title.getStyleClass().add("workspace-title");
        Label subtitle = new Label(
                "Previously fetched seasonal catalogues remain available from your local cache.");
        subtitle.getStyleClass().add("history-subtitle");

        reload.setTooltip(new Tooltip("Reload Search History from the local database"));
        reload.setAccessibleText("Refresh Search History");
        reload.setOnAction(event -> reload());
        progress.setPrefSize(20, 20);
        progress.setVisible(false);
        progress.setManaged(false);

        HBox heading = new HBox(12, title, reload, progress);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox top = new VBox(5, heading, subtitle);
        top.setPadding(new Insets(0, 0, 16, 0));
        setTop(top);

        configureTable();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("history-table");
        table.setPlaceholder(new Label("No seasonal searches have been recorded yet."));
        setCenter(table);

        feedback.getStyleClass().add("history-feedback");
        BorderPane.setMargin(feedback, new Insets(12, 0, 0, 0));
        setBottom(feedback);
        reload();
    }

    private void configureTable() {
        TableColumn<CatalogueSearch, Season> season = new TableColumn<>("Season");
        season.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().season()));
        season.setComparator(Comparator.comparingInt(Enum::ordinal));
        season.setPrefWidth(130);

        TableColumn<CatalogueSearch, Integer> year = new TableColumn<>("Year");
        year.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().year()));
        year.setPrefWidth(90);

        TableColumn<CatalogueSearch, Instant> fetched = new TableColumn<>("Last fetched");
        fetched.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().lastSuccessfulFetchAt()));
        fetched.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        fetched.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Instant value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value == null ? "Never successfully fetched"
                        : FORMAT.format(value.atZone(ZoneId.systemDefault())));
            }
        });
        fetched.setPrefWidth(210);

        TableColumn<CatalogueSearch, FetchStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().fetchStatus()));
        status.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(FetchStatus value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : switch (value) {
                    case SUCCESS -> "Successful";
                    case FAILED -> "Latest attempt failed";
                    case NEVER_FETCHED -> "Not fetched";
                });
                getStyleClass().removeAll(
                        "history-status-success", "history-status-failed", "history-status-neutral");
                if (!empty && value != null) {
                    getStyleClass().add(switch (value) {
                        case SUCCESS -> "history-status-success";
                        case FAILED -> "history-status-failed";
                        case NEVER_FETCHED -> "history-status-neutral";
                    });
                }
            }
        });
        status.setPrefWidth(155);

        TableColumn<CatalogueSearch, CatalogueSearch> action = new TableColumn<>("Action");
        action.setSortable(false);
        action.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        action.setCellFactory(column -> new TableCell<>() {
            private final Button button = new Button("Reopen catalogue");
            {
                button.setOnAction(event -> {
                    CatalogueSearch search = getItem();
                    if (search != null) SearchHistoryView.this.onReopen.accept(search.searchId());
                });
            }
            @Override
            protected void updateItem(CatalogueSearch search, boolean empty) {
                super.updateItem(search, empty);
                setGraphic(empty || search == null ? null : button);
            }
        });
        action.setPrefWidth(170);

        table.getColumns().setAll(season, year, fetched, status, action);
        table.getSortOrder().setAll(fetched);
        fetched.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void reload() {
        long request = ++reloadGeneration;
        reload.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        feedback.setText("Refreshing Search History…");

        controller.searchesAsync().whenComplete((searches, failure) -> Platform.runLater(() -> {
            if (request != reloadGeneration) return;
            reload.setDisable(false);
            progress.setVisible(false);
            progress.setManaged(false);
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause() : failure;
                feedback.setText("Could not refresh Search History: " + safeMessage(cause)
                        + " Existing results have been preserved.");
                return;
            }
            table.setItems(FXCollections.observableArrayList(searches));
            feedback.setText(searches.size()
                    + " seasonal search(es) · sorted by last successful fetch");
        }));
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
