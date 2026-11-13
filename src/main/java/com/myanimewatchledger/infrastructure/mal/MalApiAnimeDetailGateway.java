package com.myanimewatchledger.infrastructure.mal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.application.port.MalAnimeDetailGateway;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;

import java.util.Objects;

/** MAL API adapter for a single anime detail endpoint. */
public final class MalApiAnimeDetailGateway implements MalAnimeDetailGateway {
    private final MalApiTransport transport;
    private final MalResponseMapper mapper;
    private final ObjectMapper json;

    public MalApiAnimeDetailGateway(MalApiTransport transport, MalResponseMapper mapper) {
        this(transport, mapper, new ObjectMapper());
    }

    MalApiAnimeDetailGateway(MalApiTransport transport, MalResponseMapper mapper, ObjectMapper json) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public AnimeMetadataCache fetchAnime(long malId) {
        if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");
        String response = transport.get("anime/" + malId + "?fields=" + MalApiFields.queryValue());
        try {
            JsonNode root = json.readTree(response);
            AnimeMetadataCache result = mapper.mapToCache(root);
            if (result.malId() != malId) {
                throw new MalResponseMappingException("MAL returned a different anime ID than requested.");
            }
            return result;
        } catch (MalResponseMappingException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new MalResponseMappingException("MAL returned an unreadable anime detail response.", failure);
        }
    }
}
