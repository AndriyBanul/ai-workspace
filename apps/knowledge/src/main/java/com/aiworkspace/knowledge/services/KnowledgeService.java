package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.providers.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private static final String HARDCODED_WORKSPACE_ID = "default-workspace";
    private static final int MAX_QUESTION_LENGTH = 4_000;

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeAnswerProvider knowledgeAnswerProvider;

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider) {
        this.knowledgeRepository = knowledgeRepository;
        this.knowledgeAnswerProvider = knowledgeAnswerProvider;
    }

    public Optional<WorkspaceKnowledge> findWorkspaceKnowledge(String workspaceId) throws IOException {
        return knowledgeRepository.findByWorkspaceId(normalizedWorkspaceId(workspaceId));
    }

    public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException {
        if (field == null) {
            throw new IllegalArgumentException("Knowledge field must not be null");
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Knowledge field value must not be blank");
        }

        knowledgeRepository.updateWorkspaceKnowledgeField(normalizedWorkspaceId(workspaceId), field, value.trim());
    }

    public void recordDocumentsInfo(String value) throws IOException {
        updateWorkspaceKnowledgeField(HARDCODED_WORKSPACE_ID, WorkspaceKnowledgeField.DOCUMENTS_INFO, value);
    }

    public void recordAudioInfo(String value) throws IOException {
        updateWorkspaceKnowledgeField(HARDCODED_WORKSPACE_ID, WorkspaceKnowledgeField.AUDIO_INFO, value);
    }

    public void recordVideoInfo(String value) throws IOException {
        updateWorkspaceKnowledgeField(HARDCODED_WORKSPACE_ID, WorkspaceKnowledgeField.VIDEO_INFO, value);
    }

    public void recordImagesInfo(String value) throws IOException {
        updateWorkspaceKnowledgeField(HARDCODED_WORKSPACE_ID, WorkspaceKnowledgeField.IMAGES_INFO, value);
    }

    public WorkspaceKnowledgeAnswer answerWorkspaceQuestion(String workspaceId, String question) throws IOException {
        String normalizedWorkspaceId = normalizedWorkspaceId(workspaceId);
        String normalizedQuestion = normalizedQuestion(question);
        WorkspaceKnowledge knowledge = knowledgeRepository.findByWorkspaceId(normalizedWorkspaceId)
                .orElseThrow(() -> new NoSuchElementException("Workspace knowledge was not found"));

        String answer = knowledgeAnswerProvider.answer(normalizedQuestion, contextFrom(knowledge));

        return new WorkspaceKnowledgeAnswer(normalizedWorkspaceId, normalizedQuestion, answer);
    }

    private String normalizedWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }

        return workspaceId.trim();
    }

    private String normalizedQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }

        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Question must not be longer than 4000 characters");
        }

        return question.trim();
    }

    private String contextFrom(WorkspaceKnowledge knowledge) {
        return """
                Workspace ID: %s

                Documents info:
                %s

                Audio info:
                %s

                Video info:
                %s

                Images info:
                %s
                """.formatted(
                knowledge.workspaceId(),
                valueOrEmpty(knowledge.documentsInfo()),
                valueOrEmpty(knowledge.audioInfo()),
                valueOrEmpty(knowledge.videoInfo()),
                valueOrEmpty(knowledge.imagesInfo())
        );
    }

    private String valueOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }

        return value;
    }
}
