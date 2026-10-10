package com.aiworkspace.audio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WyomingEventHeader(
        String type,
        @JsonProperty("data_length") int dataLength,
        @JsonProperty("payload_length") int payloadLength
) {
}
