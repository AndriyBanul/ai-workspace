package com.aiworkspace.audio.models;

public record WyomingEvent(String type, PcmAudioFormat audioFormat, WyomingErrorResponse error, byte[] payload) {
}
