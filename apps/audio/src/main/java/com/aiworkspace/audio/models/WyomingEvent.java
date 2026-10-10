package com.aiworkspace.audio.models;

import com.fasterxml.jackson.databind.JsonNode;

public record WyomingEvent(String type, JsonNode data, byte[] payload) {
}
