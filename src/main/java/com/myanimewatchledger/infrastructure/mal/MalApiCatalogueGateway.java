package com.myanimewatchledger.infrastructure.mal;

import com.myanimewatchledger.application.port.MalCatalogueGateway;
import com.myanimewatchledger.domain.catalogue.Season;
import com.myanimewatchledger.domain.catalogue.SeasonalCatalogue;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** MAL-backed adapter that converts complete seasonal API responses to application models. */
public final class MalApiCatalogueGateway implements MalCatalogueGateway {
    /**
     * Conservative initial page size. Pagination remains sequential and follows
     * MAL's paging.next link until the complete response has been read.
     */
    static final int PAGE_SIZE = 100;

    private final MalSeasonalCatalogueReader reader;

    public MalApiCatalogueGateway(MalApiTransport transport, URI apiBaseUri) {
        this(transport, apiBaseUri, new MalResponseMapper());
    }

    public MalApiCatalogueGateway(
            MalApiTransport transport, URI apiBaseUri, MalResponseMapper mapper) {
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(apiBaseUri, "apiBaseUri");
        Objects.requireNonNull(mapper, "mapper");
        this.reader = new MalSeasonalCatalogueReader(transport, apiBaseUri, mapper);
    }

    @Override
    public SeasonalCatalogue fetchSeason(Season season, int year) {
        Objects.requireNonNull(season, "season");
        if (year < 1900 || year > 9999) {
            throw new IllegalArgumentException("Year is out of range.");
        }

        String endpoint = "anime/season/" + year + "/"
                + season.name().toLowerCase(Locale.ROOT)
                + "?limit=" + PAGE_SIZE
                + "&fields=" + MalApiFields.queryValue();

        MalSeasonalCatalogueReader.SeasonalCatalogueResult result = reader.read(endpoint);
        return new SeasonalCatalogue(result.anime(), result.skippedItems(), result.warnings());
    }
}
