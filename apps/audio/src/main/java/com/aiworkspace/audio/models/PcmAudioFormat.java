package com.aiworkspace.audio.models;

import com.fasterxml.jackson.databind.JsonNode;

public record PcmAudioFormat(int rate, int width, int channels) {

    public static PcmAudioFormat from(JsonNode data) {
        return new PcmAudioFormat(
                data.path("rate").asInt(),
                data.path("width").asInt(),
                data.path("channels").asInt()
        );
    }
}
