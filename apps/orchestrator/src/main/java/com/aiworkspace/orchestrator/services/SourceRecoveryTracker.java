package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.workspaces.models.WorkspaceFile;

public interface SourceRecoveryTracker {

    int startAttempt(WorkspaceFile source, SourceOperationType operationType);

    void complete(WorkspaceFile source, SourceOperationType operationType);

    void fail(WorkspaceFile source, SourceOperationType operationType, Exception exception);

    static SourceRecoveryTracker noop() {
        return new SourceRecoveryTracker() {
            @Override
            public int startAttempt(WorkspaceFile source, SourceOperationType operationType) {
                return 1;
            }

            @Override
            public void complete(WorkspaceFile source, SourceOperationType operationType) {
            }

            @Override
            public void fail(WorkspaceFile source, SourceOperationType operationType, Exception exception) {
            }
        };
    }
}
