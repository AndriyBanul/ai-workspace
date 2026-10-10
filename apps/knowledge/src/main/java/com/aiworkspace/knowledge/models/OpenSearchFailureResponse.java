package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchFailureResponse(String index, Integer status) {
}
