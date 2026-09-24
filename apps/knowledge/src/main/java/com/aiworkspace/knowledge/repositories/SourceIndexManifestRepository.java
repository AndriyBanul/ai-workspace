package com.aiworkspace.knowledge.repositories;

import com.aiworkspace.knowledge.entities.SourceIndexManifestEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceIndexManifestRepository extends JpaRepository<SourceIndexManifestEntity, String> {

    List<SourceIndexManifestEntity> findAllByWorkspaceId(String workspaceId);
}
