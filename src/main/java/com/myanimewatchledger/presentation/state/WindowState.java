package com.myanimewatchledger.presentation.state;

import com.myanimewatchledger.presentation.main.MainNavigation;

/** Persistable main-window geometry and last active primary workspace. */
public record WindowState(double width, double height, boolean maximized, MainNavigation lastNavigation) {
    public WindowState {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 800 || height < 560) {
            throw new IllegalArgumentException("Window dimensions are outside the supported range.");
        }
        if (lastNavigation == null) throw new IllegalArgumentException("Last navigation must not be null.");
    }

    public static WindowState defaults() {
        return new WindowState(1200, 780, true, MainNavigation.MY_LEDGER);
    }
}
