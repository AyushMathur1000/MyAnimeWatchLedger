package com.myanimewatchledger.infrastructure.mal;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;

/** Parsed seasonal page envelope; items remain transport JSON until mapped. */
public record MalSeasonalCataloguePage(List<JsonNode> animeNodes, URI nextPageUri) {
    public MalSeasonalCataloguePage {
        animeNodes = List.copyOf(animeNodes);
    }
}
