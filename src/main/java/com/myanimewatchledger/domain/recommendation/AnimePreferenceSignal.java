package com.myanimewatchledger.domain.recommendation;

import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.ledger.AnimePreference;

import java.util.Objects;

/** Explicit user feedback used to build a local anime preference profile. */
public record AnimePreferenceSignal(AnimeMetadata metadata, AnimePreference preference) {
    public AnimePreferenceSignal {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(preference, "preference");
    }
}
