package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.OpenAnimeDetailTabRepository;
import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Manages persistent, unique Anime Details tabs independently of the ledger and cache. */
public final class AnimeDetailTabService {
    private final OpenAnimeDetailTabRepository tabs;

    public AnimeDetailTabService(OpenAnimeDetailTabRepository tabs) {
        this.tabs = Objects.requireNonNull(tabs, "tabs");
    }

    public List<OpenAnimeDetailTab> restoreOpenTabs() {
        return tabs.findAllOrdered();
    }

    /** Opens an anime or returns its existing tab without creating a duplicate. */
    public OpenAnimeDetailTab open(long malId) {
        requireValidMalId(malId);
        List<OpenAnimeDetailTab> current = tabs.findAllOrdered();
        for (OpenAnimeDetailTab tab : current) {
            if (tab.malId() == malId) return tab;
        }
        List<OpenAnimeDetailTab> updated = new ArrayList<>(current);
        OpenAnimeDetailTab created = new OpenAnimeDetailTab(malId, current.size());
        updated.add(created);
        tabs.replaceAll(updated);
        return created;
    }

    /** Closing a tab affects only the saved tab list, never metadata or ledger data. */
    public boolean close(long malId) {
        requireValidMalId(malId);
        boolean removed = tabs.close(malId);
        if (removed) compactOrder(tabs.findAllOrdered());
        return removed;
    }

    public void reorder(List<Long> malIds) {
        Objects.requireNonNull(malIds, "malIds");
        List<OpenAnimeDetailTab> current = tabs.findAllOrdered();
        Set<Long> existing = new HashSet<>();
        current.forEach(tab -> existing.add(tab.malId()));
        Set<Long> requested = new HashSet<>(malIds);
        if (malIds.size() != requested.size() || !existing.equals(requested)) {
            throw new IllegalArgumentException("Tab order must contain every open anime exactly once.");
        }

        List<OpenAnimeDetailTab> reordered = new ArrayList<>();
        for (int i = 0; i < malIds.size(); i++) {
            reordered.add(new OpenAnimeDetailTab(malIds.get(i), i));
        }
        tabs.replaceAll(reordered);
    }

    private void compactOrder(List<OpenAnimeDetailTab> current) {
        List<OpenAnimeDetailTab> compacted = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            compacted.add(new OpenAnimeDetailTab(current.get(i).malId(), i));
        }
        tabs.replaceAll(compacted);
    }

    private static void requireValidMalId(long malId) {
        if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");
    }
}
