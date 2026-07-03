package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.mappers.WorkspaceMapperImpl;
import com.aiworkspace.workspaces.repositories.WorkspaceEntity;
import com.aiworkspace.workspaces.repositories.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

    @Test
    void createsWorkspaceWithTrimmedName() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        when(repository.save(any(WorkspaceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WorkspaceService service = newService(repository);

        var workspace = service.createWorkspace("owner-1", " Investor demo ");

        assertEquals("owner-1", workspace.ownerId());
        assertEquals("Investor demo", workspace.name());
    }

    @Test
    void listsWorkspacesNewestFirst() {
        Instant older = Instant.parse("2026-07-01T00:00:00Z");
        Instant newer = Instant.parse("2026-07-02T00:00:00Z");
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        when(repository.findAllByOwnerId("owner-1")).thenReturn(List.of(
                new WorkspaceEntity("old", "owner-1", "Old", older, older),
                new WorkspaceEntity("new", "owner-1", "New", newer, newer)
        ));
        WorkspaceService service = newService(repository);

        var workspaces = service.listWorkspaces("owner-1");

        assertEquals(List.of("new", "old"), workspaces.stream().map(workspace -> workspace.id()).toList());
    }

    @Test
    void getsWorkspaceByTrimmedId() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        Instant now = Instant.parse("2026-07-02T00:00:00Z");
        when(repository.findByIdAndOwnerId("workspace-1", "owner-1"))
                .thenReturn(Optional.of(new WorkspaceEntity("workspace-1", "owner-1", "Demo", now, now)));
        WorkspaceService service = newService(repository);

        var workspace = service.getWorkspace(" owner-1 ", " workspace-1 ");

        assertEquals("workspace-1", workspace.id());
    }

    @Test
    void rejectsBlankWorkspaceName() {
        WorkspaceService service = newService(mock(WorkspaceRepository.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createWorkspace("owner-1", " ")
        );

        assertEquals("Workspace name must not be blank", exception.getMessage());
    }

    @Test
    void failsWhenWorkspaceIsMissing() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        when(repository.findByIdAndOwnerId("missing", "owner-1")).thenReturn(Optional.empty());
        WorkspaceService service = newService(repository);

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> service.getWorkspace("owner-1", "missing")
        );

        assertEquals("Workspace was not found", exception.getMessage());
    }

    private static WorkspaceService newService(WorkspaceRepository repository) {
        return new WorkspaceService(repository, new WorkspaceMapperImpl());
    }
}
