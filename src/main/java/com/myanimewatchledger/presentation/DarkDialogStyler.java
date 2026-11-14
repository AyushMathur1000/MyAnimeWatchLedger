package com.myanimewatchledger.presentation;

import javafx.scene.control.Dialog;

/** Applies the application's dark stylesheet to native JavaFX dialogs. */
public final class DarkDialogStyler {
    private DarkDialogStyler() { }

    public static void apply(Dialog<?> dialog) {
        var stylesheet = DarkDialogStyler.class.getResource("/styles/application.css");
        if (stylesheet == null) return;
        dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-dialog");
    }
}
