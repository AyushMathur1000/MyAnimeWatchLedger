package com.myanimewatchledger.app;

import com.myanimewatchledger.config.ApplicationPaths;
import com.myanimewatchledger.infrastructure.persistence.DatabaseManager;
import com.myanimewatchledger.infrastructure.persistence.DatabaseMigrationRunner;
import com.myanimewatchledger.infrastructure.persistence.PersistenceContext;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

/** Coordinates application resources before the primary JavaFX stage is shown. */
public final class ApplicationInitializer implements AutoCloseable {
 private static final Logger LOGGER=Logger.getLogger(ApplicationInitializer.class.getName());
 private DatabaseManager databaseManager;
 private PersistenceContext persistenceContext;

 public void initialize() throws IOException,SQLException {
  LOGGER.info("Initializing MyAnimeWatchLedger...");
  databaseManager=new DatabaseManager(ApplicationPaths.databaseFile());
  databaseManager.initialize();
  new DatabaseMigrationRunner(databaseManager).migrate();
  persistenceContext=new PersistenceContext(databaseManager);
  LOGGER.info("Application foundation initialized.");
 }

 public PersistenceContext persistence(){
  if(persistenceContext==null)throw new IllegalStateException("Application has not been initialized.");
  return persistenceContext;
 }

 @Override public void close(){
  persistenceContext=null;
  if(databaseManager!=null){databaseManager.close();databaseManager=null;}
  LOGGER.info("Application resources shut down.");
 }
}
