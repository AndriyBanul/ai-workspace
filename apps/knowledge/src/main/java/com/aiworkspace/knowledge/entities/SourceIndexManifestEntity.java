package com.aiworkspace.knowledge.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "source_index_manifests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SourceIndexManifestEntity {

    @Id
    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "active_generation", nullable = false)
    private String activeGeneration;

    @Column(name = "expected_items", nullable = false)
    private int expectedItems;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
