package com.myanimewatchledger.infrastructure.mal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MalApiFieldsTest {
    @Test
    void exposesExplicitCommaSeparatedMetadataSelectionWithoutSpaces() {
        String value = MalApiFields.queryValue();
        assertTrue(MalApiFields.fields().contains("alternative_titles"));
        assertTrue(MalApiFields.fields().contains("average_episode_duration"));
        assertTrue(value.contains("id,title,main_picture,alternative_titles"));
        assertFalse(value.contains(" "));
    }
}
