package com.myanimewatchledger.presentation.state;

/** Persistable main-window geometry. */
public record WindowState(double width, double height, boolean maximized) {
    public WindowState {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 800 || height < 560) {
            throw new IllegalArgumentException("Window dimensions are outside the supported range.");
        }
    }

    public static WindowState defaults() {
        return new WindowState(1200, 780, true);
    }
}
