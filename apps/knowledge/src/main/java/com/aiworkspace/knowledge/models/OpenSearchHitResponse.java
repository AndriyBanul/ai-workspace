package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchHitResponse(
        @JsonProperty("_id") String id,
        @JsonProperty("_score") double score,
        @JsonProperty("_source") OpenSearchKnowledgeDocument source
) {
}
