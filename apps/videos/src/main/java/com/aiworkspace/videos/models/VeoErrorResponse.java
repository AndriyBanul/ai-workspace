package com.aiworkspace.videos.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VeoErrorResponse(Integer code, String message) {
}
