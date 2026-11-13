package com.myanimewatchledger.presentation.main;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainNavigationTest {
    @Test
    void exposesExactlyTheFourApprovedPrimaryDestinations() {
        assertArrayEquals(new MainNavigation[]{
                MainNavigation.MY_LEDGER,
                MainNavigation.CATALOGUE,
                MainNavigation.SEARCH_HISTORY,
                MainNavigation.SETTINGS
        }, MainNavigation.values());
    }

    @Test
    void myLedgerIsTheDefaultNavigationDestination() {
        assertEquals("My Ledger", MainNavigation.MY_LEDGER.label());
    }
}
