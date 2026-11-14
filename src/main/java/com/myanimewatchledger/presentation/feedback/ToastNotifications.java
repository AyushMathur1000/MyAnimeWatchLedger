package com.myanimewatchledger.presentation.feedback;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Map;
import java.util.WeakHashMap;

/** Displays brief, non-blocking feedback without taking space in the workspace layout. */
public final class ToastNotifications {
    private static final Map<Window, Popup> ACTIVE = new WeakHashMap<>();

    private ToastNotifications() { }

    public static void show(Node anchor, String message, boolean error) {
        if (anchor == null || anchor.getScene() == null || anchor.getScene().getWindow() == null
                || message == null || message.isBlank()) return;

        Window owner = anchor.getScene().getWindow();
        Runnable display = () -> showForWindow(owner, message, error);
        if (Platform.isFxApplicationThread()) display.run();
        else Platform.runLater(display);
    }

    private static void showForWindow(Window owner, String message, boolean error) {
        Popup previous = ACTIVE.remove(owner);
        if (previous != null) previous.hide();

        Label text = new Label(message);
        text.getStyleClass().add("app-toast-message");
        text.setWrapText(true);
        text.setMaxWidth(420);

        StackPane toast = new StackPane(text);
        toast.getStyleClass().addAll("app-toast", error ? "app-toast-error" : "app-toast-success");
        toast.setPadding(new Insets(10, 16, 10, 16));
        toast.setMaxWidth(460);
        toast.setMouseTransparent(true);

        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(false);
        popup.getContent().add(toast);
        ACTIVE.put(owner, popup);
        popup.setOnHidden(event -> ACTIVE.remove(owner, popup));
        popup.show(owner);

        Platform.runLater(() -> {
            if (!popup.isShowing()) return;
            popup.setX(owner.getX() + owner.getWidth() - popup.getWidth() - 24);
            popup.setY(owner.getY() + owner.getHeight() - popup.getHeight() - 54);
        });

        PauseTransition visibleTime = new PauseTransition(Duration.millis(2300));
        visibleTime.setOnFinished(event -> {
            FadeTransition fade = new FadeTransition(Duration.millis(300), toast);
            fade.setFromValue(1);
            fade.setToValue(0);
            fade.setOnFinished(done -> popup.hide());
            fade.play();
        });
        visibleTime.play();
    }
}
