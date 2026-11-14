package com.myanimewatchledger.presentation.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MalImageLoaderTest {
    @Test
    void acceptsHttpsUrlsFromKnownMalImageHosts() {
        assertTrue(MalImageLoader.isAllowedUrl("https://cdn.myanimelist.net/images/anime/1/1.jpg"));
        assertTrue(MalImageLoader.isAllowedUrl("https://api-cdn.myanimelist.net/images/anime/1/1.jpg"));
        assertTrue(MalImageLoader.isAllowedUrl("https://myanimelist.cdn-dena.com/images/anime/1/1.jpg"));
    }

    @Test
    void rejectsNonHttpsAndUntrustedHosts() {
        assertFalse(MalImageLoader.isAllowedUrl("http://cdn.myanimelist.net/images/anime/1/1.jpg"));
        assertFalse(MalImageLoader.isAllowedUrl("https://example.com/images/anime/1/1.jpg"));
        assertFalse(MalImageLoader.isAllowedUrl("file:///tmp/poster.jpg"));
        assertFalse(MalImageLoader.isAllowedUrl("https://cdn.myanimelist.net.example.com/poster.jpg"));
    }

    @Test
    void rejectsCredentialsAndUnexpectedPorts() {
        assertFalse(MalImageLoader.isAllowedUrl("https://user@cdn.myanimelist.net/poster.jpg"));
        assertFalse(MalImageLoader.isAllowedUrl("https://cdn.myanimelist.net:8443/poster.jpg"));
        assertTrue(MalImageLoader.isAllowedUrl("https://cdn.myanimelist.net:443/poster.jpg"));
    }

    @Test
    void rejectsMalformedAndBlankValues() {
        assertFalse(MalImageLoader.isAllowedUrl(null));
        assertFalse(MalImageLoader.isAllowedUrl("  "));
        assertFalse(MalImageLoader.isAllowedUrl("not a URL"));
    }

    @Test
    void prefersValidPrimaryUrlAndFallsBackWhenItIsUnusable() {
        String primary = "https://cdn.myanimelist.net/large.jpg";
        String fallback = "https://api-cdn.myanimelist.net/medium.jpg";

        assertEquals(primary, MalImageLoader.selectUrl(primary, fallback));
        assertEquals(fallback, MalImageLoader.selectUrl("https://example.com/poster.jpg", fallback));
        assertEquals(fallback, MalImageLoader.selectUrl(null, fallback));
        assertNull(MalImageLoader.selectUrl("https://example.com/poster.jpg", null));
    }
}
