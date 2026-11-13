package com.myanimewatchledger.app;
import com.myanimewatchledger.application.service.*;
import com.myanimewatchledger.config.*;
import com.myanimewatchledger.infrastructure.mal.*;
import com.myanimewatchledger.infrastructure.persistence.*;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.*;
/** Coordinates startup resources and application service wiring. */
public final class ApplicationInitializer implements AutoCloseable {
 private static final Logger LOG=Logger.getLogger(ApplicationInitializer.class.getName());
 private final Path databaseFile; private final MalClientConfigurationStore configStore;
 private DatabaseManager database; private PersistenceContext persistence;
 private BackgroundTaskExecutor background; private ApplicationServices services;
 public ApplicationInitializer(){this(ApplicationPaths.databaseFile(),new MalClientConfigurationStore());}
 ApplicationInitializer(Path db,MalClientConfigurationStore config){databaseFile=db;configStore=config;}
 public synchronized void initialize() throws IOException,SQLException{
  if(services!=null)return; LOG.info("Initializing MyAnimeWatchLedger...");
  database=new DatabaseManager(databaseFile);
  try{
   database.initialize();new DatabaseMigrationRunner(database).migrate();persistence=new PersistenceContext(database);
   background=new BackgroundTaskExecutor(2);
   LedgerService ledger=new LedgerService(persistence.ledger());
   CatalogueService catalogue=new CatalogueService(persistence.catalogue());
   CatalogueTabService tabs=new CatalogueTabService(persistence.catalogue(),persistence.openCatalogueTabs());
   CatalogueRefreshService refresh=createRefreshService();
   services=new ApplicationServices(ledger,catalogue,tabs,refresh,background);
   LOG.info("Application services initialized. MAL configured: "+services.isMalConfigured());
  }catch(IOException|SQLException|RuntimeException failure){close();throw failure;}
 }
 private CatalogueRefreshService createRefreshService() throws IOException {
  try {
   var id = configStore.loadClientId();
   if (id.isEmpty()) {
    LOG.info("MAL Client ID is not configured; online catalogue refresh is unavailable.");
    return null;
   }
   MalApiConfiguration cfg = MalApiConfiguration.defaults(id.orElseThrow());
   MalApiClient client = new MalApiClient(cfg);
   MalResponseMapper mapper = new MalResponseMapper();
   return new CatalogueRefreshService(new MalApiCatalogueGateway(client, client.baseUri()),
    persistence.catalogue(), persistence.animeMetadata(), mapper,
    persistence.ledger(), persistence.transactions());
  } catch (IllegalArgumentException invalid) {
   LOG.log(Level.WARNING, "MAL configuration is invalid; online catalogue refresh is unavailable.", invalid);
   return null;
  }
 }

 public synchronized PersistenceContext persistence(){if(persistence==null)throw new IllegalStateException("Application has not been initialized.");return persistence;}
 public synchronized ApplicationServices services(){if(services==null)throw new IllegalStateException("Application has not been initialized.");return services;}
 @Override public synchronized void close(){
  services=null;persistence=null;
  try {
   if (background != null) { background.close(); background = null; }
  } finally {
   if (database != null) { database.close(); database = null; }
  }
  LOG.info("Application resources shut down.");
 }
}
