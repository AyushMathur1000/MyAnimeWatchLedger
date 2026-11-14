package com.myanimewatchledger.app;

import com.myanimewatchledger.application.service.*;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.domain.catalogue.CatalogueAnime;
import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;
import com.myanimewatchledger.domain.catalogue.Season;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;

import java.io.IOException;
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
    private final MalApiConnection malConnection;

    ApplicationServices(
            LedgerService ledger,
            CatalogueService catalogue,
            CatalogueTabService tabs,
            CatalogueRefreshService refresh,
            AnimeDetailService animeDetails,
            AnimeDetailTabService animeDetailTabs,
            BackgroundTaskExecutor background,
            MalApiConnection malConnection) {
        this.ledger = Objects.requireNonNull(ledger);
        this.catalogue = Objects.requireNonNull(catalogue);
        this.tabs = Objects.requireNonNull(tabs);
        this.refresh = Objects.requireNonNull(refresh);
        this.animeDetails = Objects.requireNonNull(animeDetails);
        this.animeDetailTabs = Objects.requireNonNull(animeDetailTabs);
        this.background = Objects.requireNonNull(background);
        this.malConnection = Objects.requireNonNull(malConnection);
    }

    public LedgerService ledger() { return ledger; }
    public CatalogueService catalogue() { return catalogue; }
    public CatalogueTabService catalogueTabs() { return tabs; }
    public AnimeDetailService animeDetails() { return animeDetails; }
    public AnimeDetailTabService animeDetailTabs() { return animeDetailTabs; }
    public boolean isMalConfigured() { return malConnection.isConfigured(); }

    /** Persists and activates the supplied Client ID away from the JavaFX thread. */
    public CompletableFuture<Void> saveMalClientIdAsync(String clientId) {
        return background.submit(() -> {
            try {
                malConnection.saveClientId(clientId);
                return null;
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Could not save MAL configuration. Check application file permissions.", failure);
            }
        });
    }

    /** Makes one explicit read-only MAL request; no catalogue or ledger data is changed. */
    public CompletableFuture<Void> testMalConnectionAsync() {
        if (!isMalConfigured()) {
            throw new IllegalStateException("Save a MAL Client ID before testing the connection.");
        }
        return background.submit(() -> {
            malConnection.testConnection();
            return null;
        });
    }

    /** Completion executes on a worker; presentation code must marshal UI changes to JavaFX. */
    public CompletableFuture<CatalogueRefreshOutcome> refreshSeasonAsync(Season season, int year) {
        if (!isMalConfigured()) {
            throw new IllegalStateException("Configure a MAL Client ID before refreshing a catalogue.");
        }
        return background.submit(() -> refresh.refresh(season, year));
    }

    /** All database and MAL I/O for details executes on a worker thread. */
    public CompletableFuture<AnimeDetailResult> loadAnimeDetailsAsync(long malId) {
        return background.submit(() -> animeDetails.load(malId));
    }

    public CompletableFuture<List<OpenAnimeDetailTab>> restoreAnimeDetailTabsAsync() {
        return background.submit(animeDetailTabs::restoreOpenTabs);
    }

    public CompletableFuture<OpenAnimeDetailTab> openAnimeDetailTabAsync(long malId) {
        return background.submit(() -> animeDetailTabs.open(malId));
    }

    public CompletableFuture<Boolean> closeAnimeDetailTabAsync(long malId) {
        return background.submit(() -> animeDetailTabs.close(malId));
    }

    public CompletableFuture<Void> reorderAnimeDetailTabsAsync(List<Long> malIds) {
        List<Long> order = List.copyOf(malIds);
        return background.submit(() -> {
            animeDetailTabs.reorder(order);
            return null;
        });
    }

    public CompletableFuture<Optional<LedgerEntry>> findLedgerEntryAsync(long malId) {
        return background.submit(() -> ledger.find(malId));
    }

    public CompletableFuture<LedgerEntry> addAnimeToLedgerAsync(AnimeMetadataCache cache, LedgerStatus status) {
        Objects.requireNonNull(cache);
        Objects.requireNonNull(status);
        return background.submit(() -> ledger.add(new CatalogueAnime(
                cache.malId(), cache.preferredTitle(), cache.metadataJson(),
                cache.searchableText(), cache.cachedAt(), 0, null), status));
    }

    public CompletableFuture<LedgerEntry> changeLedgerStatusAsync(long malId, LedgerStatus status) {
        Objects.requireNonNull(status);
        return background.submit(() -> {
            if (!ledger.changeStatus(malId, status)) {
                LedgerEntry current = ledger.find(malId).orElseThrow(
                        () -> new IllegalStateException("Anime is no longer in the ledger."));
                if (current.status() != status) {
                    throw new IllegalStateException("The ledger status could not be updated.");
                }
                return current;
            }
            return ledger.find(malId).orElseThrow(
                    () -> new IllegalStateException("Updated ledger entry could not be found."));
        });
    }
}
