package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.CatalogueRepository;
import com.myanimewatchledger.domain.catalogue.CatalogueAnime;
import com.myanimewatchledger.domain.catalogue.CatalogueSearch;
import com.myanimewatchledger.domain.catalogue.Season;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provides season catalogue operations over the persistent catalogue cache. */
public final class CatalogueService {
    private final CatalogueRepository catalogue;
    private final CatalogueSearchService searchService;

    public CatalogueService(CatalogueRepository catalogue) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.searchService = new CatalogueSearchService(catalogue);
    }

    public CatalogueSearch getOrCreateSeason(Season season, int year) {
        return catalogue.findOrCreate(Objects.requireNonNull(season, "season"), year);
    }

    public Optional<CatalogueSearch> findSeason(long searchId) {
        if (searchId <= 0) {
            throw new IllegalArgumentException("Search ID must be positive.");
        }
        return catalogue.findById(searchId);
    }

    public List<CatalogueSearch> getKnownSeasons() {
        return catalogue.findAll();
    }

    public List<CatalogueAnime> getAnime(long searchId, String query) {
        return searchService.search(searchId, query);
    }

    public void clearCache() {
        catalogue.clearCache();
    }
}
