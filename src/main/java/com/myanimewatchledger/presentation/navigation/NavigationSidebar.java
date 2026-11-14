package com.myanimewatchledger.presentation.navigation;

import com.myanimewatchledger.presentation.main.MainNavigation;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
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
        setPadding(new Insets(18, 10, 18, 10));
        setSpacing(8);

        for (MainNavigation item : MainNavigation.values()) {
            Button button = new Button(item.icon());
            button.getStyleClass().add("navigation-button");
            button.setMinSize(42, 42);
            button.setPrefSize(42, 42);
            button.setMaxSize(42, 42);
            button.setTooltip(new Tooltip(item.label()));
            button.setAccessibleText(item.label());
            button.setFocusTraversable(true);
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
