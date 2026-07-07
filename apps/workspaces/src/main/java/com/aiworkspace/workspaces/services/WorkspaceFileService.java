package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.interfaces.FileStorage;
import com.aiworkspace.workspaces.mappers.WorkspaceFileMapper;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.FileStorageRequest;
import com.aiworkspace.workspaces.models.StoredFile;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.repositories.WorkspaceFileEntity;
import com.aiworkspace.workspaces.repositories.WorkspaceFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceFileService {

    private final WorkspaceFileRepository workspaceFileRepository;
    private final WorkspaceFileMapper workspaceFileMapper;
    private final FileStorage fileStorage;
    private final WorkspaceFileValidator workspaceFileValidator;

    @Autowired
    public WorkspaceFileService(
            WorkspaceFileRepository workspaceFileRepository,
            WorkspaceFileMapper workspaceFileMapper,
            FileStorage fileStorage
    ) {
        this(workspaceFileRepository, workspaceFileMapper, fileStorage, new WorkspaceFileValidator());
    }

    public WorkspaceFileService(
            WorkspaceFileRepository workspaceFileRepository,
            WorkspaceFileMapper workspaceFileMapper,
            FileStorage fileStorage,
            WorkspaceFileValidator workspaceFileValidator
    ) {
        this.workspaceFileRepository = workspaceFileRepository;
        this.workspaceFileMapper = workspaceFileMapper;
        this.fileStorage = fileStorage;
        this.workspaceFileValidator = workspaceFileValidator;
    }

    @Transactional
    public WorkspaceFile createFile(CreateWorkspaceFileRequest request) throws IOException {
        workspaceFileValidator.validateCreateRequest(request);
        String fileId = UUID.randomUUID().toString();
        String workspaceId = request.workspaceId().trim();
        String originalFilename = request.originalFilename().trim();
        String contentType = normalizedOptionalValue(request.contentType());
        StoredFile storedFile = fileStorage.store(FileStorageRequest.builder()
                .workspaceId(workspaceId)
                .fileId(fileId)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .content(request.content())
                .build());
        Instant now = Instant.now();
        WorkspaceFileEntity entity = WorkspaceFileEntity.builder()
                .id(fileId)
                .workspaceId(workspaceId)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .sizeBytes(storedFile.sizeBytes())
                .storageKey(storedFile.storageKey())
                .checksumSha256(storedFile.checksumSha256())
                .sourceType(request.sourceType())
                .status(WorkspaceFileStatus.UPLOADED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return workspaceFileMapper.toModel(workspaceFileRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceFile> listFiles(String workspaceId) {
        workspaceFileValidator.validateWorkspaceId(workspaceId);
        return workspaceFileRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId.trim())
                .stream()
                .map(workspaceFileMapper::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceFile getFile(String workspaceId, String fileId) {
        return workspaceFileMapper.toModel(findActiveEntity(workspaceId, fileId));
    }

    @Transactional(readOnly = true)
    public InputStream readContent(String workspaceId, String fileId) throws IOException {
        WorkspaceFileEntity entity = findActiveEntity(workspaceId, fileId);
        return fileStorage.read(entity.getStorageKey());
    }

    @Transactional
    public WorkspaceFile markProcessing(String workspaceId, String fileId) {
        return updateStatus(workspaceId, fileId, WorkspaceFileStatus.PROCESSING);
    }

    @Transactional
    public WorkspaceFile markProcessed(String workspaceId, String fileId) {
        return updateStatus(workspaceId, fileId, WorkspaceFileStatus.PROCESSED);
    }

    @Transactional
    public WorkspaceFile markFailed(String workspaceId, String fileId) {
        return updateStatus(workspaceId, fileId, WorkspaceFileStatus.FAILED);
    }

    @Transactional
    public void deleteFile(String workspaceId, String fileId) throws IOException {
        WorkspaceFileEntity entity = findActiveEntity(workspaceId, fileId);
        fileStorage.delete(entity.getStorageKey());
        entity.softDelete(Instant.now());
        workspaceFileRepository.save(entity);
    }

    private WorkspaceFile updateStatus(String workspaceId, String fileId, WorkspaceFileStatus status) {
        WorkspaceFileEntity entity = findActiveEntity(workspaceId, fileId);
        entity.updateStatus(status, Instant.now());
        return workspaceFileMapper.toModel(workspaceFileRepository.save(entity));
    }

    private WorkspaceFileEntity findActiveEntity(String workspaceId, String fileId) {
        workspaceFileValidator.validateWorkspaceId(workspaceId);
        workspaceFileValidator.validateFileId(fileId);
        return workspaceFileRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(fileId.trim(), workspaceId.trim())
                .orElseThrow(() -> new NoSuchElementException("Workspace file was not found"));
    }

    private String normalizedOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
