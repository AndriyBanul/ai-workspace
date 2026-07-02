package com.aiworkspace.knowledge.models;

public enum KnowledgeSourceType {

    DOCUMENT("documents"),
    AUDIO("audio"),
    IMAGE("images"),
    VIDEO("videos");

    private final String apiName;

    KnowledgeSourceType(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }
}
