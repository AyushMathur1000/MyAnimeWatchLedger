package com.myanimewatchledger.infrastructure.mal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.domain.catalogue.AnimeMetadata;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MalResponseMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MalResponseMapper mapper = new MalResponseMapper();

    @Test
    void mapsMetadataAndPrefersEnglishTitle() throws Exception {
        var node = json.readTree("""
                {"id":10,"title":"Canonical","alternative_titles":{"en":"English","ja":"日本語","synonyms":["Alias"]},
                 "main_picture":{"medium":"https://img/m.jpg","large":"https://img/l.jpg"},
                 "mean":8.25,"rank":12,"num_episodes":24,
                 "genres":[{"id":1,"name":"Fantasy"}],"studios":[{"id":2,"name":"Studio"}]}
                """);
        AnimeMetadata mapped = mapper.mapAnime(node);
        assertEquals(10, mapped.malId());
        assertEquals("English", mapped.preferredTitle());
        assertEquals("Canonical", mapped.canonicalTitle());
        assertEquals("日本語", mapped.japaneseTitle());
        assertEquals(8.25, mapped.meanScore(), 0.0001);
        assertEquals(24, mapped.episodeCount());
        assertEquals("Fantasy", mapped.genres().getFirst().name());
        assertEquals("Studio", mapped.studios().getFirst().name());
    }

    @Test
    void usesRomanizedThenJapaneseThenCanonicalTitleAsFallbacks() throws Exception {
        var romanized = json.readTree("""
                {"id":11,"title":"Canonical","alternative_titles":{"romanized":"Romanized","ja":"日本語"}}
                """);
        var japanese = json.readTree("""
                {"id":12,"title":"Canonical","alternative_titles":{"ja":"日本語"}}
                """);
        var canonical = json.readTree("{\"id\":13,\"title\":\"Canonical\"}");
        assertEquals("Romanized", mapper.mapAnime(romanized).preferredTitle());
        assertEquals("日本語", mapper.mapAnime(japanese).preferredTitle());
        assertEquals("Canonical", mapper.mapAnime(canonical).preferredTitle());
    }

    @Test
    void preservesMissingNullableValuesInsteadOfInventingZeros() throws Exception {
        var node = json.readTree("""
                {"id":14,"title":"No Data","mean":null,"rank":null,"num_episodes":null}
                """);
        AnimeMetadata mapped = mapper.mapAnime(node);
        assertNull(mapped.meanScore());
        assertNull(mapped.rank());
        assertNull(mapped.episodeCount());
        assertNull(mapped.pictureMedium());
        assertTrue(mapped.genres().isEmpty());
    }

    @Test
    void ignoresUnknownFieldsAndRetainsUnknownEnumValues() throws Exception {
        var node = json.readTree("""
                {"id":15,"title":"Future","media_type":"future_type",
                 "status":"future_status","unknown_field":{"new":true}}
                """);
        AnimeMetadata mapped = mapper.mapAnime(node);
        assertEquals("future_type", mapped.mediaType());
        assertEquals("future_status", mapped.airingStatus());
    }

    @Test
    void requiresPositiveIdAndUsableTitle() throws Exception {
        var missingId = json.readTree("{\"title\":\"No ID\"}");
        var missingTitle = json.readTree("{\"id\":16}");
        assertThrows(MalResponseMappingException.class, () -> mapper.mapAnime(missingId));
        assertThrows(MalResponseMappingException.class, () -> mapper.mapAnime(missingTitle));
    }

    @Test
    void createsCacheProjectionWithSearchableAlternativeTitlesAndMetadata() throws Exception {
        var node = json.readTree("""
                {"id":17,"title":"Canonical","alternative_titles":{"en":"English","ja":"日本語",
                 "synonyms":["Alias"]},"synopsis":"A space fantasy","genres":[{"name":"Sci-Fi"}]}
                """);
        AnimeMetadataCache cached = mapper.mapToCache(node);
        assertEquals("English", cached.preferredTitle());
        assertTrue(cached.searchableText().contains("alias"));
        assertTrue(cached.searchableText().contains("space fantasy"));
        assertTrue(cached.searchableText().contains("sci-fi"));
        assertTrue(cached.metadataJson().contains("Canonical"));
    }

    @Test
    void conservativeMergeKeepsExistingValuesWhenIncomingMetadataIsNullOrEmpty() throws Exception {
        AnimeMetadata existing = mapper.mapAnime(json.readTree("""
                {"id":18,"title":"Old","alternative_titles":{"en":"English"},
                 "mean":8.5,"genres":[{"id":1,"name":"Fantasy"}]}
                """));
        AnimeMetadata incoming = mapper.mapAnime(json.readTree("""
                {"id":18,"title":"New Canonical","mean":null,"genres":[]}
                """));

        AnimeMetadata merged = mapper.merge(existing, incoming);

        assertEquals("English", merged.preferredTitle());
        assertEquals(8.5, merged.meanScore(), 0.0001);
        assertEquals("Fantasy", merged.genres().getFirst().name());
        assertEquals("New Canonical", merged.canonicalTitle());
    }
}
