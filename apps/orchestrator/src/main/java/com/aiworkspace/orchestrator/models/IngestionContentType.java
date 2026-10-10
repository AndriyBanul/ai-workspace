package com.aiworkspace.orchestrator.models;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum IngestionContentType {

    DOCUMENTS("documents"),
    AUDIO("audio"),
    IMAGES("images"),
    VIDEOS("videos");

    private final String apiName;

    public String apiName() {
        return apiName;
    }
}
