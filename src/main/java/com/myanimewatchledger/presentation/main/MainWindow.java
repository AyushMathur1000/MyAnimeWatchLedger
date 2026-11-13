package com.myanimewatchledger.presentation.main;
import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.presentation.ledger.*;
import com.myanimewatchledger.presentation.catalogue.*;
import com.myanimewatchledger.presentation.history.*;
import com.myanimewatchledger.presentation.navigation.NavigationSidebar;
import com.myanimewatchledger.presentation.state.*;
import com.myanimewatchledger.presentation.status.ApplicationStatusBar;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.*;
/** Main application shell with persistent global regions and replaceable workspace. */
public final class MainWindow {
 private static final Logger LOGGER=Logger.getLogger(MainWindow.class.getName());
 private final ApplicationServices services;private final WindowStateStore windowStateStore;private final BorderPane root=new BorderPane();private final NavigationSidebar sidebar=new NavigationSidebar(this::navigate);
 private MainNavigation activeNavigation=MainNavigation.MY_LEDGER;private double lastWindowedWidth=WindowState.defaults().width(),lastWindowedHeight=WindowState.defaults().height();
 public MainWindow(ApplicationServices services){this(services,new WindowStateStore(com.myanimewatchledger.config.ApplicationPaths.dataDirectory().resolve("config").resolve("ui.properties")));}
 public MainWindow(ApplicationServices services,WindowStateStore store){this.services=Objects.requireNonNull(services);windowStateStore=Objects.requireNonNull(store);}
 public void show(Stage stage){
  root.getStyleClass().add("app-root");root.setLeft(sidebar);root.setTop(createHeader());root.setBottom(new ApplicationStatusBar(services.isMalConfigured()));Scene scene=new Scene(root);
  var stylesheet=getClass().getResource("/styles/application.css");if(stylesheet!=null)scene.getStylesheets().add(stylesheet.toExternalForm());
  WindowState saved=windowStateStore.load();activeNavigation=saved.lastNavigation();sidebar.select(activeNavigation);renderWorkspace();lastWindowedWidth=saved.width();lastWindowedHeight=saved.height();
  stage.setTitle("MyAnimeWatchLedger");stage.setMinWidth(800);stage.setMinHeight(560);stage.setWidth(saved.width());stage.setHeight(saved.height());
  stage.widthProperty().addListener((o,old,v)->{if(!stage.isMaximized()&&v.doubleValue()>=800)lastWindowedWidth=v.doubleValue();});
  stage.heightProperty().addListener((o,old,v)->{if(!stage.isMaximized()&&v.doubleValue()>=560)lastWindowedHeight=v.doubleValue();});
  stage.setScene(scene);stage.setOnHiding(e->saveWindowState(stage));stage.show();stage.setMaximized(saved.maximized());
 }
 private HBox createHeader(){Label title=new Label("MyAnimeWatchLedger");title.getStyleClass().add("app-title");HBox header=new HBox(title);header.setAlignment(Pos.CENTER_LEFT);header.setPadding(new Insets(12,18,10,18));header.getStyleClass().add("application-header");return header;}
 private void navigate(MainNavigation navigation){if(activeNavigation==navigation)return;activeNavigation=navigation;sidebar.select(navigation);renderWorkspace();}
 private void openHistoricalSearch(long id){services.catalogueTabs().openSearch(id);navigate(MainNavigation.CATALOGUE);}
 private void renderWorkspace(){
  if(activeNavigation==MainNavigation.CATALOGUE){root.setCenter(new CatalogueView(new CatalogueController(services)));return;}
  if(activeNavigation==MainNavigation.MY_LEDGER){root.setCenter(new LedgerView(new LedgerController(services.ledger())));return;}
  if(activeNavigation==MainNavigation.SEARCH_HISTORY){root.setCenter(new SearchHistoryView(new SearchHistoryController(services),this::openHistoricalSearch));return;}
  Label title=new Label(activeNavigation.label());title.getStyleClass().add("workspace-title");Label message=new Label(activeNavigation.label()+" workspace will be available in its implementation milestone.");message.getStyleClass().add("workspace-message");VBox workspace=new VBox(10,title,message);workspace.setAlignment(Pos.TOP_LEFT);workspace.setPadding(new Insets(26));workspace.getStyleClass().add("main-workspace");root.setCenter(workspace);
 }
 private void saveWindowState(Stage stage){try{windowStateStore.save(new WindowState(lastWindowedWidth,lastWindowedHeight,stage.isMaximized(),activeNavigation));}catch(IOException|IllegalArgumentException e){LOGGER.log(Level.WARNING,"Could not save main window preferences.",e);}}
}