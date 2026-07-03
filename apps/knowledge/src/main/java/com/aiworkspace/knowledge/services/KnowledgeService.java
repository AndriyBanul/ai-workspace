package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeAnswer;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeSource;
import com.aiworkspace.knowledge.providers.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private static final int MAX_QUESTION_LENGTH = 4_000;
    private static final int RETRIEVAL_LIMIT = 8;
    private static final int SOURCE_SNIPPET_LENGTH = 500;

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeAnswerProvider knowledgeAnswerProvider;

    public KnowledgeService(KnowledgeRepository knowledgeRepository, KnowledgeAnswerProvider knowledgeAnswerProvider) {
        this.knowledgeRepository = knowledgeRepository;
        this.knowledgeAnswerProvider = knowledgeAnswerProvider;
    }

    public Optional<WorkspaceKnowledge> findWorkspaceKnowledge(String workspaceId) throws IOException {
        validateWorkspaceId(workspaceId);
        return knowledgeRepository.findByWorkspaceId(workspaceId.trim());
    }

    public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
            throws IOException {
        if (field == null) {
            throw new IllegalArgumentException("Knowledge field must not be null");
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Knowledge field value must not be blank");
        }

        validateWorkspaceId(workspaceId);
        knowledgeRepository.updateWorkspaceKnowledgeField(workspaceId.trim(), field, value.trim());
    }

    public void recordDocumentsInfo(String workspaceId, String sourceName, String jobId, String value) throws IOException {
        recordKnowledgeItem(workspaceId, KnowledgeSourceType.DOCUMENT, sourceName, jobId, value);
    }

    public void recordAudioInfo(String workspaceId, String sourceName, String jobId, String value) throws IOException {
        recordKnowledgeItem(workspaceId, KnowledgeSourceType.AUDIO, sourceName, jobId, value);
    }

    public void recordVideoInfo(String workspaceId, String sourceName, String jobId, String value) throws IOException {
        recordKnowledgeItem(workspaceId, KnowledgeSourceType.VIDEO, sourceName, jobId, value);
    }

    public void recordImagesInfo(String workspaceId, String sourceName, String jobId, String value) throws IOException {
        recordKnowledgeItem(workspaceId, KnowledgeSourceType.IMAGE, sourceName, jobId, value);
    }

    public WorkspaceKnowledgeAnswer answerWorkspaceQuestion(String workspaceId, String question) throws IOException {
        validateWorkspaceId(workspaceId);
        validateQuestion(question);
        String trimmedWorkspaceId = workspaceId.trim();
        String trimmedQuestion = question.trim();
        List<KnowledgeItem> items = knowledgeRepository.searchKnowledgeItems(
                trimmedWorkspaceId,
                trimmedQuestion,
                RETRIEVAL_LIMIT
        );
        if (items.isEmpty()) {
            throw new NoSuchElementException("Workspace knowledge was not found");
        }

        String answer = knowledgeAnswerProvider.answer(trimmedQuestion, contextFrom(trimmedWorkspaceId, items));

        return new WorkspaceKnowledgeAnswer(
                trimmedWorkspaceId,
                trimmedQuestion,
                answer,
                items.stream().map(this::sourceFrom).toList()
        );
    }

    private void recordKnowledgeItem(
            String workspaceId,
            KnowledgeSourceType sourceType,
            String sourceName,
            String jobId,
            String value
    ) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Knowledge item content must not be blank");
        }

        validateWorkspaceId(workspaceId);
        knowledgeRepository.addKnowledgeItem(new KnowledgeItem(
                UUID.randomUUID().toString(),
                workspaceId.trim(),
                sourceType,
                normalizedOptionalValue(sourceName),
                normalizedOptionalValue(jobId),
                value.trim(),
                Instant.now()
        ));
    }

    private void validateWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }
    }

    private void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }

        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Question must not be longer than 4000 characters");
        }
    }

    private String contextFrom(String workspaceId, List<KnowledgeItem> items) {
        StringBuilder context = new StringBuilder("Workspace ID: ")
                .append(workspaceId)
                .append("\n\nRelevant knowledge items:\n");

        for (int index = 0; index < items.size(); index++) {
            KnowledgeItem item = items.get(index);
            context.append("\n[")
                    .append(index + 1)
                    .append("] Type: ")
                    .append(item.sourceType().apiName())
                    .append("\nSource: ")
                    .append(valueOrEmpty(item.sourceName()))
                    .append("\nContent:\n")
                    .append(item.content())
                    .append("\n");
        }

        return context.toString();
    }

    private WorkspaceKnowledgeSource sourceFrom(KnowledgeItem item) {
        return new WorkspaceKnowledgeSource(
                item.id(),
                item.sourceType().apiName(),
                item.sourceName(),
                item.jobId(),
                snippet(item.content())
        );
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }

        if (value.length() <= SOURCE_SNIPPET_LENGTH) {
            return value;
        }

        return value.substring(0, SOURCE_SNIPPET_LENGTH);
    }

    private String normalizedOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String valueOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }

        return value;
    }
}
