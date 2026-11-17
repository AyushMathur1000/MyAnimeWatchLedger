package com.myanimewatchledger.domain.recommendation;

import com.myanimewatchledger.domain.catalogue.AnimeMetadata;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, locally-derived preference profile.
 *
 * Only explicit LIKE/DISLIKE choices are used as taste labels. Ledger status
 * alone is deliberately not interpreted as proof that an anime was liked or
 * disliked.
 */
public record AnimePreferenceProfile(
        Map<String, Double> genreAffinity,
        Map<String, Double> studioAffinity,
        Map<String, Double> typeAffinity,
        int explicitFeedbackCount) {

    public AnimePreferenceProfile {
        genreAffinity = Map.copyOf(genreAffinity == null ? Map.of() : genreAffinity);
        studioAffinity = Map.copyOf(studioAffinity == null ? Map.of() : studioAffinity);
        typeAffinity = Map.copyOf(typeAffinity == null ? Map.of() : typeAffinity);
        if (explicitFeedbackCount < 0) {
            throw new IllegalArgumentException("Feedback count cannot be negative.");
        }
    }

    public static AnimePreferenceProfile empty() {
        return new AnimePreferenceProfile(Map.of(), Map.of(), Map.of(), 0);
    }

    public static AnimePreferenceProfile fromSignals(Collection<AnimePreferenceSignal> signals) {
        Objects.requireNonNull(signals, "signals");
        Map<String, Totals> genres = new LinkedHashMap<>();
        Map<String, Totals> studios = new LinkedHashMap<>();
        Map<String, Totals> types = new LinkedHashMap<>();
        int count = 0;

        for (AnimePreferenceSignal signal : signals) {
            if (signal == null) continue;
            double value = signal.preference() == com.myanimewatchledger.domain.ledger.AnimePreference.LIKE
                    ? 1.0 : -1.0;
            AnimeMetadata metadata = signal.metadata();
            addNames(genres, metadata.genres().stream().map(AnimeMetadata.NamedValue::name).toList(), value);
            addNames(studios, metadata.studios().stream().map(AnimeMetadata.NamedValue::name).toList(), value);
            addNames(types, metadata.mediaType() == null ? java.util.List.of()
                    : java.util.List.of(metadata.mediaType()), value);
            count++;
        }

        return new AnimePreferenceProfile(averages(genres), averages(studios), averages(types), count);
    }

    private static void addNames(Map<String, Totals> target, Iterable<String> names, double value) {
        for (String name : names) {
            String key = normalize(name);
            if (!key.isEmpty()) target.computeIfAbsent(key, ignored -> new Totals()).add(value);
        }
    }

    private static Map<String, Double> averages(Map<String, Totals> source) {
        Map<String, Double> result = new LinkedHashMap<>();
        source.forEach((key, totals) -> result.put(key, totals.average()));
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Totals {
        private double sum;
        private int count;
        private void add(double value) { sum += value; count++; }
        private double average() { return count == 0 ? 0.0 : sum / count; }
    }
}
