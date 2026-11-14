package com.myanimewatchledger.presentation.navigation;

import com.myanimewatchledger.presentation.main.MainNavigation;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Persistent icon-only primary navigation for the main application window. */
public final class NavigationSidebar extends VBox {
    private final Map<MainNavigation, Button> buttons = new EnumMap<>(MainNavigation.class);

    public NavigationSidebar(Consumer<MainNavigation> onNavigate) {
        Objects.requireNonNull(onNavigate, "onNavigate");
        getStyleClass().add("navigation-sidebar");
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(18, 9, 18, 9));
        setSpacing(9);

        for (MainNavigation item : MainNavigation.values()) {
            SVGPath icon = new SVGPath();
            icon.setContent(item.iconPath());
            icon.getStyleClass().add("navigation-icon");
            icon.setMouseTransparent(true);
            icon.setScaleX(1.2);
            icon.setScaleY(1.2);

            Button button = new Button();
            button.setGraphic(icon);
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            button.getStyleClass().add("navigation-button");
            button.setMinSize(46, 46);
            button.setPrefSize(46, 46);
            button.setMaxSize(46, 46);
            button.setAlignment(Pos.CENTER);
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
