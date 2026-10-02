package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.RecoveryClaim;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.knowledge.models.StagedKnowledgeIndex;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.knowledge.services.SourceIndexManifestService;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Commits job, source and recovery state under the same leased source row lock. */
@Service
public class SourceCompletionService {

    private static final Logger log = LoggerFactory.getLogger(SourceCompletionService.class);

    private final IngestionJobService jobs;
    private final WorkspaceFileService sources;
    private final SourceRecoveryTracker recovery;
    private final SourceIndexManifestService manifests;
    private final KnowledgeRepository knowledge;

    public SourceCompletionService(IngestionJobService jobs, WorkspaceFileService sources,
            SourceRecoveryTracker recovery) {
        this(jobs, sources, recovery, null, null);
    }

    @Autowired
    public SourceCompletionService(IngestionJobService jobs, WorkspaceFileService sources,
            SourceRecoveryTracker recovery, SourceIndexManifestService manifests, KnowledgeRepository knowledge) {
        this.jobs = jobs;
        this.sources = sources;
        this.recovery = recovery;
        this.manifests = manifests;
        this.knowledge = knowledge;
    }

    @Transactional
    public void complete(IngestionJobDetails job, IngestionContentType contentType, WorkspaceFile source,
            RecoveryClaim claim, StagedKnowledgeIndex stagedIndex) {
        recovery.assertCurrent(source, SourceOperationType.PROCESS, claim);
        if (manifests != null) {
            if (stagedIndex == null || !job.workspaceId().equals(stagedIndex.workspaceId())
                    || !source.id().equals(stagedIndex.sourceId())) {
                throw new IllegalStateException("Source index generation was not staged");
            }
            manifests.publish(stagedIndex);
        }
        if (!jobs.markStepCompleted(job.jobId(), contentType)) {
            throw new IllegalStateException("Ingestion source is no longer running");
        }
        sources.markProcessed(job.workspaceId(), source.id());
        recovery.complete(source, SourceOperationType.PROCESS, claim);
        if (knowledge != null && stagedIndex != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        knowledge.pruneSourceGenerations(stagedIndex.workspaceId(), stagedIndex.sourceId(),
                                stagedIndex.generation());
                    } catch (java.io.IOException | RuntimeException exception) {
                        log.warn("Published source generation could not be pruned workspaceId={} sourceId={}",
                                stagedIndex.workspaceId(), stagedIndex.sourceId(), exception);
                    }
                }
            });
        }
    }

    @Transactional
    public void fail(IngestionJobDetails job, IngestionContentType contentType, WorkspaceFile source,
            RecoveryClaim claim, boolean sourceTransitioned, Exception exception) {
        recovery.assertCurrent(source, SourceOperationType.PROCESS, claim);
        jobs.markStepFailed(job.jobId(), contentType, exception);
        if (sourceTransitioned) {
            sources.markFailed(job.workspaceId(), source.id());
        }
        recovery.fail(source, SourceOperationType.PROCESS, claim, exception);
    }
}
