package com.myanimewatchledger.domain.catalogue;

/** Persisted identity and ordering for an open Anime Details tab. */
public record OpenAnimeDetailTab(long malId, int tabOrder) {
    public OpenAnimeDetailTab {
        if (malId <= 0 || tabOrder < 0) {
            throw new IllegalArgumentException("Invalid anime detail tab identity or order.");
        }
    }
}
