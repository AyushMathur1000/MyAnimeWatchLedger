package com.myanimewatchledger.application.port;

import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;
import java.util.List;

/** Persistence boundary for the user's open Anime Details tabs. */
public interface OpenAnimeDetailTabRepository {
    List<OpenAnimeDetailTab> findAllOrdered();
    void replaceAll(List<OpenAnimeDetailTab> tabs);
    boolean close(long malId);
}
