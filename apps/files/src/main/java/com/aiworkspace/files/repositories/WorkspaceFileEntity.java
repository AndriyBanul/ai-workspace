package com.aiworkspace.files.repositories;

import com.aiworkspace.files.models.WorkspaceFileSourceType;
import com.aiworkspace.files.models.WorkspaceFileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "workspace_files")
public class WorkspaceFileEntity {

    @Id
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "checksum_sha256", nullable = false)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private WorkspaceFileSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkspaceFileStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public void updateStatus(WorkspaceFileStatus status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public void softDelete(Instant deletedAt) {
        this.status = WorkspaceFileStatus.DELETED;
        this.updatedAt = deletedAt;
        this.deletedAt = deletedAt;
    }
}
