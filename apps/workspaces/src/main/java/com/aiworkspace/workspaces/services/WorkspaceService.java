package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.CreateWorkspaceRequest;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.mappers.WorkspaceMapper;
import com.aiworkspace.workspaces.repositories.WorkspaceEntity;
import com.aiworkspace.workspaces.repositories.WorkspaceRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceFileService workspaceFileService;

    public WorkspaceService(WorkspaceRepository workspaceRepository, WorkspaceMapper workspaceMapper) {
        this(workspaceRepository, workspaceMapper, null);
    }

    @Autowired
    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMapper workspaceMapper,
            WorkspaceFileService workspaceFileService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMapper = workspaceMapper;
        this.workspaceFileService = workspaceFileService;
    }

    @Transactional
    public Workspace createWorkspace(String ownerId, CreateWorkspaceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        return createWorkspace(ownerId, request.name());
    }

    @Transactional
    public Workspace createWorkspace(String ownerId, String name) {
        validateOwnerId(ownerId);
        validateName(name);
        Instant now = Instant.now();
        WorkspaceEntity entity = new WorkspaceEntity(
                UUID.randomUUID().toString(),
                ownerId.trim(),
                name.trim(),
                now,
                now
        );

        return workspaceMapper.toModel(workspaceRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<Workspace> listWorkspaces(String ownerId) {
        validateOwnerId(ownerId);
        return workspaceRepository.findAllByOwnerId(ownerId.trim()).stream()
                .sorted(Comparator.comparing(WorkspaceEntity::getCreatedAt).reversed())
                .map(workspaceMapper::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public Workspace getWorkspace(String ownerId, String workspaceId) {
        validateOwnerId(ownerId);
        validateWorkspaceId(workspaceId);
        return workspaceRepository.findByIdAndOwnerId(workspaceId.trim(), ownerId.trim())
                .map(workspaceMapper::toModel)
                .orElseThrow(() -> new NoSuchElementException("Workspace was not found"));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceFile> listFiles(String ownerId, String workspaceId) {
        return workspaceFileService.listFiles(getWorkspace(ownerId, workspaceId).id());
    }

    @Transactional(readOnly = true)
    public WorkspaceFile getFile(String ownerId, String workspaceId, String fileId) {
        return workspaceFileService.getFile(getWorkspace(ownerId, workspaceId).id(), fileId);
    }

    @Transactional
    public void deleteFile(String ownerId, String workspaceId, String fileId) throws IOException {
        workspaceFileService.deleteFile(getWorkspace(ownerId, workspaceId).id(), fileId);
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Workspace owner ID must not be blank");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workspace name must not be blank");
        }
    }

    private void validateWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }
    }

}
