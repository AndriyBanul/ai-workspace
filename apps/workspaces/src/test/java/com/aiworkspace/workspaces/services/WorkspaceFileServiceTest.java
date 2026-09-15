package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.entities.WorkspaceFileEntity;
import com.aiworkspace.workspaces.exceptions.WorkspaceSourceConflictException;
import com.aiworkspace.workspaces.interfaces.FileStorage;
import com.aiworkspace.workspaces.mappers.WorkspaceFileMapperImpl;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.models.CreateWorkspaceUrlSourceRequest;
import com.aiworkspace.workspaces.repositories.WorkspaceFileRepository;
import java.time.Instant;
import java.io.IOException;
import java.util.List;
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceFileServiceTest {

    @Test
    void createsUrlBackedSourceWithoutWritingFileStorage() throws IOException {
        WorkspaceFileRepository repository = mock(WorkspaceFileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        when(repository.save(any(WorkspaceFileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WorkspaceFileService service = new WorkspaceFileService(
                repository, new WorkspaceFileMapperImpl(), storage);

        var source = service.createUrlSource(CreateWorkspaceUrlSourceRequest.builder()
                .workspaceId(" workspace-1 ")
                .sourceType(WorkspaceFileSourceType.YOUTUBE)
                .displayName(" Video ")
                .sourceUrl(" https://www.youtube.com/watch?v=9hE5-98ZeCg ")
                .contentType("video/youtube")
                .build());

        assertEquals("workspace-1", source.workspaceId());
        assertEquals("Video", source.originalFilename());
        assertEquals("https://www.youtube.com/watch?v=9hE5-98ZeCg", source.sourceUrl());
        assertEquals(WorkspaceFileStatus.UPLOADED, source.status());
        assertNull(source.storageKey());
        verify(storage, never()).store(any());
    }

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
    void rejectsConcurrentSourceProcessing() {
        WorkspaceFileRepository repository = mock(WorkspaceFileRepository.class);
        when(repository.updateStatusIfActive(
                eq("file-1"), eq("workspace-1"), eq(WorkspaceFileStatus.PROCESSING),
                any(Instant.class), anyList())).thenReturn(0);
        WorkspaceFileService service = new WorkspaceFileService(
                repository, new WorkspaceFileMapperImpl(), mock(FileStorage.class));

        WorkspaceSourceConflictException exception = assertThrows(
                WorkspaceSourceConflictException.class,
                () -> service.markProcessing("workspace-1", "file-1"));

        assertEquals("Workspace source is already processing or cannot be reprocessed", exception.getMessage());
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

    @Test
    void deletesEveryStoredFileForWorkspace() throws IOException {
        WorkspaceFileRepository repository = mock(WorkspaceFileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        WorkspaceFileEntity first = fileEntity("file-1", WorkspaceFileStatus.PROCESSED);
        WorkspaceFileEntity second = fileEntity("file-2", WorkspaceFileStatus.FAILED);
        when(repository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc("workspace-1"))
                .thenReturn(List.of(first, second));
        WorkspaceFileService service = new WorkspaceFileService(
                repository,
                new WorkspaceFileMapperImpl(),
                storage
        );

        service.deleteStoredFilesByWorkspaceId("workspace-1");

        verify(storage).delete("workspace-1/file-1");
        verify(storage).delete("workspace-1/file-2");
    }

    private WorkspaceFileEntity fileEntity(WorkspaceFileStatus status) {
        return fileEntity("file-1", status);
    }

    private WorkspaceFileEntity fileEntity(String id, WorkspaceFileStatus status) {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return WorkspaceFileEntity.builder()
                .id(id)
                .workspaceId("workspace-1")
                .originalFilename("sample.txt")
                .contentType("text/plain")
                .sizeBytes(12)
                .storageKey("workspace-1/" + id)
                .checksumSha256("a".repeat(64))
                .sourceType(WorkspaceFileSourceType.DOCUMENT)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
