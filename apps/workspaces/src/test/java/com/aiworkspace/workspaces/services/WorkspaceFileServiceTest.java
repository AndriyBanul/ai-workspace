package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.entities.WorkspaceFileEntity;
import com.aiworkspace.workspaces.interfaces.FileStorage;
import com.aiworkspace.workspaces.mappers.WorkspaceFileMapperImpl;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.repositories.WorkspaceFileRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceFileServiceTest {

    @Test
    void doesNotOverwriteFailedFileWithProcessedStatus() {
        WorkspaceFileRepository repository = mock(WorkspaceFileRepository.class);
        when(repository.findByIdAndWorkspaceIdAndDeletedAtIsNull("file-1", "workspace-1"))
                .thenReturn(Optional.of(fileEntity(WorkspaceFileStatus.FAILED)));
        WorkspaceFileService service = new WorkspaceFileService(
                repository,
                new WorkspaceFileMapperImpl(),
                mock(FileStorage.class)
        );

        var file = service.markProcessed("workspace-1", "file-1");

        assertEquals(WorkspaceFileStatus.FAILED, file.status());
        verify(repository, never()).save(any(WorkspaceFileEntity.class));
    }

    @Test
    void updatesActiveFileStatusWithConditionalRepositoryUpdate() {
        WorkspaceFileRepository repository = mock(WorkspaceFileRepository.class);
        when(repository.updateStatusIfActive(
                eq("file-1"),
                eq("workspace-1"),
                eq(WorkspaceFileStatus.PROCESSED),
                any(Instant.class),
                anyList()
        )).thenReturn(1);
        when(repository.findByIdAndWorkspaceIdAndDeletedAtIsNull("file-1", "workspace-1"))
                .thenReturn(Optional.of(fileEntity(WorkspaceFileStatus.PROCESSED)));
        WorkspaceFileService service = new WorkspaceFileService(
                repository,
                new WorkspaceFileMapperImpl(),
                mock(FileStorage.class)
        );

        var file = service.markProcessed("workspace-1", "file-1");

        assertEquals(WorkspaceFileStatus.PROCESSED, file.status());
        verify(repository, never()).save(any(WorkspaceFileEntity.class));
    }

    private WorkspaceFileEntity fileEntity(WorkspaceFileStatus status) {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return WorkspaceFileEntity.builder()
                .id("file-1")
                .workspaceId("workspace-1")
                .originalFilename("sample.txt")
                .contentType("text/plain")
                .sizeBytes(12)
                .storageKey("workspace-1/file-1")
                .checksumSha256("a".repeat(64))
                .sourceType(WorkspaceFileSourceType.DOCUMENT)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
