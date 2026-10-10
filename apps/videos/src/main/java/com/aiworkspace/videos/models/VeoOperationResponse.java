package com.aiworkspace.videos.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VeoOperationResponse(String name, boolean done, VeoErrorResponse error, VeoGenerationResponse response) {
}
