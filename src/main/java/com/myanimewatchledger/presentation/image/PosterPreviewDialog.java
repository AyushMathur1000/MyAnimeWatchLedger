package com.myanimewatchledger.presentation.image;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Window;

/** Displays a cover at a larger size without changing the cached source image. */
public final class PosterPreviewDialog {
    private PosterPreviewDialog() { }

    public static void show(String preferredUrl, String fallbackUrl, String title, Window owner) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        ImageView preview = new ImageView();
        preview.setPreserveRatio(true);
        preview.setFitWidth(bounds.getWidth() * 0.78);
        preview.setFitHeight(bounds.getHeight() * 0.78);
        preview.setSmooth(true);

        Label placeholder = new Label("No poster available");
        placeholder.getStyleClass().add("anime-details-no-poster");
        StackPane frame = new StackPane(preview, placeholder);
        frame.setPadding(new Insets(12));
        frame.setStyle("-fx-background-color: #17191f;");
        MalImageLoader.load(preview, placeholder, preferredUrl, fallbackUrl,
                bounds.getWidth() * 0.78, bounds.getHeight() * 0.78);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title == null || title.isBlank() ? "Cover preview" : title);
        if (owner != null) dialog.initOwner(owner);
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(frame);
        dialog.getDialogPane().setPrefSize(bounds.getWidth() * 0.82, bounds.getHeight() * 0.86);
        dialog.setResizable(true);
        dialog.showAndWait();
    }
}
