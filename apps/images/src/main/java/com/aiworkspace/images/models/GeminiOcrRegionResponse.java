package com.aiworkspace.images.models;

import com.aiworkspace.images.client.GeminiOcrTextDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiOcrRegionResponse(@JsonDeserialize(using = GeminiOcrTextDeserializer.class) String text) {
}
