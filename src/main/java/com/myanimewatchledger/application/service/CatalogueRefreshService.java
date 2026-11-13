package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.AnimeMetadataCacheMerger;
import com.myanimewatchledger.application.port.AnimeMetadataRepository;
import com.myanimewatchledger.application.port.CatalogueRepository;
import com.myanimewatchledger.application.port.LedgerRepository;
import com.myanimewatchledger.application.port.MalCatalogueGateway;
import com.myanimewatchledger.application.port.TransactionRunner;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.domain.catalogue.CatalogueSearch;
import com.myanimewatchledger.domain.catalogue.Season;
import com.myanimewatchledger.domain.catalogue.SeasonalCatalogue;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Coordinates complete seasonal retrieval and atomic cache replacement.
 * Network retrieval always finishes before the persistence transaction begins.
 */
public final class CatalogueRefreshService {
    private static final int MAX_STORED_ERROR_LENGTH = 1_000;

    private final MalCatalogueGateway gateway;
    private final CatalogueRepository catalogue;
    private final AnimeMetadataRepository metadata;
    private final AnimeMetadataCacheMerger metadataMerger;
    private final LedgerRepository ledger;
    private final TransactionRunner transactions;
    private final Clock clock;
    private static final Set<Long> REFRESHING_SEARCH_IDS = ConcurrentHashMap.newKeySet();
    private static final Semaphore MAL_REQUEST_PERMIT = new Semaphore(1);

    public CatalogueRefreshService(
            MalCatalogueGateway gateway,
            CatalogueRepository catalogue,
            AnimeMetadataRepository metadata,
            AnimeMetadataCacheMerger metadataMerger,
            LedgerRepository ledger,
            TransactionRunner transactions) {
        this(gateway, catalogue, metadata, metadataMerger, ledger, transactions, Clock.systemUTC());
    }

    public CatalogueRefreshService(
            MalCatalogueGateway gateway,
            CatalogueRepository catalogue,
            AnimeMetadataRepository metadata,
            AnimeMetadataCacheMerger metadataMerger,
            LedgerRepository ledger,
            TransactionRunner transactions,
            Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.metadataMerger = Objects.requireNonNull(metadataMerger, "metadataMerger");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CatalogueRefreshOutcome refresh(Season season, int year) {
        CatalogueSearch search = catalogue.findOrCreate(
                Objects.requireNonNull(season, "season"), year);

        if (!REFRESHING_SEARCH_IDS.add(search.searchId())) {
            throw new IllegalStateException("This seasonal catalogue is already being refreshed.");
        }
        if (!MAL_REQUEST_PERMIT.tryAcquire()) {
            REFRESHING_SEARCH_IDS.remove(search.searchId());
            throw new IllegalStateException("Another MAL catalogue request is already in progress.");
        }

        try {
            // This call retrieves and validates every page. No DB transaction is open here.
            SeasonalCatalogue fetched = Objects.requireNonNull(gateway.fetchSeason(season, year),
                    "Catalogue gateway returned no result.");
            Instant completedAt = clock.instant();

            transactions.executeInTransaction(() -> {
                List<AnimeMetadataCache> effectiveMetadata = new ArrayList<>(fetched.anime().size());
                for (AnimeMetadataCache incoming : fetched.anime()) {
                    AnimeMetadataCache effective = metadata.findByMalId(incoming.malId())
                            .map(existing -> metadataMerger.merge(existing, incoming))
                            .orElse(incoming);
                    effectiveMetadata.add(effective);
                }

                metadata.upsertAll(effectiveMetadata);

                // Updating an existing row never inserts a ledger entry and does not
                // modify its status or date_added. The repository updates last_modified
                // only when the preferred title actually changes.
                for (AnimeMetadataCache item : effectiveMetadata) {
                    ledger.updatePreferredTitle(item.malId(), item.preferredTitle());
                }

                List<Long> malIdsInDisplayOrder = effectiveMetadata.stream()
                        .map(AnimeMetadataCache::malId)
                        .toList();
                catalogue.replaceMembership(search.searchId(), malIdsInDisplayOrder);
                catalogue.markFetchSucceeded(search.searchId(), completedAt);
                return null;
            });

            return new CatalogueRefreshOutcome(
                    search.searchId(), fetched.anime().size(), fetched.skippedItems(),
                    fetched.warnings(), completedAt);
        } catch (RuntimeException failure) {
            // The failed-attempt marker is intentionally outside the failed refresh
            // transaction, so it can be recorded after the cache writes roll back.
            try {
                catalogue.markFetchFailed(search.searchId(), clock.instant(), safeError(failure));
            } catch (RuntimeException recordingFailure) {
                failure.addSuppressed(recordingFailure);
            }
            throw failure;
        } finally {
            MAL_REQUEST_PERMIT.release();
            refreshingSearchIds.remove(search.searchId());
        }
    }

    private static String safeError(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() <= MAX_STORED_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_STORED_ERROR_LENGTH);
    }
}
