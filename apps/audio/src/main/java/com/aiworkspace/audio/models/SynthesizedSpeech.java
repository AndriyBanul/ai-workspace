package com.aiworkspace.audio.models;

import lombok.Builder;

@Builder
public record SynthesizedSpeech(String filename, byte[] wavContent) {
}
