package com.myanimewatchledger.presentation.ledger;

import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic ledger filtering rules, independent of JavaFX. */
public final class LedgerFilter {
    private LedgerFilter() {
    }

    public static List<LedgerEntry> apply(
            List<LedgerEntry> entries, String query, Set<LedgerStatus> statuses) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Set<LedgerStatus> selectedStatuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        return entries.stream()
                .filter(entry -> normalizedQuery.isEmpty()
                        || entry.title().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .filter(entry -> selectedStatuses.isEmpty() || selectedStatuses.contains(entry.status()))
                .toList();
    }
}
