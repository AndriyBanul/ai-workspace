package com.aiworkspace.audio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PcmAudioFormat(int rate, int width, int channels) {
}
