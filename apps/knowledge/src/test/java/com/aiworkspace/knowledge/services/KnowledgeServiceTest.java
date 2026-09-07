package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.models.KnowledgeSourceType;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.interfaces.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeServiceTest {

    @Test
    void fetchesWorkspaceKnowledgeByTrimmedWorkspaceId() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.findWorkspaceKnowledge(" workspace-1 ");

        assertEquals("workspace-1", repository.workspaceId);
    }

    @Test
    void rejectsBlankWorkspaceId() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.findWorkspaceKnowledge(" ")
        );

        assertEquals("Workspace ID must not be blank", exception.getMessage());
    }

    @Test
    void updatesWorkspaceKnowledgeField() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.updateWorkspaceKnowledgeField(" workspace-1 ", WorkspaceKnowledgeField.DOCUMENTS_INFO, " New info ");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.DOCUMENT, repository.items.get(0).sourceType());
        assertEquals("New info", repository.items.get(0).content());
    }

    @Test
    void recordsDocumentsInfoForWorkspace() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordDocumentsInfo("workspace-1", "document.txt", "job-1", "Document text");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.DOCUMENT, repository.items.get(0).sourceType());
        assertEquals("document.txt", repository.items.get(0).sourceName());
        assertEquals("job-1", repository.items.get(0).jobId());
        assertEquals("Document text", repository.items.get(0).content());
    }

    @Test
    void recordsDurableDocumentSourceMetadata() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());
        Instant extractedAt = Instant.parse("2026-09-07T12:00:00Z");

        service.recordDocumentsInfo(
                "workspace-1",
                "document.txt",
                "job-1",
                "Document text",
                new KnowledgeSourceMetadata(
                        "file-42",
                        "https://example.com/source",
                        extractedAt,
                        "parser-1"
                )
        );

        KnowledgeItem item = repository.items.get(0);
        assertEquals("file-42", item.sourceId());
        assertEquals("https://example.com/source", item.sourceUrl());
        assertEquals(extractedAt, item.extractedAt());
        assertEquals("parser-1", item.parserVersion());
    }

    @Test
    void recordsAudioInfoForWorkspace() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordAudioInfo("workspace-1", "meeting.mp3", "job-1", "Audio transcript");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.AUDIO, repository.items.get(0).sourceType());
        assertEquals("Audio transcript", repository.items.get(0).content());
    }

    @Test
    void recordsVideoInfoForWorkspace() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordVideoInfo("workspace-1", "video.mp4", "job-1", "Video description");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.VIDEO, repository.items.get(0).sourceType());
        assertEquals("Video description", repository.items.get(0).content());
    }

    @Test
    void recordsImagesInfoForWorkspace() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordImagesInfo("workspace-1", "image.png", "job-1", "Image description");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(KnowledgeSourceType.IMAGE, repository.items.get(0).sourceType());
        assertEquals("Image description", repository.items.get(0).content());
    }

    @Test
    void rejectsMissingKnowledgeField() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateWorkspaceKnowledgeField("workspace-1", null, "Info")
        );

        assertEquals("Knowledge field must not be null", exception.getMessage());
    }

    @Test
    void rejectsBlankKnowledgeFieldValue() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateWorkspaceKnowledgeField("workspace-1", WorkspaceKnowledgeField.AUDIO_INFO, " ")
        );

        assertEquals("Knowledge field value must not be blank", exception.getMessage());
    }

    @Test
    void answersWorkspaceQuestionWithWorkspaceContext() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        repository.knowledge = Optional.of(new WorkspaceKnowledge(
                "workspace-1",
                "Document context",
                "Audio context",
                "Video context",
                "Image context"
        ));
        repository.searchResults = List.of(
                new KnowledgeItem(
                        "item-1",
                        "workspace-1",
                        KnowledgeSourceType.DOCUMENT,
                        "document.txt",
                        "job-1",
                        "file-42",
                        "https://example.com/source",
                        "Document context",
                        Instant.parse("2026-07-01T23:59:00Z"),
                        "parser-1",
                        Instant.parse("2026-07-02T00:00:00Z")
                ),
                new KnowledgeItem(
                        "item-2",
                        "workspace-1",
                        KnowledgeSourceType.AUDIO,
                        "meeting.mp3",
                        "job-1",
                        "Audio context",
                        Instant.parse("2026-07-02T00:00:01Z")
                )
        );
        CapturingKnowledgeAnswerProvider answerProvider = new CapturingKnowledgeAnswerProvider();
        KnowledgeService service = new KnowledgeService(repository, answerProvider);

        var answer = service.answerWorkspaceQuestion(" workspace-1 ", " What do we know? ");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals("What do we know?", answer.question());
        assertEquals("Answer from LLM", answer.answer());
        assertEquals(2, answer.sourceFiles().size());
        assertEquals("document.txt", answer.sourceFiles().get(0).name());
        assertEquals("documents", answer.sourceFiles().get(0).type());
        assertEquals("job-1", answer.sourceFiles().get(0).jobId());
        assertEquals("file-42", answer.sourceFiles().get(0).sourceId());
        assertEquals("https://example.com/source", answer.sourceFiles().get(0).sourceUrl());
        assertEquals("parser-1", answer.sourceFiles().get(0).parserVersion());
        assertEquals(1, answer.sourceFiles().get(0).sourceCount());
        assertEquals(2, answer.sources().size());
        assertEquals("document.txt", answer.sources().get(0).sourceName());
        assertEquals("documents:file-42", answer.sources().get(0).sourceFileKey());
        assertEquals("file-42", answer.sources().get(0).sourceId());
        assertEquals("What do we know?", answerProvider.question);
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Document context"));
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Audio context"));
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Source file: document.txt"));
    }

    @Test
    void rejectsBlankQuestion() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.answerWorkspaceQuestion("workspace-1", " ")
        );

        assertEquals("Question must not be blank", exception.getMessage());
    }

    @Test
    void rejectsTooLongQuestion() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.answerWorkspaceQuestion("workspace-1", "a".repeat(4_001))
        );

        assertEquals("Question must not be longer than 4000 characters", exception.getMessage());
    }

    @Test
    void failsWhenWorkspaceKnowledgeIsMissing() {
        KnowledgeService service = new KnowledgeService(
                new CapturingKnowledgeRepository(),
                new CapturingKnowledgeAnswerProvider()
        );

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> service.answerWorkspaceQuestion("workspace-1", "What do we know?")
        );

        assertEquals("Workspace knowledge was not found", exception.getMessage());
    }

    private static class CapturingKnowledgeRepository implements KnowledgeRepository {

        private String workspaceId;
        private final List<KnowledgeItem> items = new ArrayList<>();
        private List<KnowledgeItem> searchResults = List.of();
        private Optional<WorkspaceKnowledge> knowledge = Optional.empty();

        @Override
        public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return knowledge;
        }

        @Override
        public List<KnowledgeItem> findKnowledgeItemsByWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return items;
        }

        @Override
        public List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, int limit) {
            this.workspaceId = workspaceId;
            return searchResults;
        }

        @Override
        public void addKnowledgeItem(KnowledgeItem item) {
            this.workspaceId = item.workspaceId();
            items.add(item);
        }

        @Override
        public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value) {
            addKnowledgeItem(new KnowledgeItem(
                    "item-" + items.size(),
                    workspaceId,
                    switch (field) {
                        case DOCUMENTS_INFO -> KnowledgeSourceType.DOCUMENT;
                        case AUDIO_INFO -> KnowledgeSourceType.AUDIO;
                        case IMAGES_INFO -> KnowledgeSourceType.IMAGE;
                        case VIDEO_INFO -> KnowledgeSourceType.VIDEO;
                    },
                    field.fieldName(),
                    null,
                    value,
                    Instant.now()
            ));
        }
    }

    private static class CapturingKnowledgeAnswerProvider implements KnowledgeAnswerProvider {

        private String question;
        private String context;

        @Override
        public String answer(String question, String context) {
            this.question = question;
            this.context = context;
            return "Answer from LLM";
        }
    }
}
