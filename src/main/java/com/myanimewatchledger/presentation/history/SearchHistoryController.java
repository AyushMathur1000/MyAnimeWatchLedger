package com.myanimewatchledger.presentation.history;
import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.domain.catalogue.*;
import java.util.*;
/** Presentation boundary for historical seasonal searches. */
public final class SearchHistoryController {
 private final ApplicationServices services;
 public SearchHistoryController(ApplicationServices services){this.services=Objects.requireNonNull(services);}
 public List<CatalogueSearch> searches(){return services.catalogue().getKnownSeasons();}
 public OpenCatalogueTab reopen(long searchId){return services.catalogueTabs().openSearch(searchId);}
}