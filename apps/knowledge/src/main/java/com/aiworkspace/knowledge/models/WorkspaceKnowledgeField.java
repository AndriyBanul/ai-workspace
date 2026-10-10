package com.aiworkspace.knowledge.models;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum WorkspaceKnowledgeField {

    DOCUMENTS_INFO("documentsInfo"),
    AUDIO_INFO("audioInfo"),
    VIDEO_INFO("videoInfo"),
    IMAGES_INFO("imagesInfo");

    private final String fieldName;

    public String fieldName() {
        return fieldName;
    }
}
