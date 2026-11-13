package com.myanimewatchledger.infrastructure.mal;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MalApiAnimeDetailGatewayTest {
    @Test
    void requestsOneAnimeWithTheSharedMetadataFieldSelection() {
        AtomicReference<String> endpoint = new AtomicReference<>();
        MalApiAnimeDetailGateway gateway = new MalApiAnimeDetailGateway(transport(path -> {
            endpoint.set(path);
            return """
                    {"id":205,"title":"Detail","synopsis":"A complete synopsis"}
                    """;
        }), new MalResponseMapper());

        var result = gateway.fetchAnime(205);

        assertEquals(205, result.malId());
        assertEquals("anime/205?fields=" + MalApiFields.queryValue(), endpoint.get());
        assertTrue(result.metadataJson().contains("A complete synopsis"));
    }

    @Test
    void rejectsInvalidIdsBeforeCallingTransport() {
        MalApiAnimeDetailGateway gateway = new MalApiAnimeDetailGateway(transport(path -> {
            fail("Transport must not be called for an invalid ID.");
            return "";
        }), new MalResponseMapper());

        assertThrows(IllegalArgumentException.class, () -> gateway.fetchAnime(-1));
    }

    @Test
    void rejectsMalformedAndMismatchedResponses() {
        MalApiAnimeDetailGateway malformed = new MalApiAnimeDetailGateway(
                transport(path -> "not-json"), new MalResponseMapper());
        MalApiAnimeDetailGateway mismatch = new MalApiAnimeDetailGateway(
                transport(path -> "{\"id\":999,\"title\":\"Wrong\"}"), new MalResponseMapper());

        assertThrows(MalResponseMappingException.class, () -> malformed.fetchAnime(205));
        assertThrows(MalResponseMappingException.class, () -> mismatch.fetchAnime(205));
    }

    private static MalApiTransport transport(Function<String, String> getHandler) {
        return new MalApiTransport() {
            @Override
            public String get(String relativePathAndQuery) {
                return getHandler.apply(relativePathAndQuery);
            }

            @Override
            public String getNextPage(URI nextPageUri) {
                throw new UnsupportedOperationException("Pagination is not used by detail requests.");
            }
        };
    }
}
