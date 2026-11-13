package com.myanimewatchledger.infrastructure.mal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import com.myanimewatchledger.domain.catalogue.SearchTextNormalizer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/** Converts MAL JSON nodes into application-owned metadata and cache projections. */
public final class MalResponseMapper {
    private final ObjectMapper objectMapper;

    public MalResponseMapper() {
        this(new ObjectMapper());
    }

    public MalResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnimeMetadata mapAnime(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new MalResponseMappingException("MAL anime record must be a JSON object.");
        }
        long malId = positiveLong(node.get("id"));
        String canonical = text(node, "title");
        JsonNode alternatives = node.path("alternative_titles");
        String english = text(alternatives, "en");
        String romanized = text(alternatives, "romanized");
        String japanese = text(alternatives, "ja");
        List<String> synonyms = textList(alternatives.path("synonyms"));
        String preferred = firstNonBlank(english, romanized, japanese, canonical);
        if (preferred == null) {
            throw new MalResponseMappingException("MAL anime record has no usable title (id=" + malId + ").");
        }

        JsonNode picture = node.path("main_picture");
        JsonNode startSeason = node.path("start_season");
        JsonNode broadcast = node.path("broadcast");
        return new AnimeMetadata(
                malId, canonical, english, romanized, japanese, synonyms, preferred,
                text(picture, "medium"), text(picture, "large"),
                text(node, "start_date"), text(node, "end_date"), text(node, "synopsis"),
                decimal(node, "mean"), integer(node, "rank"), integer(node, "popularity"),
                integer(node, "num_list_users"), integer(node, "num_scoring_users"),
                text(node, "media_type"), text(node, "status"), integer(node, "num_episodes"),
                integer(startSeason, "year"), text(startSeason, "season"),
                text(broadcast, "day_of_the_week"), text(broadcast, "start_time"),
                text(node, "source"), namedValues(node.path("genres")), namedValues(node.path("studios")),
                integer(node, "average_episode_duration"), text(node, "rating"),
                scalarText(node.get("nsfw")), text(node, "background"));
    }

    public AnimeMetadataCache mapToCache(JsonNode node) {
        return toCache(mapAnime(node));
    }

    public AnimeMetadataCache toCache(AnimeMetadata metadata) {
        String json;
        try {
            json = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new MalResponseMappingException("Could not serialize mapped MAL anime metadata.", exception);
        }
        return new AnimeMetadataCache(
                metadata.malId(), metadata.preferredTitle(), json, searchableText(metadata), Instant.now());
    }

    /**
     * Conservative merge: non-null incoming scalar values replace cached values;
     * empty incoming collections do not erase previously known collection values.
     */
    public AnimeMetadata merge(AnimeMetadata existing, AnimeMetadata incoming) {
        if (existing.malId() != incoming.malId()) {
            throw new IllegalArgumentException("Cannot merge metadata for different MAL IDs.");
        }
        String canonical = pick(incoming.canonicalTitle(), existing.canonicalTitle());
        String english = pick(incoming.englishTitle(), existing.englishTitle());
        String romanized = pick(incoming.romanizedTitle(), existing.romanizedTitle());
        String japanese = pick(incoming.japaneseTitle(), existing.japaneseTitle());
        String preferred = firstNonBlank(english, romanized, japanese, canonical, existing.preferredTitle());
        return new AnimeMetadata(
                existing.malId(), canonical, english, romanized, japanese,
                pickList(incoming.synonyms(), existing.synonyms()), preferred,
                pick(incoming.pictureMedium(), existing.pictureMedium()),
                pick(incoming.pictureLarge(), existing.pictureLarge()),
                pick(incoming.startDate(), existing.startDate()), pick(incoming.endDate(), existing.endDate()),
                pick(incoming.synopsis(), existing.synopsis()), pick(incoming.meanScore(), existing.meanScore()),
                pick(incoming.rank(), existing.rank()), pick(incoming.popularity(), existing.popularity()),
                pick(incoming.listUserCount(), existing.listUserCount()),
                pick(incoming.scoringUserCount(), existing.scoringUserCount()),
                pick(incoming.mediaType(), existing.mediaType()), pick(incoming.airingStatus(), existing.airingStatus()),
                pick(incoming.episodeCount(), existing.episodeCount()),
                pick(incoming.seasonYear(), existing.seasonYear()), pick(incoming.season(), existing.season()),
                pick(incoming.broadcastDay(), existing.broadcastDay()),
                pick(incoming.broadcastStartTime(), existing.broadcastStartTime()),
                pick(incoming.source(), existing.source()),
                pickList(incoming.genres(), existing.genres()), pickList(incoming.studios(), existing.studios()),
                pick(incoming.averageEpisodeDurationSeconds(), existing.averageEpisodeDurationSeconds()),
                pick(incoming.contentRating(), existing.contentRating()), pick(incoming.nsfw(), existing.nsfw()),
                pick(incoming.background(), existing.background()));
    }

    public String searchableText(AnimeMetadata metadata) {
        StringJoiner searchable = new StringJoiner(" ");
        add(searchable, metadata.preferredTitle());
        add(searchable, metadata.canonicalTitle());
        add(searchable, metadata.englishTitle());
        add(searchable, metadata.romanizedTitle());
        add(searchable, metadata.japaneseTitle());
        metadata.synonyms().forEach(value -> add(searchable, value));
        add(searchable, metadata.synopsis());
        metadata.genres().forEach(value -> add(searchable, value.name()));
        metadata.studios().forEach(value -> add(searchable, value.name()));
        add(searchable, metadata.mediaType());
        add(searchable, metadata.airingStatus());
        add(searchable, metadata.season());
        if (metadata.seasonYear() != null) add(searchable, metadata.seasonYear().toString());
        add(searchable, metadata.source());
        add(searchable, metadata.contentRating());
        add(searchable, metadata.background());
        if (metadata.meanScore() != null) add(searchable, metadata.meanScore().toString());
        if (metadata.rank() != null) add(searchable, metadata.rank().toString());
        if (metadata.popularity() != null) add(searchable, metadata.popularity().toString());
        if (metadata.episodeCount() != null) add(searchable, metadata.episodeCount().toString());
        return SearchTextNormalizer.normalize(searchable.toString());
    }

    private static long positiveLong(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new MalResponseMappingException("MAL anime record is missing a valid positive id.");
        }
        return value.asLong();
    }

    private static String text(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode() || parent.isNull()) return null;
        JsonNode value = parent.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) return null;
        String result = value.asText();
        return result.isBlank() ? null : result;
    }

    private static String scalarText(JsonNode value) {
        if (value == null || value.isNull() || !value.isValueNode()) return null;
        return value.asText();
    }

    private static Integer integer(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }

    private static Double decimal(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        return value != null && value.isNumber() ? value.doubleValue() : null;
    }

    private static List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private static List<AnimeMetadata.NamedValue> namedValues(JsonNode array) {
        List<AnimeMetadata.NamedValue> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String name = text(item, "name");
                if (name == null) continue;
                JsonNode idNode = item.get("id");
                Long id = idNode != null && idNode.isIntegralNumber() && idNode.canConvertToLong() && idNode.asLong() > 0
                        ? idNode.asLong() : null;
                values.add(new AnimeMetadata.NamedValue(id, name));
            }
        }
        return List.copyOf(values);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate;
        }
        return null;
    }

    private static String pick(String incoming, String existing) {
        return incoming == null || incoming.isBlank() ? existing : incoming;
    }

    private static <T> T pick(T incoming, T existing) {
        return incoming == null ? existing : incoming;
    }

    private static <T> List<T> pickList(List<T> incoming, List<T> existing) {
        return incoming == null || incoming.isEmpty() ? existing : incoming;
    }

    private static void add(StringJoiner joiner, String value) {
        if (value != null && !value.isBlank()) joiner.add(value.trim());
    }
}
