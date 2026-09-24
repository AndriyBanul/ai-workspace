package com.aiworkspace.knowledge.entities;

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
@Table(name = "workspace_answer_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceAnswerHistoryEntity {

    @Id
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(nullable = false, length = 4000)
    private String question;

    @Column(name = "answer_json", nullable = false)
    private String answerJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WorkspaceAnswerHistoryEntity(String id, String workspaceId, String question, String answerJson,
            Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.question = question;
        this.answerJson = answerJson;
        this.createdAt = createdAt;
    }
}
