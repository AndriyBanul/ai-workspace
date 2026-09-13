package com.aiworkspace.knowledge.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeQueryExpansionPropertiesTest {

    @Test
    void enablesTwoQueriesByDefault() {
        var properties = new KnowledgeQueryExpansionProperties(null, null);

        assertTrue(properties.enabled());
        assertEquals(2, properties.queryLimit());
    }

    @Test
    void rejectsMoreThanFourQueries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeQueryExpansionProperties(true, 5)
        );
    }
}
