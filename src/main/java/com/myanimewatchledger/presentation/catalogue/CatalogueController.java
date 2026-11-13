package com.myanimewatchledger.presentation.catalogue;

import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.application.service.CatalogueRefreshOutcome;
import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Thin presentation boundary for seasonal catalogue and ledger actions. */
public final class CatalogueController {
    private final ApplicationServices services;

    public CatalogueController(ApplicationServices services) { this.services = services; }

    public List<OpenCatalogueTab> restoreTabs() { return services.catalogueTabs().restoreOpenTabs(); }
    public OpenCatalogueTab open(Season season, int year, String query) {
        return services.catalogueTabs().open(season, year, query);
    }
    public boolean close(long id) { return services.catalogueTabs().close(id); }
    public boolean updateQuery(long id, String query) { return services.catalogueTabs().updateQuery(id, query); }
    public Optional<CatalogueSearch> findSearch(long id) { return services.catalogue().findSeason(id); }
    public List<CatalogueAnime> loadAnime(long id, String query) { return services.catalogue().getAnime(id, query); }
    public CompletableFuture<CatalogueRefreshOutcome> refresh(Season season, int year) {
        return services.refreshSeasonAsync(season, year);
    }
    public boolean isMalConfigured() { return services.isMalConfigured(); }
    public Optional<LedgerEntry> findLedgerEntry(long malId) { return services.ledger().find(malId); }
    public LedgerEntry add(CatalogueAnime anime, LedgerStatus status) { return services.ledger().add(anime, status); }
    public boolean changeStatus(long malId, LedgerStatus status) {
        return services.ledger().changeStatus(malId, status);
    }
}
