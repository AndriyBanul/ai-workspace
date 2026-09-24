package com.aiworkspace.orchestrator.services;

import com.aiworkspace.orchestrator.config.SourceRecoveryProperties;
import com.aiworkspace.orchestrator.models.RecoveryClaim;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Renews leases while slow provider calls are running; fencing still applies if renewal fails. */
@Service
public class SourceLeaseKeeper {

    private static final Logger log = LoggerFactory.getLogger(SourceLeaseKeeper.class);
    private final SourceRecoveryTracker tracker;
    private final ScheduledExecutorService scheduler;
    private final long intervalMillis;

    public SourceLeaseKeeper(SourceRecoveryTracker tracker, ScheduledExecutorService sourceLeaseScheduler,
            SourceRecoveryProperties properties) {
        this.tracker = tracker;
        this.scheduler = sourceLeaseScheduler;
        this.intervalMillis = Math.max(100L, properties.leaseDuration().toMillis() / 3);
    }

    public Lease keep(WorkspaceFile source, SourceOperationType operationType, RecoveryClaim claim) {
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
            try {
                tracker.renewLease(source, operationType, claim);
            } catch (RuntimeException exception) {
                log.warn("Source lease renewal failed workspaceId={} sourceId={} operation={}",
                        source.workspaceId(), source.id(), operationType, exception);
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @FunctionalInterface
    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
