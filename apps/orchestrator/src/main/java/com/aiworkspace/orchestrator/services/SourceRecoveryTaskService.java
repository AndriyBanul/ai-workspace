package com.aiworkspace.orchestrator.services;

import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.orchestrator.config.SourceRecoveryProperties;
import com.aiworkspace.orchestrator.entities.SourceRecoveryTaskEntity;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.SourceRecoveryStatus;
import com.aiworkspace.orchestrator.models.SourceRecoveryTask;
import com.aiworkspace.orchestrator.repositories.SourceRecoveryTaskRepository;
import com.aiworkspace.workspaces.exceptions.WorkspaceSourceConflictException;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceRecoveryTaskService implements SourceRecoveryTracker {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final SourceRecoveryTaskRepository repository;
    private final SourceRetryPolicy retryPolicy;
    private final SourceRecoveryProperties properties;
    private final Clock clock;

    @Autowired
    public SourceRecoveryTaskService(SourceRecoveryTaskRepository repository, SourceRetryPolicy retryPolicy,
            SourceRecoveryProperties properties) {
        this(repository, retryPolicy, properties, Clock.systemUTC());
    }

    SourceRecoveryTaskService(SourceRecoveryTaskRepository repository, SourceRetryPolicy retryPolicy,
            SourceRecoveryProperties properties, Clock clock) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int startAttempt(WorkspaceFile source, SourceOperationType operationType) {
        Instant now = Instant.now(clock);
        SourceRecoveryTaskEntity task = repository.findForUpdate(source.id())
                .orElseGet(() -> newTask(source, operationType, now));
        if (task.getStatus() == SourceRecoveryStatus.RUNNING && task.getLeaseExpiresAt() != null
                && task.getLeaseExpiresAt().isAfter(now)) {
            throw new WorkspaceSourceConflictException("Workspace source operation is already running");
        }
        boolean resetAttempts = task.getOperationType() != operationType
                || task.getStatus() == SourceRecoveryStatus.COMPLETED
                || task.getStatus() == SourceRecoveryStatus.DEAD_LETTER;
        task.start(operationType, now, now.plus(properties.leaseDuration()), resetAttempts);
        return repository.save(task).getAttemptCount();
    }

    @Override
    @Transactional
    public void complete(WorkspaceFile source, SourceOperationType operationType) {
        Instant now = Instant.now(clock);
        SourceRecoveryTaskEntity task = repository.findForUpdate(source.id())
                .orElseGet(() -> newTask(source, operationType, now));
        Instant reconcileAt = operationType == SourceOperationType.PROCESS
                ? now.plus(properties.reconciliationInterval()) : null;
        task.complete(reconcileAt, now);
        repository.save(task);
    }

    @Override
    @Transactional
    public void fail(WorkspaceFile source, SourceOperationType operationType, Exception exception) {
        Instant now = Instant.now(clock);
        SourceRecoveryTaskEntity task = repository.findForUpdate(source.id())
                .orElseGet(() -> newTask(source, operationType, now));
        SourceRetryPolicy.RetryDecision decision = retryPolicy.decide(exception, task.getAttemptCount(), now);
        String code = errorCode(exception);
        String message = errorMessage(exception);
        if (properties.enabled() && decision.retryable()) {
            task.schedule(decision.nextAttemptAt(), code, message, now);
        } else {
            task.deadLetter(code, message, now);
        }
        repository.save(task);
    }

    @Transactional(readOnly = true)
    public List<SourceRecoveryTask> findDueTasks() {
        Instant now = Instant.now(clock);
        return repository.findDueTasks(
                        SourceRecoveryStatus.SCHEDULED, SourceRecoveryStatus.RUNNING,
                        SourceRecoveryStatus.COMPLETED, now, PageRequest.of(0, properties.batchSize()))
                .stream().map(this::toModel).toList();
    }

    @Transactional(readOnly = true)
    public SourceRecoveryTask getTask(String workspaceId, String sourceId) {
        return repository.findById(sourceId)
                .filter(task -> task.getWorkspaceId().equals(workspaceId))
                .map(this::toModel)
                .orElseThrow(() -> new NoSuchElementException("Source recovery task was not found"));
    }

    @Transactional
    public void scheduleRepair(WorkspaceFile source, String errorCode, String errorMessage) {
        Instant now = Instant.now(clock);
        SourceRecoveryTaskEntity task = repository.findForUpdate(source.id())
                .orElseGet(() -> newTask(source, SourceOperationType.PROCESS, now));
        task.prepare(SourceOperationType.PROCESS, now, now);
        task.schedule(now, errorCode, truncate(errorMessage), now);
        repository.save(task);
    }

    @Transactional
    public void deferReconciliation(WorkspaceFile source) {
        complete(source, SourceOperationType.PROCESS);
    }

    @Transactional
    public void recordIntegrityFailure(WorkspaceFile source, String errorCode, String errorMessage) {
        Instant now = Instant.now(clock);
        SourceRecoveryTaskEntity task = repository.findForUpdate(source.id())
                .orElseGet(() -> newTask(source, SourceOperationType.PROCESS, now));
        task.deadLetter(errorCode, truncate(errorMessage), now);
        repository.save(task);
    }

    @Transactional
    public void completeDeletedTask(SourceRecoveryTask recoveryTask) {
        Instant now = Instant.now(clock);
        repository.findForUpdate(recoveryTask.sourceId()).ifPresent(task -> {
            task.complete(null, now);
            repository.save(task);
        });
    }

    private SourceRecoveryTaskEntity newTask(WorkspaceFile source, SourceOperationType operationType, Instant now) {
        return SourceRecoveryTaskEntity.builder()
                .sourceId(source.id())
                .workspaceId(source.workspaceId())
                .operationType(operationType)
                .status(SourceRecoveryStatus.SCHEDULED)
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private SourceRecoveryTask toModel(SourceRecoveryTaskEntity task) {
        return new SourceRecoveryTask(
                task.getSourceId(), task.getWorkspaceId(), task.getOperationType(), task.getStatus(),
                task.getAttemptCount(), task.getNextAttemptAt(), task.getLeaseExpiresAt(), task.getLastErrorCode(),
                task.getLastErrorMessage(), task.getCreatedAt(), task.getUpdatedAt());
    }

    private String errorCode(Exception exception) {
        if (exception instanceof DocumentProcessingException documentException) {
            return documentException.code().name();
        }
        return exception == null ? "UNKNOWN" : exception.getClass().getSimpleName();
    }

    private String errorMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception == null ? "Unknown error" : exception.getClass().getSimpleName();
        }
        return truncate(exception.getMessage());
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_MESSAGE_LENGTH ? value : value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
