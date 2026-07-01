package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
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
        KnowledgeService service = new KnowledgeService(workspaceId -> Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.findWorkspaceKnowledge(" ")
        );

        assertEquals("Workspace ID must not be blank", exception.getMessage());
    }

    private static class CapturingKnowledgeRepository implements KnowledgeRepository {

        private String workspaceId;

        @Override
        public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return Optional.empty();
        }
    }
}
