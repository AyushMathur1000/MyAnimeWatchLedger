package com.myanimewatchledger.presentation.ledger;

import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerFilterTest {
    private static final Instant DATE = Instant.parse("2026-11-14T00:00:00Z");

    @Test
    void partialTitleSearchIsCaseInsensitive() {
        var entries = List.of(entry(1, "Steins;Gate", LedgerStatus.WATCHED),
                entry(2, "Steins;Gate 0", LedgerStatus.HOLD),
                entry(3, "Fullmetal Alchemist", LedgerStatus.WATCHING));

        assertEquals(List.of(1L, 2L), LedgerFilter.apply(entries, "STEINS", Set.of())
                .stream().map(LedgerEntry::malId).toList());
    }

    @Test
    void selectedStatusesCombineWithTitleSearchUsingAndSemantics() {
        var entries = List.of(entry(1, "Steins;Gate", LedgerStatus.WATCHED),
                entry(2, "Steins;Gate 0", LedgerStatus.HOLD),
                entry(3, "Steins;Gate Movie", LedgerStatus.WATCHING));

        assertEquals(List.of(1L, 3L), LedgerFilter.apply(entries, "steins",
                Set.of(LedgerStatus.WATCHED, LedgerStatus.WATCHING))
                .stream().map(LedgerEntry::malId).toList());
    }

    @Test
    void noStatusSelectionMeansAllStatusesAndBlankSearchMeansAllTitles() {
        var entries = List.of(entry(1, "One", LedgerStatus.ABANDONED),
                entry(2, "Two", LedgerStatus.BLACKLISTED));

        assertEquals(entries, LedgerFilter.apply(entries, "  ", Set.of()));
    }

    private static LedgerEntry entry(long id, String title, LedgerStatus status) {
        return new LedgerEntry(id, title, status, DATE, DATE);
    }
}
