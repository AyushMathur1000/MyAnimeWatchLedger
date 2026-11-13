package com.myanimewatchledger.domain.catalogue;

import java.util.List;

/** Application-owned representation of MAL anime metadata. Unknown enum values remain raw strings. */
public record AnimeMetadata(
        long malId,
        String canonicalTitle,
        String englishTitle,
        String romanizedTitle,
        String japaneseTitle,
        List<String> synonyms,
        String preferredTitle,
        String pictureMedium,
        String pictureLarge,
        String startDate,
        String endDate,
        String synopsis,
        Double meanScore,
        Integer rank,
        Integer popularity,
        Integer listUserCount,
        Integer scoringUserCount,
        String mediaType,
        String airingStatus,
        Integer episodeCount,
        Integer seasonYear,
        String season,
        String broadcastDay,
        String broadcastStartTime,
        String source,
        List<NamedValue> genres,
        List<NamedValue> studios,
        Integer averageEpisodeDurationSeconds,
        String contentRating,
        String nsfw,
        String background) {

    public AnimeMetadata {
        if (malId <= 0) throw new IllegalArgumentException("MAL ID must be positive.");
        if (preferredTitle == null || preferredTitle.isBlank()) {
            throw new IllegalArgumentException("Preferred title is required.");
        }
        synonyms = List.copyOf(synonyms == null ? List.of() : synonyms);
        genres = List.copyOf(genres == null ? List.of() : genres);
        studios = List.copyOf(studios == null ? List.of() : studios);
    }

    public record NamedValue(Long malId, String name) {
        public NamedValue {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required.");
        }
    }
}
