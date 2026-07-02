package com.aiworkspace.orchestrator.models;

public enum IngestionContentType {

    DOCUMENTS("documents"),
    AUDIO("audio"),
    IMAGES("images"),
    VIDEOS("videos");

    private final String apiName;

    IngestionContentType(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }
}
