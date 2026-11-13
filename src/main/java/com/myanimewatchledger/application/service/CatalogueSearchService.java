package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.CatalogueRepository;
import com.myanimewatchledger.domain.catalogue.CatalogueAnime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Applies the catalogue's whitespace-tokenized, case-insensitive AND search semantics. */
public final class CatalogueSearchService {
    private final CatalogueRepository catalogue;

    public CatalogueSearchService(CatalogueRepository catalogue) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
    }

    public List<CatalogueAnime> search(long searchId, String query) {
        if (searchId <= 0) {
            throw new IllegalArgumentException("Search ID must be positive.");
        }
        return catalogue.findEligibleAnime(searchId, tokenize(query));
    }

    /**
     * Splits on whitespace, trims empty terms, lowercases with a stable locale,
     * and removes duplicate terms while preserving their original order.
     */
    public static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String part : query.trim().split("\\s+")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return List.copyOf(new ArrayList<>(tokens));
    }
}
