package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.AnimeMetadataRepository;
import com.myanimewatchledger.application.port.CatalogueRepository;
import com.myanimewatchledger.application.port.LedgerRepository;
import com.myanimewatchledger.application.port.MalCatalogueGateway;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.domain.catalogue.CatalogueSearch;
import com.myanimewatchledger.domain.catalogue.Season;
import com.myanimewatchledger.domain.catalogue.SeasonalCatalogue;
import com.myanimewatchledger.infrastructure.persistence.DatabaseTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Coordinates complete seasonal retrieval and atomic cache replacement.
 * Network retrieval always finishes before the persistence transaction begins.
 */
public final class CatalogueRefreshService {
    private static final int MAX_STORED_ERROR_LENGTH = 1_000;

    private final MalCatalogueGateway gateway;
    private final CatalogueRepository catalogue;
    private final AnimeMetadataRepository metadata;
    private final LedgerRepository ledger;
    private final DatabaseTransactionManager transactions;
    private final Clock clock;

    public CatalogueRefreshService(
            MalCatalogueGateway gateway,
            CatalogueRepository catalogue,
            AnimeMetadataRepository metadata,
            LedgerRepository ledger,
            DatabaseTransactionManager transactions) {
        this(gateway, catalogue, metadata, ledger, transactions, Clock.systemUTC());
    }

    public CatalogueRefreshService(
            MalCatalogueGateway gateway,
            CatalogueRepository catalogue,
            AnimeMetadataRepository metadata,
            LedgerRepository ledger,
            DatabaseTransactionManager transactions,
            Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CatalogueRefreshOutcome refresh(Season season, int year) {
        CatalogueSearch search = catalogue.findOrCreate(
                Objects.requireNonNull(season, "season"), year);

        final SeasonalCatalogue fetched;
        try {
            // This call retrieves and validates every page. No DB transaction is open here.
            fetched = Objects.requireNonNull(gateway.fetchSeason(season, year),
                    "Catalogue gateway returned no result.");
            List<AnimeMetadataCache> anime = fetched.anime();
            List<Long> malIdsInDisplayOrder = anime.stream()
                    .map(AnimeMetadataCache::malId)
                    .toList();
            Instant completedAt = clock.instant();

            transactions.executeInTransaction(() -> {
                metadata.upsertAll(anime);

                // Updating an existing row never inserts a ledger entry and does not
                // modify its status or date_added. The repository updates last_modified
                // only when the preferred title actually changes.
                for (AnimeMetadataCache item : anime) {
                    ledger.updatePreferredTitle(item.malId(), item.preferredTitle());
                }

                catalogue.replaceMembership(search.searchId(), malIdsInDisplayOrder);
                catalogue.markFetchSucceeded(search.searchId(), completedAt);
                return null;
            });

            return new CatalogueRefreshOutcome(
                    search.searchId(), anime.size(), fetched.skippedItems(),
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
