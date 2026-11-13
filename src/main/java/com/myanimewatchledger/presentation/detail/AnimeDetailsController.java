package com.myanimewatchledger.presentation.detail;

import com.myanimewatchledger.app.ApplicationServices;
import com.myanimewatchledger.application.service.AnimeDetailResult;
import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;
import com.myanimewatchledger.domain.ledger.LedgerEntry;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous presentation boundary for the shared Anime Details workspace. */
public final class AnimeDetailsController {
    private final ApplicationServices services;

    public AnimeDetailsController(ApplicationServices services) {
        this.services = java.util.Objects.requireNonNull(services, "services");
    }

    public CompletableFuture<List<OpenAnimeDetailTab>> restoreTabs() {
        return services.restoreAnimeDetailTabsAsync();
    }

    public CompletableFuture<OpenAnimeDetailTab> openTab(long malId) {
        return services.openAnimeDetailTabAsync(malId);
    }

    public CompletableFuture<Boolean> closeTab(long malId) {
        return services.closeAnimeDetailTabAsync(malId);
    }

    public CompletableFuture<Void> reorderTabs(List<Long> malIds) {
        return services.reorderAnimeDetailTabsAsync(malIds);
    }

    public CompletableFuture<AnimeDetailResult> loadDetails(long malId) {
        return services.loadAnimeDetailsAsync(malId);
    }

    public CompletableFuture<Optional<LedgerEntry>> findLedgerEntry(long malId) {
        return services.findLedgerEntryAsync(malId);
    }
}
