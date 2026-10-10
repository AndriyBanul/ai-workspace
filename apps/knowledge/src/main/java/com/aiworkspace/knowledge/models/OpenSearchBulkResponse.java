package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchBulkResponse(Boolean errors) {
}
