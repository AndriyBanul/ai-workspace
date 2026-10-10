package com.aiworkspace.knowledge.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchDeleteResponse(
        @JsonProperty("timed_out") Boolean timedOut,
        List<OpenSearchFailureResponse> failures
) {
}
