package com.myanimewatchledger.app;
import com.myanimewatchledger.config.MalClientConfigurationStore;
import com.myanimewatchledger.domain.catalogue.Season;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class ApplicationInitializerTest {
 @TempDir Path temp;
 @Test void initializesWithoutMalCredentials() throws Exception {
  var store=new MalClientConfigurationStore(temp.resolve("config/app.properties"));
  try(var app=new ApplicationInitializer(temp.resolve("app.db"),store)){
   app.initialize();assertNotNull(app.persistence());assertNotNull(app.services());
   assertFalse(app.services().isMalConfigured());assertTrue(app.services().catalogue().getKnownSeasons().isEmpty());
   assertThrows(IllegalStateException.class,()->app.services().refreshSeasonAsync(Season.WINTER,2026));
  }
 }
 @Test void wiresMalWhenClientIdExists() throws Exception {
  var store=new MalClientConfigurationStore(temp.resolve("config/app.properties"));store.saveClientId("test-client-id");
  try(var app=new ApplicationInitializer(temp.resolve("with-mal.db"),store)){app.initialize();assertTrue(app.services().isMalConfigured());}
 }
 @Test void supportsReinitializationAfterClose() throws Exception {
  var store=new MalClientConfigurationStore(temp.resolve("config/app.properties"));
  try(var app=new ApplicationInitializer(temp.resolve("repeat.db"),store)){app.initialize();app.close();app.initialize();assertNotNull(app.services());}
 }
}
