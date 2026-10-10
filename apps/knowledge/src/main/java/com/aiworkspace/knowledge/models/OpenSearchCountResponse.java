package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchCountResponse(Integer count) {
}
