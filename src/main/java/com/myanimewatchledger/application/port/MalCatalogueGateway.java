package com.myanimewatchledger.application.port;

import com.myanimewatchledger.domain.catalogue.Season;
import com.myanimewatchledger.domain.catalogue.SeasonalCatalogue;

/** Application-facing boundary for retrieving a complete MAL seasonal catalogue. */
public interface MalCatalogueGateway {
    SeasonalCatalogue fetchSeason(Season season, int year);
}
