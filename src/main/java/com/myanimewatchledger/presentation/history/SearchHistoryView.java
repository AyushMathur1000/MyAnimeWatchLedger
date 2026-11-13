package com.myanimewatchledger.presentation.history;
import com.myanimewatchledger.domain.catalogue.*;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.LongConsumer;
/** Sortable history of cached seasonal catalogue searches. */
public final class SearchHistoryView extends BorderPane {
 private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm",Locale.getDefault());
 private final SearchHistoryController controller; private final LongConsumer onReopen;
 private final TableView<CatalogueSearch> table=new TableView<>(); private final Label feedback=new Label();
 public SearchHistoryView(SearchHistoryController controller,LongConsumer onReopen){
  this.controller=Objects.requireNonNull(controller);this.onReopen=Objects.requireNonNull(onReopen);getStyleClass().add("history-workspace");setPadding(new Insets(22,26,24,26));
  Label title=new Label("Search History");title.getStyleClass().add("workspace-title");
  Label subtitle=new Label("Previously fetched seasonal catalogues remain available from your local cache.");subtitle.getStyleClass().add("history-subtitle");
  Button reload=new Button("↻ Refresh list");reload.setOnAction(e->reload());
  HBox heading=new HBox(14,title,reload);heading.setAlignment(Pos.CENTER_LEFT);VBox top=new VBox(5,heading,subtitle);top.setPadding(new Insets(0,0,16,0));setTop(top);
  configureTable();table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.getStyleClass().add("history-table");table.setPlaceholder(new Label("No seasonal searches have been recorded yet."));setCenter(table);
  feedback.getStyleClass().add("history-feedback");BorderPane.setMargin(feedback,new Insets(12,0,0,0));setBottom(feedback);reload();
 }
 private void configureTable(){
  TableColumn<CatalogueSearch,Season> season=new TableColumn<>("Season");season.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue().season()));season.setComparator(Comparator.comparingInt(Enum::ordinal));season.setPrefWidth(130);
  TableColumn<CatalogueSearch,Integer> year=new TableColumn<>("Year");year.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue().year()));year.setPrefWidth(90);
  TableColumn<CatalogueSearch,Instant> fetched=new TableColumn<>("Last fetched");fetched.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue().lastSuccessfulFetchAt()));fetched.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));fetched.setCellFactory(col->new TableCell<>(){
   @Override protected void updateItem(Instant value,boolean empty){super.updateItem(value,empty);setText(empty?null:value==null?"Never successfully fetched":FORMAT.format(value.atZone(ZoneId.systemDefault())));}
  });fetched.setPrefWidth(210);
  TableColumn<CatalogueSearch,FetchStatus> status=new TableColumn<>("Status");status.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue().fetchStatus()));status.setCellFactory(col->new TableCell<>(){
   @Override protected void updateItem(FetchStatus value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?null:switch(value){case SUCCESS->"Successful";case FAILED->"Latest attempt failed";case NEVER_FETCHED->"Not fetched";});getStyleClass().removeAll("history-status-success","history-status-failed","history-status-neutral");if(!empty&&value!=null)getStyleClass().add(switch(value){case SUCCESS->"history-status-success";case FAILED->"history-status-failed";case NEVER_FETCHED->"history-status-neutral";});}
  });status.setPrefWidth(155);
  TableColumn<CatalogueSearch,CatalogueSearch> action=new TableColumn<>("Action");action.setSortable(false);action.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));action.setCellFactory(col->new TableCell<>(){
   private final Button button=new Button("Reopen catalogue");{button.setOnAction(e->{CatalogueSearch s=getItem();if(s!=null)SearchHistoryView.this.onReopen.accept(s.searchId());});}
   @Override protected void updateItem(CatalogueSearch s,boolean empty){super.updateItem(s,empty);setGraphic(empty||s==null?null:button);}
  });action.setPrefWidth(170);
  table.getColumns().setAll(season,year,fetched,status,action);table.getSortOrder().setAll(fetched);fetched.setSortType(TableColumn.SortType.DESCENDING);
 }
 private void reload(){try{List<CatalogueSearch> searches=controller.searches();table.setItems(FXCollections.observableArrayList(searches));feedback.setText(searches.size()+" seasonal search(es) · sorted by last successful fetch");}catch(RuntimeException e){table.setItems(FXCollections.observableArrayList());feedback.setText("Could not load Search History: "+safeMessage(e));}}
 private static String safeMessage(Throwable e){return e.getMessage()==null||e.getMessage().isBlank()?e.getClass().getSimpleName():e.getMessage();}
}