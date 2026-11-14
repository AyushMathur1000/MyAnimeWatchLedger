package com.myanimewatchledger.presentation.main;

/** Primary workspaces available from the persistent application sidebar. */
public enum MainNavigation {
    MY_LEDGER("My Ledger", "M12 7v14 M3 18V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v13 M3 18a3 3 0 0 0 3 3h12 M8 7h8 M8 11h8"),
    CATALOGUE("Catalogue", "M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z"),
    ANIME_DETAILS("Anime Details", "M3 3h18v18H3z M3 9h18 M9 21V9"),
    SEARCH_HISTORY("Search History", "M3 12a9 9 0 1 0 2.64-6.36L3 8 M3 3v5h5 M12 7v5l4 2"),
    SETTINGS("Settings", "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.7 2.94-.08-.02a1.7 1.7 0 0 0-1.78.5l-.05.06h-3.4l-.04-.07a1.7 1.7 0 0 0-1.78-.49l-.08.02-1.7-2.94.06-.06a1.7 1.7 0 0 0 .34-1.88l-.04-.08-2.94-1.7.02-.08a1.7 1.7 0 0 0-.5-1.78l-.06-.05v-3.4l.07-.04a1.7 1.7 0 0 0 .49-1.78l-.02-.08 2.94-1.7.06.06a1.7 1.7 0 0 0 1.88.34l.08-.04 1.7-2.94.08.02a1.7 1.7 0 0 0 1.78-.5l.05-.06h3.4l.04.07a1.7 1.7 0 0 0 1.78.49l.08-.02 1.7 2.94-.06.06a1.7 1.7 0 0 0-.34 1.88l.04.08 2.94 1.7-.02.08a1.7 1.7 0 0 0 .5 1.78l.06.05v3.4l-.07.04a1.7 1.7 0 0 0-.49 1.78z");

    private final String label;
    private final String iconPath;

    MainNavigation(String label, String iconPath) {
        this.label = label;
        this.iconPath = iconPath;
    }

    public String label() { return label; }
    public String iconPath() { return iconPath; }
}
