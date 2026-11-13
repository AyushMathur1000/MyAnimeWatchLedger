package com.myanimewatchledger.domain.catalogue;

import java.text.Normalizer;
import java.util.Locale;

/** Shared Unicode compatibility normalization and locale-stable lowercase projection for search. */
public final class SearchTextNormalizer {
    private SearchTextNormalizer() { }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
    }
}
