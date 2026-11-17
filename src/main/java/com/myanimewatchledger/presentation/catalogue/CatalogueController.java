package com.myanimewatchledger.presentation.catalogue;

import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.application.service.CatalogueRefreshOutcome;
import com.myanimewatchledger.application.service.LedgerChangeEvent;
import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.AnimePreference;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import com.myanimewatchledger.presentation.state.WindowStateStore;
import java.io.IOException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Thin presentation boundary for seasonal catalogue and ledger actions. */
public final class CatalogueController {
    private final ApplicationServices services;
    private final WindowStateStore preferences;

    public CatalogueController(ApplicationServices services, WindowStateStore preferences) {
        this.services = services;
        this.preferences = preferences;
    }

    public List<OpenCatalogueTab> restoreTabs() { return services.catalogueTabs().restoreOpenTabs(); }
    public OpenCatalogueTab open(Season season, int year, String query) {
        return services.catalogueTabs().open(season, year, query);
    }
    public boolean close(long id) { return services.catalogueTabs().close(id); }
    public boolean updateQuery(long id, String query) { return services.catalogueTabs().updateQuery(id, query); }
    public Optional<CatalogueSearch> findSearch(long id) { return services.catalogue().findSeason(id); }
    public List<CatalogueAnime> loadAnime(long id, String query) { return loadAnime(id, query, false); }
    public List<CatalogueAnime> loadAnime(long id, String query, boolean excludeLedgerEntries) {
        return services.catalogue().getAnime(id, query, excludeLedgerEntries);
    }
    public boolean hideLedgerEntries() { return preferences.loadHideLedgerEntries(); }
    public void saveHideLedgerEntries(boolean enabled) {
        try { preferences.saveHideLedgerEntries(enabled); }
        catch (IOException failure) {
            throw new IllegalStateException("Could not save the Catalogue filter preference.", failure);
        }
    }
    public CompletableFuture<CatalogueRefreshOutcome> refresh(Season season, int year) {
        return services.refreshSeasonAsync(season, year);
    }
    public boolean isMalConfigured() { return services.isMalConfigured(); }
    public CompletableFuture<Void> clearCacheAsync() { return services.clearCatalogueCacheAsync(); }
    public Optional<LedgerEntry> findLedgerEntry(long malId) { return services.ledger().find(malId); }
    public LedgerEntry add(CatalogueAnime anime, LedgerStatus status) { return services.ledger().add(anime, status); }
    public boolean changeStatus(long malId, LedgerStatus status) {
        return services.ledger().changeStatus(malId, status);
    }
    public boolean changePreference(long malId, AnimePreference preference) { return services.ledger().changePreference(malId, preference); }
    public boolean changeWatchLaterPriority(long malId, Integer priority) { return services.ledger().changeWatchLaterPriority(malId, priority); }
    public AutoCloseable subscribeToLedgerChanges(Consumer<LedgerChangeEvent> listener) {
        return services.ledger().subscribe(listener);
    }
}
