package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeServiceTest {

    @Test
    void fetchesWorkspaceKnowledgeByTrimmedWorkspaceId() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository);

        service.findWorkspaceKnowledge(" workspace-1 ");

        assertEquals("workspace-1", repository.workspaceId);
    }

    @Test
    void rejectsBlankWorkspaceId() {
        KnowledgeService service = new KnowledgeService(new CapturingKnowledgeRepository());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.findWorkspaceKnowledge(" ")
        );

        assertEquals("Workspace ID must not be blank", exception.getMessage());
    }

    @Test
    void updatesWorkspaceKnowledgeField() throws IOException {
        CapturingKnowledgeRepository repository = new CapturingKnowledgeRepository();
        KnowledgeService service = new KnowledgeService(repository);

        service.updateWorkspaceKnowledgeField(" workspace-1 ", WorkspaceKnowledgeField.DOCUMENTS_INFO, " New info ");

        assertEquals("workspace-1", repository.workspaceId);
        assertEquals(WorkspaceKnowledgeField.DOCUMENTS_INFO, repository.field);
        assertEquals("New info", repository.value);
    }

    @Test
    void rejectsMissingKnowledgeField() {
        KnowledgeService service = new KnowledgeService(new CapturingKnowledgeRepository());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateWorkspaceKnowledgeField("workspace-1", null, "Info")
        );

        assertEquals("Knowledge field must not be null", exception.getMessage());
    }

    @Test
    void rejectsBlankKnowledgeFieldValue() {
        KnowledgeService service = new KnowledgeService(new CapturingKnowledgeRepository());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateWorkspaceKnowledgeField("workspace-1", WorkspaceKnowledgeField.AUDIO_INFO, " ")
        );

        assertEquals("Knowledge field value must not be blank", exception.getMessage());
    }

    private static class CapturingKnowledgeRepository implements KnowledgeRepository {

        private String workspaceId;
        private WorkspaceKnowledgeField field;
        private String value;

        @Override
        public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return Optional.empty();
        }

        @Override
        public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value) {
            this.workspaceId = workspaceId;
            this.field = field;
            this.value = value;
        }
    }
}
