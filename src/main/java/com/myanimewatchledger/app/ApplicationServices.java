package com.myanimewatchledger.app;
import com.myanimewatchledger.application.service.*;
import com.myanimewatchledger.domain.catalogue.Season;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
/** Application-level service graph exposed to the presentation layer. */
public final class ApplicationServices {
 private final LedgerService ledger; private final CatalogueService catalogue;
 private final CatalogueTabService tabs; private final CatalogueRefreshService refresh;
 private final BackgroundTaskExecutor background;
 ApplicationServices(LedgerService l,CatalogueService c,CatalogueTabService t,CatalogueRefreshService r,BackgroundTaskExecutor b){
  ledger=Objects.requireNonNull(l);catalogue=Objects.requireNonNull(c);tabs=Objects.requireNonNull(t);refresh=r;background=Objects.requireNonNull(b);
 }
 public LedgerService ledger(){return ledger;} public CatalogueService catalogue(){return catalogue;}
 public CatalogueTabService catalogueTabs(){return tabs;} public boolean isMalConfigured(){return refresh!=null;}
 /** Completion executes on a worker; presentation code must marshal UI changes to JavaFX. */
 public CompletableFuture<CatalogueRefreshOutcome> refreshSeasonAsync(Season season,int year){
  if(refresh==null)throw new IllegalStateException("Configure a MAL Client ID before refreshing a catalogue.");
  return background.submit(()->refresh.refresh(season,year));
 }
}
