package com.aiworkspace.orchestrator.models;

import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.services.OrchestratorService;
import com.aiworkspace.orchestrator.services.SourceRecoveryScheduler;
import com.aiworkspace.orchestrator.services.SourceRecoveryTaskService;
import com.aiworkspace.workspaces.services.WorkspaceFileService;

public record SourceRecoverySchedulerTestContext(
        SourceRecoveryScheduler scheduler,
        SourceRecoveryTaskService recovery,
        OrchestratorService orchestrator,
        WorkspaceFileService files,
        KnowledgeService knowledge
) {
}
