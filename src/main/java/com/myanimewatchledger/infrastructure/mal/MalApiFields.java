package com.myanimewatchledger.infrastructure.mal;

import java.util.List;
import java.util.stream.Collectors;

/** Explicit MAL field selection shared by seasonal and detail requests. */
public final class MalApiFields {
    private static final List<String> FIELDS = List.of(
            "id", "title", "main_picture", "alternative_titles", "start_date", "end_date",
            "synopsis", "mean", "rank", "popularity", "num_list_users", "num_scoring_users",
            "media_type", "status", "num_episodes", "start_season", "broadcast", "source",
            "genres", "studios", "average_episode_duration", "rating", "nsfw", "background");

    private MalApiFields() { }

    public static List<String> fields() {
        return FIELDS;
    }

    public static String queryValue() {
        return FIELDS.stream().collect(Collectors.joining(","));
    }
}
