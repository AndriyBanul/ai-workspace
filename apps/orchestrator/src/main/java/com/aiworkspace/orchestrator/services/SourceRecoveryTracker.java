package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.RecoveryClaim;
import com.aiworkspace.workspaces.models.WorkspaceFile;

public interface SourceRecoveryTracker {

    default void queueSubmission(WorkspaceFile source, String jobId) {
    }

    default void assignJob(WorkspaceFile source, String jobId, RecoveryClaim claim) {
    }

    RecoveryClaim startAttempt(WorkspaceFile source, SourceOperationType operationType);

    void assertCurrent(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim);

    void renewLease(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim);

    void complete(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim);

    void fail(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim, Exception exception);

    static SourceRecoveryTracker noop() {
        return new SourceRecoveryTracker() {
            @Override
            public RecoveryClaim startAttempt(WorkspaceFile source, SourceOperationType operationType) {
                return new RecoveryClaim("no-op", 1);
            }

            @Override
            public void assertCurrent(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim) {
            }

            @Override
            public void renewLease(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim) {
            }

            @Override
            public void complete(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim) {
            }

            @Override
            public void fail(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim,
                    Exception exception) {
            }
        };
    }
}
