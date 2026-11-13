package com.myanimewatchledger.infrastructure.mal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class MalSeasonalCatalogueReaderTest {
    private static final URI BASE = URI.create("https://api.myanimelist.net/v2/");

    @Test
    void readsEveryPagePreservesFirstDisplayOrderAndMergesDuplicateMetadata() {
        StubTransport transport = new StubTransport();
        transport.responses.add("""
                {"data":[{"node":{"id":1,"title":"One"}},{"node":{"id":2,"title":"Two"}}],
                 "paging":{"next":"https://api.myanimelist.net/v2/anime/season/2026/fall?offset=2&limit=2"}}
                """);
        transport.responses.add("""
                {"data":[{"node":{"id":2,"title":"Two duplicate","mean":8.1}},{"node":{"id":3,"title":"Three"}}],
                 "paging":{}}
                """);

        var result = reader(transport).read("anime/season/2026/fall?offset=0&limit=2");

        assertEquals(3, result.anime().size());
        assertEquals(1, result.anime().get(0).malId());
        assertEquals(2, result.anime().get(1).malId());
        assertEquals(3, result.anime().get(2).malId());
        assertTrue(result.anime().get(1).metadataJson().contains("Two duplicate"));
        assertTrue(result.anime().get(1).metadataJson().contains("8.1"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Duplicate MAL ID")));
        assertEquals(1, transport.nextPageCalls);
    }

    @Test
    void acceptsAValidEmptyCatalogue() {
        StubTransport transport = new StubTransport();
        transport.responses.add("{\"data\":[],\"paging\":{}}");
        var result = reader(transport).read("anime/season/2026/fall?offset=0&limit=2");
        assertTrue(result.anime().isEmpty());
        assertEquals(0, result.skippedItems());
    }

    @Test
    void skipsAnItemWithInvalidIdentityAndReportsIt() {
        StubTransport transport = new StubTransport();
        transport.responses.add("""
                {"data":[{"node":{"title":"No ID"}},{"node":{"id":5,"title":"Valid"}}],"paging":{}}
                """);
        var result = reader(transport).read("anime/season/2026/fall?offset=0&limit=2");
        assertEquals(1, result.anime().size());
        assertEquals(1, result.skippedItems());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void failsMalformedPageStructureAndInvalidJson() {
        StubTransport missingData = new StubTransport();
        missingData.responses.add("{\"paging\":{}}");
        assertThrows(MalResponseMappingException.class,
                () -> reader(missingData).read("anime/season/2026/fall"));
        StubTransport invalidJson = new StubTransport();
        invalidJson.responses.add("{");
        assertThrows(MalResponseMappingException.class,
                () -> reader(invalidJson).read("anime/season/2026/fall"));
    }

    @Test
    void rejectsForeignHostAndRepeatedPaginationUrls() {
        StubTransport foreign = new StubTransport();
        foreign.responses.add("""
                {"data":[],"paging":{"next":"https://example.com/v2/page"}}
                """);
        assertThrows(MalResponseMappingException.class,
                () -> reader(foreign).read("anime/season/2026/fall"));

        StubTransport repeated = new StubTransport();
        String next = "https://api.myanimelist.net/v2/anime/season/2026/fall?offset=0";
        repeated.responses.add("{\"data\":[],\"paging\":{\"next\":\"" + next + "\"}}");
        repeated.responses.add("{\"data\":[],\"paging\":{\"next\":\"" + next + "\"}}");
        assertThrows(MalResponseMappingException.class,
                () -> reader(repeated).read("anime/season/2026/fall"));
    }

    @Test
    void rejectsNonTextPaginationAndMalformedPagingObjects() {
        StubTransport numericNext = new StubTransport();
        numericNext.responses.add("{\"data\":[],\"paging\":{\"next\":12}}");
        assertThrows(MalResponseMappingException.class,
                () -> reader(numericNext).read("anime/season/2026/fall"));

        StubTransport malformedPaging = new StubTransport();
        malformedPaging.responses.add("{\"data\":[],\"paging\":[]}"); 
        assertThrows(MalResponseMappingException.class,
                () -> reader(malformedPaging).read("anime/season/2026/fall"));
    }

    private static MalSeasonalCatalogueReader reader(StubTransport transport) {
        return new MalSeasonalCatalogueReader(transport, BASE, new MalResponseMapper(), new ObjectMapper());
    }

    private static final class StubTransport implements MalApiTransport {
        private final Queue<String> responses = new ArrayDeque<>();
        private int nextPageCalls;

        @Override public String get(String relativePathAndQuery) { return responses.remove(); }
        @Override public String getNextPage(URI nextPageUri) {
            nextPageCalls++;
            return responses.remove();
        }
    }
}
