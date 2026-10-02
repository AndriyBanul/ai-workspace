package com.aiworkspace.knowledge.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeRerankingPropertiesTest {

    @Test
    void suppliesOperationalDefaultsWhenEnabled() {
        var properties = new KnowledgeRerankingProperties(
                true, null, " model ", " tokenizer ", null, null, null, null, null);

        assertEquals("cross-encoder/mmarco-mMiniLMv2-L12-H384-v1", properties.model());
        assertEquals("model", properties.modelUri());
        assertEquals("tokenizer", properties.tokenizerUri());
        assertEquals(100, properties.candidateLimit());
        assertEquals(12, properties.resultLimit());
        assertEquals(16, properties.batchSize());
        assertEquals(512, properties.maxTokens());
    }

    @Test
    void disabledConfigurationDoesNotRequireModelResources() {
        assertFalse(KnowledgeRerankingProperties.disabled().enabled());
    }

    @Test
    void rejectsCandidateLimitBelowResultLimit() {
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeRerankingProperties(
                true, null, "model", "tokenizer", null, 11, 12, null, null));
    }
}
