package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.entities.SourceIndexManifestEntity;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;
import com.aiworkspace.knowledge.repositories.SourceIndexManifestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceIndexManifestService {

    private final SourceIndexManifestRepository repository;

    public SourceIndexManifestService(SourceIndexManifestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void publish(StagedKnowledgeIndex index) {
        if (index == null || index.itemCount() < 1) {
            throw new IllegalArgumentException("A complete source index is required for publication");
        }
        repository.save(new SourceIndexManifestEntity(index.sourceId(), index.workspaceId(),
                index.generation(), index.itemCount(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public Optional<SourceIndexManifestEntity> findBySourceId(String sourceId) {
        return repository.findById(sourceId);
    }

    @Transactional(readOnly = true)
    public List<SourceIndexManifestEntity> findByWorkspaceId(String workspaceId) {
        return repository.findAllByWorkspaceId(workspaceId);
    }
}
