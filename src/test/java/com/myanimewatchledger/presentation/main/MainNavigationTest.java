package com.myanimewatchledger.presentation.main;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainNavigationTest {
    @Test
    void exposesTheApprovedPrimaryDestinationsIncludingAnimeDetails() {
        assertArrayEquals(new MainNavigation[]{
                MainNavigation.MY_LEDGER,
                MainNavigation.CATALOGUE,
                MainNavigation.ANIME_DETAILS,
                MainNavigation.SEARCH_HISTORY,
                MainNavigation.SETTINGS
        }, MainNavigation.values());
    }

    @Test
    void myLedgerIsTheDefaultNavigationDestination() {
        assertEquals("My Ledger", MainNavigation.MY_LEDGER.label());
    }
}
