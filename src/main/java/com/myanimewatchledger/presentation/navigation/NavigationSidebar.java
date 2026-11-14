package com.myanimewatchledger.presentation.navigation;

import com.myanimewatchledger.presentation.main.MainNavigation;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Persistent primary navigation for the main application window. */
public final class NavigationSidebar extends VBox {
    private final Map<MainNavigation, Button> buttons = new EnumMap<>(MainNavigation.class);

    public NavigationSidebar(Consumer<MainNavigation> onNavigate) {
        Objects.requireNonNull(onNavigate, "onNavigate");
        getStyleClass().add("navigation-sidebar");
        setPadding(new Insets(18, 12, 18, 12));
        setSpacing(8);

        for (MainNavigation item : MainNavigation.values()) {
            Button button = new Button(item.icon() + "   " + item.label());
            button.getStyleClass().add("navigation-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> onNavigate.accept(item));
            buttons.put(item, button);
            getChildren().add(button);
        }
        select(MainNavigation.MY_LEDGER);
    }

    public void select(MainNavigation selected) {
        buttons.forEach((item, button) -> {
            button.getStyleClass().remove("selected");
            if (item == selected) button.getStyleClass().add("selected");
        });
    }
}
