package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.CatalogueRepository;
import com.myanimewatchledger.application.port.OpenCatalogueTabRepository;
import com.myanimewatchledger.domain.catalogue.CatalogueSearch;
import com.myanimewatchledger.domain.catalogue.OpenCatalogueTab;
import com.myanimewatchledger.domain.catalogue.Season;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Manages the user's restorable catalogue-tab state independently of search history. */
public final class CatalogueTabService {
    private final CatalogueRepository catalogue;
    private final OpenCatalogueTabRepository tabs;

    public CatalogueTabService(CatalogueRepository catalogue, OpenCatalogueTabRepository tabs) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.tabs = Objects.requireNonNull(tabs, "tabs");
    }

    public List<OpenCatalogueTab> restoreOpenTabs() {
        return tabs.findAllOrdered();
    }

    public OpenCatalogueTab open(Season season, int year, String query) {
        CatalogueSearch search = catalogue.findOrCreate(Objects.requireNonNull(season, "season"), year);
        List<OpenCatalogueTab> current = tabs.findAllOrdered();
        for (OpenCatalogueTab tab : current) {
            if (tab.searchId() == search.searchId()) {
                OpenCatalogueTab updated = new OpenCatalogueTab(tab.searchId(), tab.tabOrder(), normalizeQuery(query));
                tabs.save(updated);
                return updated;
            }
        }
        OpenCatalogueTab created = new OpenCatalogueTab(search.searchId(), current.size(), normalizeQuery(query));
        tabs.save(created);
        return created;
    }

    public boolean updateQuery(long searchId, String query) {
        List<OpenCatalogueTab> current = tabs.findAllOrdered();
        for (OpenCatalogueTab tab : current) {
            if (tab.searchId() == searchId) {
                tabs.save(new OpenCatalogueTab(searchId, tab.tabOrder(), normalizeQuery(query)));
                return true;
            }
        }
        return false;
    }

    public boolean close(long searchId) {
        boolean removed = tabs.close(searchId);
        if (removed) {
            compactOrder(tabs.findAllOrdered());
        }
        return removed;
    }

    /** Reorders all open tabs; the supplied IDs must be an exact permutation. */
    public void reorder(List<Long> orderedSearchIds) {
        Objects.requireNonNull(orderedSearchIds, "orderedSearchIds");
        List<OpenCatalogueTab> current = tabs.findAllOrdered();
        Set<Long> existing = new HashSet<>();
        current.forEach(tab -> existing.add(tab.searchId()));
        Set<Long> requested = new HashSet<>(orderedSearchIds);
        if (orderedSearchIds.size() != requested.size() || !existing.equals(requested)) {
            throw new IllegalArgumentException("Tab order must contain every open tab exactly once.");
        }

        List<OpenCatalogueTab> reordered = new ArrayList<>(orderedSearchIds.size());
        for (int i = 0; i < orderedSearchIds.size(); i++) {
            long id = orderedSearchIds.get(i);
            OpenCatalogueTab old = current.stream()
                    .filter(tab -> tab.searchId() == id)
                    .findFirst()
                    .orElseThrow();
            reordered.add(new OpenCatalogueTab(id, i, old.searchQuery()));
        }
        tabs.replaceAll(reordered);
    }

    private void compactOrder(List<OpenCatalogueTab> current) {
        List<OpenCatalogueTab> compacted = new ArrayList<>(current.size());
        for (int i = 0; i < current.size(); i++) {
            OpenCatalogueTab tab = current.get(i);
            compacted.add(new OpenCatalogueTab(tab.searchId(), i, tab.searchQuery()));
        }
        tabs.replaceAll(compacted);
    }

    private static String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }
}
