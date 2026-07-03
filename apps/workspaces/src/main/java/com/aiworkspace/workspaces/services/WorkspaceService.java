package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.mappers.WorkspaceMapper;
import com.aiworkspace.workspaces.repositories.WorkspaceEntity;
import com.aiworkspace.workspaces.repositories.WorkspaceRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;

    public WorkspaceService(WorkspaceRepository workspaceRepository, WorkspaceMapper workspaceMapper) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMapper = workspaceMapper;
    }

    @Transactional
    public Workspace createWorkspace(String name) {
        String normalizedName = normalizedName(name);
        Instant now = Instant.now();
        WorkspaceEntity entity = new WorkspaceEntity(
                UUID.randomUUID().toString(),
                normalizedName,
                now,
                now
        );

        return workspaceMapper.toModel(workspaceRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<Workspace> listWorkspaces() {
        return workspaceRepository.findAll().stream()
                .sorted(Comparator.comparing(WorkspaceEntity::getCreatedAt).reversed())
                .map(workspaceMapper::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public Workspace getWorkspace(String workspaceId) {
        return workspaceRepository.findById(normalizedWorkspaceId(workspaceId))
                .map(workspaceMapper::toModel)
                .orElseThrow(() -> new NoSuchElementException("Workspace was not found"));
    }

    private String normalizedName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workspace name must not be blank");
        }

        return name.trim();
    }

    private String normalizedWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace ID must not be blank");
        }

        return workspaceId.trim();
    }

}
