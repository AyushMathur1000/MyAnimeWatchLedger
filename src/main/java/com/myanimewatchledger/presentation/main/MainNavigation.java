package com.myanimewatchledger.presentation.main;

/** Primary workspaces available from the persistent application sidebar. */
public enum MainNavigation {
    MY_LEDGER("My Ledger"),
    CATALOGUE("Catalogue"),
    ANIME_DETAILS("Anime Details"),
    SEARCH_HISTORY("Search History"),
    SETTINGS("Settings");

    private final String label;

    MainNavigation(String label) {
        this.label = label;
    }

    public String label() { return label; }
}
