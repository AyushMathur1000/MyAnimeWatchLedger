package com.myanimewatchledger.presentation.toolbar;

import com.myanimewatchledger.presentation.main.MainNavigation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/** Builds compact, screen-specific action groups using standard JavaFX controls. */
public final class ContextualToolbar extends ToolBar {
    public ContextualToolbar() {
        getStyleClass().add("contextual-toolbar");
    }

    public void showFor(MainNavigation navigation) {
        getItems().clear();
        List<String[]> groups = switch (navigation) {
            case MY_LEDGER -> List.of(
                    new String[]{"＋ Add anime"},
                    new String[]{"⇄ Change status", "－ Remove"},
                    new String[]{"⌕ Search", "↕ Sort"});
            case CATALOGUE -> List.of(
                    new String[]{"← Previous season", "Season / Year", "Next season →"},
                    new String[]{"⟳ Refresh"},
                    new String[]{"⌕ Search", "Clear cache"});
            case SEARCH_HISTORY -> List.of(
                    new String[]{"⌕ Search history"},
                    new String[]{"↗ Reopen search", "Clear cache"});
            case SETTINGS -> List.of(
                    new String[]{"⚙ MAL configuration"},
                    new String[]{"Clear cache", "Backup", "Restore"});
        };

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            if (groupIndex > 0) getItems().add(new Separator());
            for (String action : groups.get(groupIndex)) {
                Button button = new Button(action);
                button.getStyleClass().add("toolbar-action");
                button.setDisable(true);
                button.setFocusTraversable(false);
                button.setTooltip(new javafx.scene.control.Tooltip(action + " — available in a later milestone"));
                getItems().add(button);
            }
        }
        Region spacer = new Region();
        spacer.setMinWidth(0);
        getItems().add(spacer);
    }
}
