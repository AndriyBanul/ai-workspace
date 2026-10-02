package com.aiworkspace.knowledge.models;

public enum WorkspaceKnowledgeField {

    DOCUMENTS_INFO("documentsInfo"),
    AUDIO_INFO("audioInfo"),
    VIDEO_INFO("videoInfo"),
    IMAGES_INFO("imagesInfo");

    private final String fieldName;

    WorkspaceKnowledgeField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }
}
