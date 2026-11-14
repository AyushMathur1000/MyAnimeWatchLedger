package com.myanimewatchledger.presentation.history;
import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.domain.catalogue.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Presentation boundary for historical seasonal searches. */
public final class SearchHistoryController {
 private final ApplicationServices services;
 public SearchHistoryController(ApplicationServices services){this.services=Objects.requireNonNull(services);}
 public List<CatalogueSearch> searches(){return services.catalogue().getKnownSeasons();}
 public CompletableFuture<List<CatalogueSearch>> searchesAsync(){return services.knownCatalogueSeasonsAsync();}
 public OpenCatalogueTab reopen(long searchId){return services.catalogueTabs().openSearch(searchId);}
}