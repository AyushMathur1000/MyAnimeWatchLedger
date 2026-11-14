package com.myanimewatchledger.presentation.ledger;

import com.myanimewatchledger.domain.ledger.LedgerStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LedgerViewStatusTest {

    @Test
    void exposesAllSixStatusesToTheSharedStatusPresentation() {
        assertEquals(Set.of(
                LedgerStatus.WATCHED,
                LedgerStatus.WATCHING,
                LedgerStatus.ABANDONED,
                LedgerStatus.BLACKLISTED,
                LedgerStatus.HOLD,
                LedgerStatus.WATCH_LATER
        ), Set.of(LedgerStatus.values()));
    }

    @Test
    void keepsHoldAndWatchLaterAsDistinctUserFacingStatuses() {
        assertEquals("Hold", LedgerView.displayStatus(LedgerStatus.HOLD));
        assertEquals("Watch Later", LedgerView.displayStatus(LedgerStatus.WATCH_LATER));
        assertNotEquals(
                LedgerView.displayStatus(LedgerStatus.HOLD),
                LedgerView.displayStatus(LedgerStatus.WATCH_LATER)
        );
    }

    @Test
    void formatsEveryStatusForTheLedgerSelectorsAndFilters() {
        Set<String> labels = Stream.of(LedgerStatus.values())
                .map(LedgerView::displayStatus)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "Watched", "Watching", "Abandoned", "Blacklisted", "Hold", "Watch Later"
        ), labels);
    }
}
