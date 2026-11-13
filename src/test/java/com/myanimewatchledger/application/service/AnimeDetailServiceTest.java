package com.myanimewatchledger.application.service;

import com.myanimewatchledger.application.port.AnimeMetadataRepository;
import com.myanimewatchledger.application.port.MalAnimeDetailGateway;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.infrastructure.mal.MalResponseMapper;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AnimeDetailServiceTest {
    private final MalResponseMapper mapper = new MalResponseMapper();

    @Test
    void returnsCompleteCachedMetadataWithoutCallingMal() throws Exception {
        AnimeMetadataCache cached = cache("""
                {"id":101,"title":"Cached","synopsis":"Already detailed"}
                """);
        InMemoryMetadataRepository repository = new InMemoryMetadataRepository(cached);
        MalAnimeDetailGateway gateway = id -> { throw new AssertionError("MAL should not be called"); };

        AnimeDetailResult result = new AnimeDetailService(repository, gateway, mapper).load(101);

        assertEquals(Optional.of(cached), result.metadata());
        assertFalse(result.refreshed());
        assertNull(result.warning());
    }

    @Test
    void fetchesMissingDetailAndMergesWithExistingCache() throws Exception {
        AnimeMetadataCache cached = cache("""
                {"id":102,"title":"Old title","alternative_titles":{"en":"Preferred"}}
                """);
        AnimeMetadataCache fetched = cache("""
                {"id":102,"title":"New title","synopsis":"Full synopsis","mean":8.4}
                """);
        InMemoryMetadataRepository repository = new InMemoryMetadataRepository(cached);
        AnimeDetailResult result = new AnimeDetailService(repository, id -> fetched, mapper).load(102);

        assertTrue(result.refreshed());
        assertEquals("Preferred", result.metadata().orElseThrow().preferredTitle());
        assertTrue(result.metadata().orElseThrow().metadataJson().contains("Full synopsis"));
        assertEquals(result.metadata(), repository.findByMalId(102));
    }

    @Test
    void retainsCachedMetadataAndReturnsWarningWhenMalFails() throws Exception {
        AnimeMetadataCache cached = cache("""
                {"id":103,"title":"Cached but incomplete"}
                """);
        InMemoryMetadataRepository repository = new InMemoryMetadataRepository(cached);
        AnimeDetailResult result = new AnimeDetailService(repository,
                id -> { throw new IllegalStateException("MAL is unavailable."); }, mapper).load(103);

        assertEquals(Optional.of(cached), result.metadata());
        assertFalse(result.refreshed());
        assertEquals("MAL is unavailable.", result.warning());
        assertEquals(Optional.of(cached), repository.findByMalId(103));
    }

    @Test
    void reportsUnavailableMalWithoutDiscardingCache() throws Exception {
        AnimeMetadataCache cached = cache("""
                {"id":104,"title":"Offline cached"}
                """);
        AnimeDetailResult result = new AnimeDetailService(
                new InMemoryMetadataRepository(cached), null, mapper).load(104);

        assertEquals(Optional.of(cached), result.metadata());
        assertNotNull(result.warning());
    }

    @Test
    void rejectsInvalidAnimeIds() {
        AnimeDetailService service = new AnimeDetailService(
                new InMemoryMetadataRepository(null), null, mapper);
        assertThrows(IllegalArgumentException.class, () -> service.load(0));
    }

    private AnimeMetadataCache cache(String body) throws Exception {
        return mapper.mapToCache(new com.fasterxml.jackson.databind.ObjectMapper().readTree(body));
    }

    private static final class InMemoryMetadataRepository implements AnimeMetadataRepository {
        private final Map<Long, AnimeMetadataCache> values = new HashMap<>();
        private InMemoryMetadataRepository(AnimeMetadataCache initial) {
            if (initial != null) values.put(initial.malId(), initial);
        }
        @Override public void upsert(AnimeMetadataCache value) { values.put(value.malId(), value); }
        @Override public void upsertAll(Collection<AnimeMetadataCache> values) {
            values.forEach(this::upsert);
        }
        @Override public Optional<AnimeMetadataCache> findByMalId(long malId) {
            return Optional.ofNullable(values.get(malId));
        }
        @Override public void deleteAll() { values.clear(); }
    }
}
