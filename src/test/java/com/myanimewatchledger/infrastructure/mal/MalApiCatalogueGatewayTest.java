package com.myanimewatchledger.infrastructure.mal;

import com.myanimewatchledger.domain.catalogue.Season;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MalApiCatalogueGatewayTest {
    @Test
    void buildsSeasonEndpointWithExplicitFieldsAndMapsTheResponse() {
        List<String> requested = new ArrayList<>();
        MalApiTransport transport = new MalApiTransport() {
            @Override public String get(String path) {
                requested.add(path);
                return """
                        {
                          "data": [
                            {"node": {"id": 123, "title": "Example Anime"}}
                          ]
                        }
                        """;
            }
            @Override public String getNextPage(URI uri) {
                fail("No next page was expected.");
                return "";
            }
        };

        var result = new MalApiCatalogueGateway(
                transport, URI.create("https://api.myanimelist.net/v2/"))
                .fetchSeason(Season.WINTER, 2026);

        assertEquals(1, result.anime().size());
        assertEquals(123, result.anime().getFirst().malId());
        assertTrue(requested.getFirst().startsWith("anime/season/2026/winter?limit=100&fields="));
        assertTrue(requested.getFirst().contains("id,title,main_picture"));
    }

    @Test
    void followsEverySeasonalPageBeforeReturningACompleteResult() {
        List<String> calls = new ArrayList<>();
        MalApiTransport transport = new MalApiTransport() {
            @Override public String get(String path) {
                calls.add("first");
                return """
                        {"data":[{"node":{"id":1,"title":"First"}}],
                         "paging":{"next":"https://api.myanimelist.net/v2/anime/season/2026/winter?offset=100"}}
                        """;
            }
            @Override public String getNextPage(URI uri) {
                calls.add("next");
                return """
                        {"data":[{"node":{"id":2,"title":"Second"}}]}
                        """;
            }
        };

        var result = new MalApiCatalogueGateway(
                transport, URI.create("https://api.myanimelist.net/v2/"))
                .fetchSeason(Season.WINTER, 2026);

        assertEquals(List.of("first", "next"), calls);
        assertEquals(List.of(1L, 2L), result.anime().stream().map(a -> a.malId()).toList());
    }

    @Test
    void rejectsInvalidYearBeforeCallingTransport() {
        MalApiTransport transport = new MalApiTransport() {
            @Override public String get(String path) {
                fail("Transport must not be called.");
                return "";
            }
            @Override public String getNextPage(URI uri) {
                fail("Transport must not be called.");
                return "";
            }
        };

        assertThrows(IllegalArgumentException.class, () ->
                new MalApiCatalogueGateway(transport, URI.create("https://api.myanimelist.net/v2/"))
                        .fetchSeason(Season.FALL, 1899));
    }
}
