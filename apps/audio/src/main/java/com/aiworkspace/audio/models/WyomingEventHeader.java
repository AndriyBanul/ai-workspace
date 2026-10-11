package com.aiworkspace.audio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WyomingEventHeader(
        String type,
        Map<String, Object> data,
        @JsonProperty("data_length") int dataLength,
        @JsonProperty("payload_length") int payloadLength
) {
}
