package com.myanimewatchledger.presentation.main;

/** Primary workspaces available from the persistent application sidebar. */
public enum MainNavigation {
    MY_LEDGER("My Ledger", "▤"),
    CATALOGUE("Catalogue", "▦"),
    SEARCH_HISTORY("Search History", "◷"),
    SETTINGS("Settings", "⚙");

    private final String label;
    private final String icon;

    MainNavigation(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() { return label; }
    public String icon() { return icon; }
}
