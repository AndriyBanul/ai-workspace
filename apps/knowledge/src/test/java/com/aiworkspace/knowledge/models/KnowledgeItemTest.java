package com.aiworkspace.knowledge.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeItemTest {

    @Test
    void exposesComposedMetadataThroughCompatibilityAccessors() {
        Instant extractedAt = Instant.parse("2026-09-15T07:00:00Z");
        KnowledgeItem item = KnowledgeItem.builder()
                .source(KnowledgeItemSource.builder()
                        .type(KnowledgeSourceType.AUDIO)
                        .name("meeting.mp3")
                        .id("source-1")
                        .extractedAt(extractedAt)
                        .build())
                .chunkMetadata(KnowledgeChunkMetadata.builder()
                        .id("source-1:2")
                        .sequence(2)
                        .startMilliseconds(1_000L)
                        .speaker("Alice")
                        .build())
                .embeddingMetadata(new KnowledgeEmbeddingMetadata(
                        List.of(1.0f, 0.0f), "embedding-model", 2, "hash"))
                .build();

        assertEquals(KnowledgeSourceType.AUDIO, item.sourceType());
        assertEquals("meeting.mp3", item.sourceName());
        assertEquals("source-1", item.sourceId());
        assertEquals(extractedAt, item.extractedAt());
        assertEquals("source-1:2", item.chunkId());
        assertEquals(2, item.chunkSequence());
        assertEquals(1_000L, item.startMilliseconds());
        assertEquals("Alice", item.speaker());
        assertEquals(List.of(1.0f, 0.0f), item.embedding());
        assertEquals("embedding-model", item.embeddingModel());
        assertEquals(2, item.embeddingDimensions());
        assertEquals("hash", item.contentHash());
    }

    @Test
    void protectsEmbeddingVectorFromExternalMutation() {
        List<Float> vector = new ArrayList<>(List.of(1.0f, 0.0f));
        KnowledgeEmbeddingMetadata metadata = new KnowledgeEmbeddingMetadata(vector, "model", 2, "hash");

        vector.set(0, 0.5f);

        assertEquals(List.of(1.0f, 0.0f), metadata.vector());
        assertThrows(UnsupportedOperationException.class, () -> metadata.vector().add(0.5f));
    }
}
