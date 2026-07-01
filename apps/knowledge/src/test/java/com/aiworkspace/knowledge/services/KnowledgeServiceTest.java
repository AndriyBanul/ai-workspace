package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.providers.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
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
        assertEquals(WorkspaceKnowledgeField.DOCUMENTS_INFO, repository.field);
        assertEquals("New info", repository.value);
    }

    @Test
    void recordsDocumentsInfoWithHardcodedWorkspaceId() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordDocumentsInfo("Document text");

        assertEquals("default-workspace", repository.workspaceId);
        assertEquals(WorkspaceKnowledgeField.DOCUMENTS_INFO, repository.field);
        assertEquals("Document text", repository.value);
    }

    @Test
    void recordsAudioInfoWithHardcodedWorkspaceId() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordAudioInfo("Audio transcript");

        assertEquals("default-workspace", repository.workspaceId);
        assertEquals(WorkspaceKnowledgeField.AUDIO_INFO, repository.field);
        assertEquals("Audio transcript", repository.value);
    }

    @Test
    void recordsVideoInfoWithHardcodedWorkspaceId() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordVideoInfo("Video description");

        assertEquals("default-workspace", repository.workspaceId);
        assertEquals(WorkspaceKnowledgeField.VIDEO_INFO, repository.field);
        assertEquals("Video description", repository.value);
    }

    @Test
    void recordsImagesInfoWithHardcodedWorkspaceId() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository, new CapturingKnowledgeAnswerProvider());

        service.recordImagesInfo("Image description");

        assertEquals("default-workspace", repository.workspaceId);
        assertEquals(WorkspaceKnowledgeField.IMAGES_INFO, repository.field);
        assertEquals("Image description", repository.value);
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
        CapturingKnowledgeAnswerProvider answerProvider = new CapturingKnowledgeAnswerProvider();
        KnowledgeService service = new KnowledgeService(repository, answerProvider);

        var answer = service.answerWorkspaceQuestion(" workspace-1 ", " What do we know? ");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals("What do we know?", answer.question());
        assertEquals("Answer from LLM", answer.answer());
        assertEquals("What do we know?", answerProvider.question);
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Document context"));
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Audio context"));
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Video context"));
        org.junit.jupiter.api.Assertions.assertTrue(answerProvider.context.contains("Image context"));
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
        private WorkspaceKnowledgeField field;
        private String value;
        private Optional<WorkspaceKnowledge> knowledge = Optional.empty();

        @Override
        public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return knowledge;
        }

        @Override
        public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value) {
            this.workspaceId = workspaceId;
            this.field = field;
            this.value = value;
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
