package com.myanimewatchledger.domain.catalogue;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import java.time.Instant;
public record CatalogueAnime(long malId, String preferredTitle, String metadataJson, String searchableText,
 Instant cachedAt, int displayOrder, LedgerStatus ledgerStatus) { }
