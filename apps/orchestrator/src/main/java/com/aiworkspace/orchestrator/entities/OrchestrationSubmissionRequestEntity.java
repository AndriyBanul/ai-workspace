package com.aiworkspace.orchestrator.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "orchestration_submission_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrchestrationSubmissionRequestEntity {

    @Id
    private String id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(nullable = false)
    private String fingerprint;

    @Column(name = "response_json")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OrchestrationSubmissionRequestEntity(String id, String ownerId, String workspaceId,
            String fingerprint, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.workspaceId = workspaceId;
        this.fingerprint = fingerprint;
        this.createdAt = createdAt;
    }

    public void complete(String responseJson) {
        this.responseJson = responseJson;
    }
}
