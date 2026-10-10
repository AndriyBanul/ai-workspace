package com.aiworkspace.knowledge.models;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum KnowledgeSourceType {

    DOCUMENT("documents"),
    AUDIO("audio"),
    IMAGE("images"),
    VIDEO("videos");

    private final String apiName;

    public String apiName() {
        return apiName;
    }
}
