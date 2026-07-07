package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.models.WorkspaceQuestionRequest;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeValidator {

    private static final int MAX_QUESTION_LENGTH = 4_000;

    public void validateWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }
    }

    public void validateKnowledgeField(WorkspaceKnowledgeField field) {
        if (field == null) {
            throw new IllegalArgumentException("Knowledge field must not be null");
        }
    }

    public void validateKnowledgeFieldValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Knowledge field value must not be blank");
        }
    }

    public void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }

        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Question must not be longer than 4000 characters");
        }
    }

    public void validateQuestionRequest(WorkspaceQuestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
    }

    public void validateKnowledgeItemContent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Knowledge item content must not be blank");
        }
    }
}
