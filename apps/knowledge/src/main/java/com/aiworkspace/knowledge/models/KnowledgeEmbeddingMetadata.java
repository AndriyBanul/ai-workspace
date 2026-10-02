package com.aiworkspace.knowledge.models;

import java.util.List;
import lombok.Builder;

/** Vector representation and the information required to reproduce it. */
@Builder
public record KnowledgeEmbeddingMetadata(
        List<Float> vector,
        String model,
        Integer dimensions,
        String contentHash
) {

    public KnowledgeEmbeddingMetadata {
        vector = vector == null ? null : List.copyOf(vector);
    }
}
