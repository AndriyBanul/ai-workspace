package com.aiworkspace.orchestrator.models;

import com.aiworkspace.orchestrator.entities.SourceRecoveryTaskEntity;
import com.aiworkspace.orchestrator.services.SourceRecoveryTaskService;
import java.util.concurrent.atomic.AtomicReference;

public record SourceRecoveryTaskTestContext(SourceRecoveryTaskService service, AtomicReference<SourceRecoveryTaskEntity> saved) {
}
