package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.CatalogueRepository;
import com.myanimewatchledger.domain.catalogue.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CatalogueSearchServiceTest {
    @Test
    void tokenizationUsesNormalizedDistinctWhitespaceSeparatedAndTerms() {
        assertEquals(List.of("one", "two", "café"),
                CatalogueSearchService.tokenize("  ONE   two one  Café "));
    }

    @Test
    void blankQueryProducesNoTokensAndSearchDelegatesAndTerms() {
        RecordingRepository repository = new RecordingRepository();
        CatalogueSearchService service = new CatalogueSearchService(repository);

        assertTrue(service.search(7, "  ").isEmpty());
        assertEquals(List.of(), repository.tokens);

        service.search(7, "Space Opera 2026");
        assertEquals(List.of("space", "opera", "2026"), repository.tokens);
        assertEquals(7, repository.searchId);
    }

    @Test
    void rejectsInvalidSearchIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogueSearchService(new RecordingRepository()).search(0, "test"));
    }

    private static final class RecordingRepository implements CatalogueRepository {
        private List<String> tokens = List.of();
        private long searchId;
        public CatalogueSearch findOrCreate(Season season, int year) { throw new UnsupportedOperationException(); }
        public Optional<CatalogueSearch> findById(long id) { return Optional.empty(); }
        public List<CatalogueSearch> findAll() { return List.of(); }
        public void markFetchSucceeded(long id, Instant at) { throw new UnsupportedOperationException(); }
        public void markFetchFailed(long id, Instant at, String error) { throw new UnsupportedOperationException(); }
        public void replaceMembership(long id, List<Long> ids) { throw new UnsupportedOperationException(); }
        public List<CatalogueAnime> findEligibleAnime(long id, List<String> andTokens) {
            searchId = id;
            tokens = List.copyOf(andTokens);
            return List.of();
        }
        public void clearCache() { throw new UnsupportedOperationException(); }
    }
}
