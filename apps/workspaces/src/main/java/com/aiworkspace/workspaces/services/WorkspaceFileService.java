package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.entities.WorkspaceFileEntity;
import com.aiworkspace.workspaces.exceptions.WorkspaceSourceConflictException;
import com.aiworkspace.workspaces.interfaces.FileStorage;
import com.aiworkspace.workspaces.mappers.WorkspaceFileMapper;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.CreateWorkspaceUrlSourceRequest;
import com.aiworkspace.workspaces.models.FileStorageRequest;
import com.aiworkspace.workspaces.models.StoredFile;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
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

    private static final List<WorkspaceFileStatus> PROCESSING_SOURCE_STATUSES = List.of(
            WorkspaceFileStatus.UPLOADED,
            WorkspaceFileStatus.PROCESSED,
            WorkspaceFileStatus.FAILED
    );
    private static final List<WorkspaceFileStatus> RUNNING_SOURCE_STATUSES = List.of(WorkspaceFileStatus.PROCESSING);

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

    @Transactional
    public WorkspaceFile createUrlSource(CreateWorkspaceUrlSourceRequest request) {
        workspaceFileValidator.validateCreateUrlRequest(request);
        Instant now = Instant.now();
        WorkspaceFileEntity entity = WorkspaceFileEntity.builder()
                .id(UUID.randomUUID().toString())
                .workspaceId(request.workspaceId().trim())
                .originalFilename(request.displayName().trim())
                .contentType(normalizedOptionalValue(request.contentType()))
                .sizeBytes(0)
                .sourceUrl(request.sourceUrl().trim())
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
        if (entity.getStorageKey() == null || entity.getStorageKey().isBlank()) {
            throw new IllegalStateException("URL-backed workspace source has no stored file content");
        }
        return fileStorage.read(entity.getStorageKey());
    }

    @Transactional(readOnly = true)
    public boolean contentExists(String workspaceId, String fileId) {
        WorkspaceFileEntity entity = findActiveEntity(workspaceId, fileId);
        return entity.getStorageKey() == null || entity.getStorageKey().isBlank()
                || fileStorage.exists(entity.getStorageKey());
    }

    @Transactional
    public WorkspaceFile markProcessing(String workspaceId, String fileId) {
        return updateStatus(
                workspaceId, fileId, WorkspaceFileStatus.PROCESSING, PROCESSING_SOURCE_STATUSES, true);
    }

    @Transactional
    public WorkspaceFile markProcessed(String workspaceId, String fileId) {
        return updateStatus(
                workspaceId, fileId, WorkspaceFileStatus.PROCESSED, RUNNING_SOURCE_STATUSES, false);
    }

    @Transactional
    public WorkspaceFile markFailed(String workspaceId, String fileId) {
        return updateStatus(workspaceId, fileId, WorkspaceFileStatus.FAILED, RUNNING_SOURCE_STATUSES, false);
    }

    @Transactional
    public void deleteFile(String workspaceId, String fileId) throws IOException {
        WorkspaceFileEntity entity = findActiveEntity(workspaceId, fileId);
        if (entity.getStorageKey() != null && !entity.getStorageKey().isBlank()) {
            fileStorage.delete(entity.getStorageKey());
        }
        entity.softDelete(Instant.now());
        workspaceFileRepository.save(entity);
    }

    public void deleteStoredFilesByWorkspaceId(String workspaceId) throws IOException {
        workspaceFileValidator.validateWorkspaceId(workspaceId);
        List<WorkspaceFileEntity> files = workspaceFileRepository
                .findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId.trim());
        for (WorkspaceFileEntity file : files) {
            if (file.getStorageKey() != null && !file.getStorageKey().isBlank()) {
                fileStorage.delete(file.getStorageKey());
            }
        }
    }

    private WorkspaceFile updateStatus(String workspaceId, String fileId, WorkspaceFileStatus status,
            List<WorkspaceFileStatus> allowedCurrentStatuses, boolean transitionRequired) {
        workspaceFileValidator.validateWorkspaceId(workspaceId);
        workspaceFileValidator.validateFileId(fileId);
        int updated = workspaceFileRepository.updateStatusIfActive(
                fileId.trim(),
                workspaceId.trim(),
                status,
                Instant.now(),
                allowedCurrentStatuses
        );
        if (transitionRequired && updated == 0) {
            throw new WorkspaceSourceConflictException(
                    "Workspace source is already processing or cannot be reprocessed");
        }
        return workspaceFileMapper.toModel(findActiveEntity(workspaceId, fileId));
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
