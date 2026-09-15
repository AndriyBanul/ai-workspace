package com.aiworkspace.orchestrator.services;

import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.config.SourceRecoveryProperties;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.SourceRecoveryStatus;
import com.aiworkspace.orchestrator.models.SourceRecoveryTask;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SourceRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SourceRecoveryScheduler.class);

    private final SourceRecoveryTaskService recoveryTaskService;
    private final OrchestratorService orchestratorService;
    private final WorkspaceLifecycleService lifecycleService;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final SourceRecoveryProperties properties;

    public SourceRecoveryScheduler(SourceRecoveryTaskService recoveryTaskService,
            OrchestratorService orchestratorService, WorkspaceLifecycleService lifecycleService,
            WorkspaceFileService workspaceFileService, KnowledgeService knowledgeService,
            SourceRecoveryProperties properties) {
        this.recoveryTaskService = recoveryTaskService;
        this.orchestratorService = orchestratorService;
        this.lifecycleService = lifecycleService;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ai-workspace.orchestrator.recovery.poll-interval:30s}")
    public void recoverDueSources() {
        if (!properties.enabled()) {
            return;
        }
        for (SourceRecoveryTask task : recoveryTaskService.findDueTasks()) {
            try {
                recover(task);
            } catch (Exception exception) {
                log.warn("Source recovery dispatch failed workspaceId={} sourceId={} operation={} status={}",
                        task.workspaceId(), task.sourceId(), task.operationType(), task.status(), exception);
            }
        }
    }

    private void recover(SourceRecoveryTask task) throws IOException {
        if (task.status() == SourceRecoveryStatus.COMPLETED
                && task.operationType() == SourceOperationType.PROCESS) {
            reconcile(task);
            return;
        }
        if (task.operationType() == SourceOperationType.DELETE) {
            retryDelete(task);
            return;
        }
        retryProcess(task);
    }

    private void retryProcess(SourceRecoveryTask task) throws IOException {
        try {
            orchestratorService.recoverSource(task.workspaceId(), task.sourceId());
        } catch (NoSuchElementException exception) {
            knowledgeService.deleteSourceKnowledge(task.workspaceId(), task.sourceId());
            recoveryTaskService.completeDeletedTask(task);
        }
    }

    private void retryDelete(SourceRecoveryTask task) throws IOException {
        try {
            lifecycleService.retryDeleteSource(task.workspaceId(), task.sourceId());
        } catch (NoSuchElementException exception) {
            recoveryTaskService.completeDeletedTask(task);
        }
    }

    private void reconcile(SourceRecoveryTask task) throws IOException {
        WorkspaceFile source;
        try {
            source = workspaceFileService.getFile(task.workspaceId(), task.sourceId());
        } catch (NoSuchElementException exception) {
            knowledgeService.deleteSourceKnowledge(task.workspaceId(), task.sourceId());
            recoveryTaskService.completeDeletedTask(task);
            return;
        }

        if (!workspaceFileService.contentExists(source.workspaceId(), source.id())) {
            recoveryTaskService.recordIntegrityFailure(
                    source, "SOURCE_CONTENT_MISSING", "Stored source content is missing");
            return;
        }
        if (source.status() == WorkspaceFileStatus.PROCESSED
                && !knowledgeService.hasSourceKnowledge(source.workspaceId(), source.id())) {
            recoveryTaskService.scheduleRepair(
                    source, "KNOWLEDGE_MISSING", "Indexed source knowledge is missing");
            return;
        }
        recoveryTaskService.deferReconciliation(source);
    }
}
