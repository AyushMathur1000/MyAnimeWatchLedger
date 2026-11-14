package com.myanimewatchledger.app;

import com.myanimewatchledger.application.service.*;
import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.domain.catalogue.CatalogueAnime;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.domain.catalogue.Season;
import com.myanimewatchledger.domain.ledger.LedgerEntry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Application-level service graph exposed to the presentation layer. */
public final class ApplicationServices {
 private final LedgerService ledger;
 private final CatalogueService catalogue;
 private final CatalogueTabService tabs;
 private final CatalogueRefreshService refresh;
 private final AnimeDetailService animeDetails;
 private final AnimeDetailTabService animeDetailTabs;
 private final BackgroundTaskExecutor background;

 ApplicationServices(LedgerService l,CatalogueService c,CatalogueTabService t,
                     CatalogueRefreshService r,AnimeDetailService d,AnimeDetailTabService detailTabs,
                     BackgroundTaskExecutor b){
  ledger=Objects.requireNonNull(l);catalogue=Objects.requireNonNull(c);tabs=Objects.requireNonNull(t);
  refresh=r;animeDetails=Objects.requireNonNull(d);animeDetailTabs=Objects.requireNonNull(detailTabs);
  background=Objects.requireNonNull(b);
 }
 public LedgerService ledger(){return ledger;}
 public CatalogueService catalogue(){return catalogue;}
 public CatalogueTabService catalogueTabs(){return tabs;}
 public AnimeDetailService animeDetails(){return animeDetails;}
 public AnimeDetailTabService animeDetailTabs(){return animeDetailTabs;}
 public boolean isMalConfigured(){return refresh!=null;}
 /** Completion executes on a worker; presentation code must marshal UI changes to JavaFX. */
 public CompletableFuture<CatalogueRefreshOutcome> refreshSeasonAsync(Season season,int year){
  if(refresh==null)throw new IllegalStateException("Configure a MAL Client ID before refreshing a catalogue.");
  return background.submit(()->refresh.refresh(season,year));
 }
 /** All database and MAL I/O for details executes on a worker thread. */
 public CompletableFuture<AnimeDetailResult> loadAnimeDetailsAsync(long malId){
  return background.submit(()->animeDetails.load(malId));
 }
 /** Persistent detail-tab reads and writes are deliberately kept off the JavaFX thread. */
 public CompletableFuture<List<OpenAnimeDetailTab>> restoreAnimeDetailTabsAsync(){
  return background.submit(animeDetailTabs::restoreOpenTabs);
 }
 public CompletableFuture<OpenAnimeDetailTab> openAnimeDetailTabAsync(long malId){
  return background.submit(()->animeDetailTabs.open(malId));
 }
 public CompletableFuture<Boolean> closeAnimeDetailTabAsync(long malId){
  return background.submit(()->animeDetailTabs.close(malId));
 }
 public CompletableFuture<Void> reorderAnimeDetailTabsAsync(List<Long> malIds){
  List<Long> order=List.copyOf(malIds);
  return background.submit(()->{animeDetailTabs.reorder(order);return null;});
 }
 public CompletableFuture<Optional<LedgerEntry>> findLedgerEntryAsync(long malId){
  return background.submit(()->ledger.find(malId));
 }
 /** Adds cached anime metadata to the ledger using the status selected in the detail workspace. */
 public CompletableFuture<LedgerEntry> addAnimeToLedgerAsync(AnimeMetadataCache cache,LedgerStatus status){
  Objects.requireNonNull(cache);Objects.requireNonNull(status);
  return background.submit(()->ledger.add(new CatalogueAnime(cache.malId(),cache.preferredTitle(),
   cache.metadataJson(),cache.searchableText(),cache.cachedAt(),0,null),status));
 }
 /** Changes an existing ledger entry's status without blocking the JavaFX thread. */
 public CompletableFuture<LedgerEntry> changeLedgerStatusAsync(long malId,LedgerStatus status){
  Objects.requireNonNull(status);
  return background.submit(()->{
   if(!ledger.changeStatus(malId,status)){
    LedgerEntry current=ledger.find(malId).orElseThrow(()->new IllegalStateException("Anime is no longer in the ledger."));
    if(current.status()!=status)throw new IllegalStateException("The ledger status could not be updated.");
    return current;
   }
   return ledger.find(malId).orElseThrow(()->new IllegalStateException("Updated ledger entry could not be found."));
  });
 }
}
