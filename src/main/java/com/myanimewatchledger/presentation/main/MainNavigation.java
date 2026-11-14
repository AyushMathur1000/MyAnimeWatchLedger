package com.myanimewatchledger.presentation.main;

/** Primary workspaces and their Lucide-inspired outline icon paths. */
public enum MainNavigation {
    MY_LEDGER("My Ledger",
            "M12 7v14 M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-5a4 4 0 0 0-4 4 4 4 0 0 0-4-4z M9 14l2 2 4-4"),
    CATALOGUE("Catalogue",
            "M3 4a1 1 0 0 1 1-1h5v18H4a1 1 0 0 1-1-1z M15 3h5a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1h-5z M9 3h6v18H9z"),
    ANIME_DETAILS("Anime Details",
            "M4 9L2 3l7 3c2-1 4-1 6 0l7-3-2 7v5c-1 4-4 7-8 8-4-1-7-4-8-8z M8 12l2 1 M16 12l-2 1 M10 17q2 2 4 0 M9 7l-1-2 M15 7l1-2"),
    SEARCH_HISTORY("Search History",
            "M3 12a9 9 0 1 0 2.64-6.36L3 8 M3 3v5h5 M12 7v5l4 2"),
    SETTINGS("Settings",
            "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.7 2.94-.08-.02a1.7 1.7 0 0 0-1.78.5l-.05.06h-3.4l-.04-.07a1.7 1.7 0 0 0-1.78-.49l-.08.02-1.7-2.94.06-.06a1.7 1.7 0 0 0 .34-1.88l-.04-.08-2.94-1.7.02-.08a1.7 1.7 0 0 0-.5-1.78l-.06-.05v-3.4l.07-.04a1.7 1.7 0 0 0 .49-1.78l-.02-.08 2.94-1.7.06.06a1.7 1.7 0 0 0 1.88.34l.08-.04 1.7-2.94.08.02a1.7 1.7 0 0 0 1.78-.5l.05-.06h3.4l.04.07a1.7 1.7 0 0 0 1.78.49l.08-.02 1.7 2.94-.06.06a1.7 1.7 0 0 0-.34 1.88l.04.08 2.94 1.7-.02.08a1.7 1.7 0 0 0 .5 1.78l.06.05v3.4l-.07.04a1.7 1.7 0 0 0-.49 1.78z");

    private final String label;
    private final String iconPath;

    MainNavigation(String label, String iconPath) {
        this.label = label;
        this.iconPath = iconPath;
    }

    public String label() { return label; }
    public String iconPath() { return iconPath; }
}
