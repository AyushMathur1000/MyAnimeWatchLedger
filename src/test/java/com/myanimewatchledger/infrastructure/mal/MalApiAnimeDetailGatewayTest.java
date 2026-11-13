package com.myanimewatchledger.infrastructure.mal;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MalApiAnimeDetailGatewayTest {
    @Test
    void requestsOneAnimeWithTheSharedMetadataFieldSelection() {
        AtomicReference<String> endpoint = new AtomicReference<>();
        MalApiAnimeDetailGateway gateway = new MalApiAnimeDetailGateway(path -> {
            endpoint.set(path);
            return """
                    {"id":205,"title":"Detail","synopsis":"A complete synopsis"}
                    """;
        }, new MalResponseMapper());

        var result = gateway.fetchAnime(205);

        assertEquals(205, result.malId());
        assertEquals("anime/205?fields=" + MalApiFields.queryValue(), endpoint.get());
        assertTrue(result.metadataJson().contains("A complete synopsis"));
    }

    @Test
    void rejectsInvalidIdsBeforeCallingTransport() {
        MalApiAnimeDetailGateway gateway = new MalApiAnimeDetailGateway(path -> {
            fail("Transport must not be called for an invalid ID.");
            return "";
        }, new MalResponseMapper());

        assertThrows(IllegalArgumentException.class, () -> gateway.fetchAnime(-1));
    }

    @Test
    void rejectsMalformedAndMismatchedResponses() {
        MalApiAnimeDetailGateway malformed = new MalApiAnimeDetailGateway(path -> "not-json",
                new MalResponseMapper());
        MalApiAnimeDetailGateway mismatch = new MalApiAnimeDetailGateway(path ->
                "{\"id\":999,\"title\":\"Wrong\"}", new MalResponseMapper());

        assertThrows(MalResponseMappingException.class, () -> malformed.fetchAnime(205));
        assertThrows(MalResponseMappingException.class, () -> mismatch.fetchAnime(205));
    }
}
