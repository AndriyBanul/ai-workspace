package com.aiworkspace.orchestrator.services;

import com.aiworkspace.knowledge.interfaces.KnowledgeAnswerProvider;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.exceptions.WorkspaceSourceConflictException;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceLifecycleServiceTest {

    @Test
    void deletesDocumentKnowledgeBeforeDeletingStoredFile() throws IOException {
        List<String> events = new ArrayList<>();
        WorkspaceLifecycleService service = new WorkspaceLifecycleService(
                new TestWorkspaceService(events),
                new TestKnowledgeService(events, false)
        );

        service.deleteFile("owner-1", "workspace-1", "file-1");

        assertEquals(List.of("knowledge", "file"), events);
    }

    @Test
    void keepsStoredFileWhenKnowledgeDeletionFails() {
        List<String> events = new ArrayList<>();
        WorkspaceLifecycleService service = new WorkspaceLifecycleService(
                new TestWorkspaceService(events),
                new TestKnowledgeService(events, true)
        );

        assertThrows(
                IOException.class,
                () -> service.deleteFile("owner-1", "workspace-1", "file-1")
        );

        assertEquals(List.of("knowledge"), events);
    }

    @Test
    void rejectsDeletionWhileSourceIsProcessing() {
        List<String> events = new ArrayList<>();
        WorkspaceLifecycleService service = new WorkspaceLifecycleService(
                new TestWorkspaceService(events, WorkspaceFileStatus.PROCESSING),
                new TestKnowledgeService(events, false)
        );

        assertThrows(
                WorkspaceSourceConflictException.class,
                () -> service.deleteFile("owner-1", "workspace-1", "file-1")
        );

        assertEquals(List.of(), events);
    }

    @Test
    void deletesWorkspaceKnowledgeBeforeFilesAndMetadata() throws IOException {
        List<String> events = new ArrayList<>();
        WorkspaceLifecycleService service = new WorkspaceLifecycleService(
                new TestWorkspaceService(events),
                new TestKnowledgeService(events, false)
        );

        service.deleteWorkspace("owner-1", "workspace-1");

        assertEquals(List.of("workspace-knowledge", "workspace"), events);
    }

    private static class TestWorkspaceService extends WorkspaceService {

        private final List<String> events;
        private final WorkspaceFileStatus fileStatus;

        TestWorkspaceService(List<String> events) {
            this(events, WorkspaceFileStatus.PROCESSED);
        }

        TestWorkspaceService(List<String> events, WorkspaceFileStatus fileStatus) {
            super(null, null);
            this.events = events;
            this.fileStatus = fileStatus;
        }

        @Override
        public WorkspaceFile getFile(String ownerId, String workspaceId, String fileId) {
            Instant now = Instant.now();
            return new WorkspaceFile(
                    fileId,
                    workspaceId,
                    "report.pdf",
                    "application/pdf",
                    100,
                    workspaceId + "/" + fileId,
                    "a".repeat(64),
                    null,
                    WorkspaceFileSourceType.DOCUMENT,
                    fileStatus,
                    now,
                    now,
                    null
            );
        }

        @Override
        public Workspace getWorkspace(String ownerId, String workspaceId) {
            Instant now = Instant.now();
            return new Workspace(workspaceId, ownerId, "Test workspace", now, now);
        }

        @Override
        public void deleteFile(String ownerId, String workspaceId, String fileId) {
            events.add("file");
        }

        @Override
        public void deleteWorkspace(String ownerId, String workspaceId) {
            events.add("workspace");
        }
    }

    private static class TestKnowledgeService extends KnowledgeService {

        private final List<String> events;
        private final boolean fail;

        TestKnowledgeService(List<String> events, boolean fail) {
            super((KnowledgeRepository) null, (KnowledgeAnswerProvider) null);
            this.events = events;
            this.fail = fail;
        }

        @Override
        public void deleteSourceKnowledge(String workspaceId, String sourceId) throws IOException {
            events.add("knowledge");
            if (fail) {
                throw new IOException("OpenSearch unavailable");
            }
        }

        @Override
        public void deleteWorkspaceKnowledge(String workspaceId) throws IOException {
            events.add("workspace-knowledge");
            if (fail) {
                throw new IOException("OpenSearch unavailable");
            }
        }
    }
}
