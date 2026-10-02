package com.aiworkspace.documents.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebPageFetchPropertiesTest {

    @Test
    void usesProductionDefaultsWhenPropertiesAreMissing() {
        WebPageFetchProperties properties = new WebPageFetchProperties(null, null);

        assertEquals(5, properties.maxRedirects());
        assertEquals(2 * 1024 * 1024, properties.maxResponseBytes());
    }

    @Test
    void rejectsNegativeRedirectLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new WebPageFetchProperties(-1, null)
        );

        assertEquals("Web page fetch max redirects must not be negative", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveResponseLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new WebPageFetchProperties(null, 0)
        );

        assertEquals("Web page fetch max response bytes must be positive", exception.getMessage());
    }
}
