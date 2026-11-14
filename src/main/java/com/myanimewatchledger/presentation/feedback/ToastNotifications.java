package com.myanimewatchledger.presentation.feedback;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Map;
import java.util.WeakHashMap;

/** Displays compact, styled, non-blocking notification cards above the workspace. */
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

        Label icon = new Label(error ? "!" : "✓");
        icon.getStyleClass().addAll("app-toast-icon", error ? "app-toast-icon-error" : "app-toast-icon-success");
        StackPane iconHolder = new StackPane(icon);
        iconHolder.getStyleClass().addAll("app-toast-icon-holder", error ? "app-toast-icon-error" : "app-toast-icon-success");
        iconHolder.setMinSize(34, 34);
        iconHolder.setPrefSize(34, 34);
        iconHolder.setMaxSize(34, 34);

        Label title = new Label(error ? "Something went wrong" : "Done");
        title.getStyleClass().add("app-toast-title");
        Label body = new Label(message);
        body.getStyleClass().add("app-toast-message");
        body.setWrapText(true);
        body.setMaxWidth(360);

        VBox text = new VBox(3, title, body);
        text.getStyleClass().add("app-toast-copy");
        HBox toast = new HBox(12, iconHolder, text);
        toast.getStyleClass().addAll("app-toast", error ? "app-toast-error" : "app-toast-success");
        toast.setPadding(new Insets(13, 17, 13, 14));
        toast.setMaxWidth(440);
        toast.setMinWidth(300);
        toast.setMouseTransparent(true);
        toast.setOpacity(0);
        toast.setTranslateY(8);

        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(false);
        popup.getContent().add(toast);
        ACTIVE.put(owner, popup);
        popup.setOnHidden(event -> ACTIVE.remove(owner, popup));
        popup.show(owner);
        if (owner.getScene() != null && popup.getScene() != null) {
            popup.getScene().getStylesheets().setAll(owner.getScene().getStylesheets());
        }

        Platform.runLater(() -> {
            if (!popup.isShowing()) return;
            popup.setX(owner.getX() + owner.getWidth() - popup.getWidth() - 24);
            popup.setY(owner.getY() + owner.getHeight() - popup.getHeight() - 28);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), toast);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            TranslateTransition slideIn = new TranslateTransition(Duration.millis(180), toast);
            slideIn.setFromY(8);
            slideIn.setToY(0);
            new ParallelTransition(fadeIn, slideIn).play();
        });

        PauseTransition visibleTime = new PauseTransition(Duration.millis(error ? 3600 : 2600));
        visibleTime.setOnFinished(event -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(260), toast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(done -> popup.hide());
            fadeOut.play();
        });
        visibleTime.play();
    }
}
