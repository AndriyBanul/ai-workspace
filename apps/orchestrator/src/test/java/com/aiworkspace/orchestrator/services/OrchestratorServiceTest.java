package com.aiworkspace.orchestrator.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.mappers.IngestionJobDetailsMapperImpl;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.RecoveryClaim;
import com.aiworkspace.orchestrator.models.SourceOperationType;
import com.aiworkspace.orchestrator.models.SourceRecoveryStatus;
import com.aiworkspace.orchestrator.models.SourceRecoveryTask;
import com.aiworkspace.orchestrator.repositories.IngestionJobRepository;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.services.VideoService;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrchestratorServiceTest {

    @Test
    void recoveredPendingSubmissionFinishesItsOriginalJob() throws IOException {
        CapturingKnowledgeRepository knowledgeRepository = new CapturingKnowledgeRepository();
        CapturingIngestionJobRepository jobRepository = new CapturingIngestionJobRepository();
        TestWorkspaceFileService files = new TestWorkspaceFileService();
        IngestionJobService jobs = new IngestionJobService(jobRepository, new IngestionJobDetailsMapperImpl());
        KnowledgeService knowledge = new KnowledgeService(knowledgeRepository, (question, context) -> "Answer");
        TestDocumentService documents = new TestDocumentService();
        TestAudioService audio = new TestAudioService();
        TestImageService images = new TestImageService();
        TestVideoService videos = new TestVideoService();
        SourceRecoveryTracker recovery = mock(SourceRecoveryTracker.class);
        when(recovery.startAttempt(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(SourceOperationType.PROCESS)))
                .thenReturn(new RecoveryClaim("lease-1", 1));
        SourceLeaseKeeper keeper = mock(SourceLeaseKeeper.class);
        SourceLeaseKeeper.Lease queuedLease = mock(SourceLeaseKeeper.Lease.class);
        SourceLeaseKeeper.Lease processingLease = mock(SourceLeaseKeeper.Lease.class);
        when(keeper.keep(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(SourceOperationType.PROCESS),
                org.mockito.ArgumentMatchers.any())).thenReturn(queuedLease, processingLease);
        SourceLifecycleCoordinator lifecycle = new SourceLifecycleCoordinator(jobs, files, recovery,
                new SourceCompletionService(jobs, files, recovery), keeper, IngestionTelemetry.NOOP);
        CapturingExecutor executor = new CapturingExecutor();
        OrchestratorService service = new OrchestratorService(
                documents, audio, images, videos, files, knowledge, jobs, new TestWorkspaceService(), executor,
                new OrchestratorValidator(), lifecycle,
                new WorkspaceSourceProcessingService(documents, audio, images, videos, files, knowledge));

        var submission = service.process("owner-1", "workspace-1",
                new OrchestrationContent("report.txt", "text/plain", "A stored report".getBytes()),
                null, null, null);
        String sourceId = submission.sourceIds().get("documents");
        WorkspaceFile source = files.files.get(sourceId);
        SourceRecoveryTask pending = new SourceRecoveryTask(
                sourceId, "workspace-1", submission.jobId(), SourceOperationType.PROCESS,
                SourceRecoveryStatus.SCHEDULED, 0, Instant.now(), null, null, null,
                Instant.now(), Instant.now());

        service.recoverSource(pending);
        verify(keeper).keep(source, SourceOperationType.PROCESS, new RecoveryClaim("lease-1", 1));
        verify(queuedLease, never()).close();
        executor.runLast();
        verify(queuedLease).close();
        verify(processingLease).close();

        assertEquals(IngestionJobStatus.COMPLETED, service.findJob("owner-1", submission.jobId()).status());
        assertEquals(1, jobRepository.jobs.size());
        assertEquals(WorkspaceFileStatus.PROCESSED, files.files.get(sourceId).status());
        verify(recovery).startAttempt(source, SourceOperationType.PROCESS);
    }

    @Test
    void retainsFailedSourceWhenAnEmptyDocumentCannotBeProcessed() throws IOException {
        CapturingKnowledgeRepository knowledgeRepository = new CapturingKnowledgeRepository();
        CapturingIngestionJobRepository jobRepository = new CapturingIngestionJobRepository();
        TestWorkspaceFileService workspaceFileService = new TestWorkspaceFileService();
        OrchestratorService service = new OrchestratorService(
                new EmptyRejectingDocumentService(),
                new TestAudioService(),
                new TestImageService(),
                new TestVideoService(),
                workspaceFileService,
                new KnowledgeService(knowledgeRepository, (question, context) -> "Answer"),
                new IngestionJobService(jobRepository, new IngestionJobDetailsMapperImpl()),
                new TestWorkspaceService(),
                Runnable::run
        );

        var submission = service.process(
                "owner-1", "workspace-1",
                new OrchestrationContent("empty.txt", "text/plain", new byte[0]), null, null, null);

        assertEquals(1, workspaceFileService.files.size());
        assertEquals(WorkspaceFileStatus.FAILED, workspaceFileService.files.get("file-1").status());
        assertEquals(IngestionJobStatus.FAILED, service.findJob("owner-1", submission.jobId()).status());
        assertTrue(knowledgeRepository.items.isEmpty());
    }

    @Test
    void processesProvidedContentAndSkipsMissingContent() throws IOException {
        CapturingKnowledgeRepository knowledgeRepository = new CapturingKnowledgeRepository();
        CapturingIngestionJobRepository jobRepository = new CapturingIngestionJobRepository();
        TestWorkspaceFileService workspaceFileService = new TestWorkspaceFileService();
        OrchestratorService service = new OrchestratorService(
                new TestDocumentService(),
                new TestAudioService(),
                new TestImageService(),
                new TestVideoService(),
                workspaceFileService,
                new KnowledgeService(knowledgeRepository, (question, context) -> "Answer"),
                new IngestionJobService(jobRepository, new IngestionJobDetailsMapperImpl()),
                new TestWorkspaceService(),
                Runnable::run
        );

        var submission = service.process(
                "owner-1",
                "workspace-1",
                new OrchestrationContent("document.txt", "text/plain", "Document input".getBytes()),
                null,
                new OrchestrationContent("image.png", "image/png", new byte[] {1, 2, 3}),
                null
        );

        assertNotNull(submission.jobId());
        assertEquals("workspace-1", submission.workspaceId());
        assertEquals(IngestionJobStatus.RUNNING, submission.status());
        assertEquals(List.of("documents", "images"), submission.submitted());
        assertEquals(List.of("audio", "videos"), submission.skipped());
        assertEquals("file-1", submission.sourceIds().get("documents"));
        assertEquals("file-2", submission.sourceIds().get("images"));
        assertEquals("Parsed document text", knowledgeRepository.items.get(0).content());
        assertEquals("file-1", knowledgeRepository.items.get(0).sourceId());
        assertEquals("test-document-parser-1", knowledgeRepository.items.get(0).parserVersion());
        assertEquals("Image description", knowledgeRepository.items.get(1).content());

        var job = service.findJob("owner-1", submission.jobId());
        assertEquals(IngestionJobStatus.COMPLETED, job.status());
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.DOCUMENTS, IngestionStepStatus.COMPLETED);
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.AUDIO, IngestionStepStatus.SKIPPED);
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.IMAGES, IngestionStepStatus.COMPLETED);
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.VIDEOS, IngestionStepStatus.SKIPPED);
        assertEquals(List.of(WorkspaceFileStatus.PROCESSED, WorkspaceFileStatus.PROCESSED),
                workspaceFileService.files.values().stream().map(WorkspaceFile::status).toList());
    }

    @Test
    void marksFailedStepAndPartiallyFailedJobWhenTaskFails() throws IOException {
        CapturingKnowledgeRepository knowledgeRepository = new CapturingKnowledgeRepository();
        CapturingIngestionJobRepository jobRepository = new CapturingIngestionJobRepository();
        TestWorkspaceFileService workspaceFileService = new TestWorkspaceFileService();
        OrchestratorService service = new OrchestratorService(
                new TestDocumentService(),
                new FailingAudioService(),
                new TestImageService(),
                new TestVideoService(),
                workspaceFileService,
                new KnowledgeService(knowledgeRepository, (question, context) -> "Answer"),
                new IngestionJobService(jobRepository, new IngestionJobDetailsMapperImpl()),
                new TestWorkspaceService(),
                Runnable::run
        );

        var submission = service.process(
                "owner-1",
                "workspace-1",
                new OrchestrationContent("document.txt", "text/plain", "Document input".getBytes()),
                new OrchestrationContent("audio.mp3", "audio/mpeg", new byte[] {1}),
                null,
                null
        );

        var job = service.findJob("owner-1", submission.jobId());
        assertEquals(IngestionJobStatus.PARTIALLY_FAILED, job.status());
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.DOCUMENTS, IngestionStepStatus.COMPLETED);
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.AUDIO, IngestionStepStatus.FAILED);
        assertEquals(List.of(WorkspaceFileStatus.PROCESSED, WorkspaceFileStatus.FAILED),
                workspaceFileService.files.values().stream().map(WorkspaceFile::status).toList());
    }

    @Test
    void skipsWorkWhenTaskStartsAfterStepReachedTerminalState() throws IOException {
        CapturingKnowledgeRepository knowledgeRepository = new CapturingKnowledgeRepository();
        CapturingIngestionJobRepository jobRepository = new CapturingIngestionJobRepository();
        TestWorkspaceFileService workspaceFileService = new TestWorkspaceFileService();
        IngestionJobService ingestionJobService = new IngestionJobService(
                jobRepository,
                new IngestionJobDetailsMapperImpl()
        );
        CapturingExecutor executor = new CapturingExecutor();
        OrchestratorService service = new OrchestratorService(
                new TestDocumentService(),
                new TestAudioService(),
                new TestImageService(),
                new TestVideoService(),
                workspaceFileService,
                new KnowledgeService(knowledgeRepository, (question, context) -> "Answer"),
                ingestionJobService,
                new TestWorkspaceService(),
                executor
        );

        var submission = service.process(
                "owner-1",
                "workspace-1",
                new OrchestrationContent("document.txt", "text/plain", "Document input".getBytes()),
                null,
                null,
                null
        );
        ingestionJobService.markStepFailed(
                submission.jobId(),
                IngestionContentType.DOCUMENTS,
                new RuntimeException("Timed out")
        );
        workspaceFileService.markFailed(submission.workspaceId(), "file-1");

        executor.runNext();

        assertEquals(0, knowledgeRepository.items.size());
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.DOCUMENTS, IngestionStepStatus.FAILED);
        assertEquals(WorkspaceFileStatus.FAILED, workspaceFileService.files.get("file-1").status());
    }

    private static class TestDocumentService extends DocumentService {

        TestDocumentService() {
            super(null);
        }

        @Override
        public ParsedTextDocument extractDocumentText(String filename, String contentType, byte[] bytes) {
            return new ParsedTextDocument(
                    filename,
                    "text/plain",
                    null,
                    "Parsed document text",
                    List.of(),
                    Instant.parse("2026-09-07T12:00:00Z"),
                    "test-document-parser-1"
            );
        }
    }

    private static class EmptyRejectingDocumentService extends TestDocumentService {

        @Override
        public ParsedTextDocument extractDocumentText(String filename, String contentType, byte[] bytes) {
            throw new DocumentProcessingException(DocumentFailureCode.EMPTY_DOCUMENT);
        }
    }

    private static class TestAudioService extends AudioService {

        TestAudioService() {
            super(null, null);
        }

        @Override
        public AudioTranscription transcribe(String filename, byte[] fileContent) {
            return new AudioTranscription(filename, "Audio transcript", "en");
        }
    }

    private static class FailingAudioService extends TestAudioService {

        @Override
        public AudioTranscription transcribe(String filename, byte[] fileContent) {
            throw new IllegalStateException("Audio provider unavailable");
        }
    }

    private static class TestImageService extends ImageService {

        TestImageService() {
            super(null, null, "Describe this image.");
        }

        @Override
        public ImageDescription describe(String filename, String contentType, byte[] imageContent) {
            return new ImageDescription(filename, contentType, "Image description");
        }
    }

    private static class TestVideoService extends VideoService {

        TestVideoService() {
            super(null, null, "Describe this video.");
        }

        @Override
        public VideoDescription describe(String filename, String contentType, byte[] videoContent) {
            return new VideoDescription(filename, contentType, "Video description");
        }
    }

    private static class TestWorkspaceService extends WorkspaceService {

        TestWorkspaceService() {
            super(null, null);
        }

        @Override
        public Workspace getWorkspace(String ownerId, String workspaceId) {
            return new Workspace(workspaceId, ownerId, "Test workspace", Instant.now(), Instant.now());
        }
    }

    private static class TestWorkspaceFileService extends WorkspaceFileService {

        private final Map<String, WorkspaceFile> files = new java.util.LinkedHashMap<>();
        private final Map<String, byte[]> contents = new HashMap<>();

        TestWorkspaceFileService() {
            super(null, null, null);
        }

        @Override
        public WorkspaceFile createFile(CreateWorkspaceFileRequest request) throws IOException {
            String fileId = "file-" + (files.size() + 1);
            byte[] content = request.content().readAllBytes();
            WorkspaceFile file = WorkspaceFile.builder()
                    .id(fileId)
                    .workspaceId(request.workspaceId())
                    .originalFilename(request.originalFilename())
                    .contentType(request.contentType())
                    .sizeBytes(content.length)
                    .storageKey(request.workspaceId() + "/" + fileId)
                    .checksumSha256("checksum-" + fileId)
                    .sourceType(request.sourceType())
                    .status(WorkspaceFileStatus.UPLOADED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            files.put(fileId, file);
            contents.put(fileId, content);
            return file;
        }

        @Override
        public InputStream readContent(String workspaceId, String fileId) {
            return new ByteArrayInputStream(contents.get(fileId));
        }

        @Override
        public WorkspaceFile getFile(String workspaceId, String fileId) {
            return files.get(fileId);
        }

        @Override
        public WorkspaceFile markProcessing(String workspaceId, String fileId) {
            return updateStatus(fileId, WorkspaceFileStatus.PROCESSING);
        }

        @Override
        public WorkspaceFile markProcessed(String workspaceId, String fileId) {
            return updateStatus(fileId, WorkspaceFileStatus.PROCESSED);
        }

        @Override
        public WorkspaceFile markFailed(String workspaceId, String fileId) {
            return updateStatus(fileId, WorkspaceFileStatus.FAILED);
        }

        private WorkspaceFile updateStatus(String fileId, WorkspaceFileStatus status) {
            WorkspaceFile existingFile = files.get(fileId);
            WorkspaceFile updatedFile = WorkspaceFile.builder()
                    .id(existingFile.id())
                    .workspaceId(existingFile.workspaceId())
                    .originalFilename(existingFile.originalFilename())
                    .contentType(existingFile.contentType())
                    .sizeBytes(existingFile.sizeBytes())
                    .storageKey(existingFile.storageKey())
                    .checksumSha256(existingFile.checksumSha256())
                    .sourceType(existingFile.sourceType())
                    .status(status)
                    .createdAt(existingFile.createdAt())
                    .updatedAt(Instant.now())
                    .deletedAt(existingFile.deletedAt())
                    .build();
            files.put(fileId, updatedFile);
            return updatedFile;
        }
    }

    private static class CapturingExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            tasks.remove(0).run();
        }

        private void runLast() {
            tasks.remove(tasks.size() - 1).run();
        }
    }

    private static class CapturingKnowledgeRepository implements KnowledgeRepository {

        private final List<KnowledgeItem> items = new java.util.ArrayList<>();

        @Override
        public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) {
            return Optional.empty();
        }

        @Override
        public List<KnowledgeItem> findKnowledgeItemsByWorkspaceId(String workspaceId) {
            return List.of();
        }

        @Override
        public List<KnowledgeItem> searchKnowledgeItems(String workspaceId, String query, int limit) {
            return List.of();
        }

        @Override
        public void addKnowledgeItem(KnowledgeItem item) {
            items.add(item);
        }

        @Override
        public void deleteKnowledgeItemsBySourceId(String workspaceId, String sourceId) {
            items.removeIf(item -> item.workspaceId().equals(workspaceId) && sourceId.equals(item.sourceId()));
        }

        @Override
        public void deleteKnowledgeItemsByWorkspaceId(String workspaceId) {
            items.removeIf(item -> item.workspaceId().equals(workspaceId));
        }

        @Override
        public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
                throws IOException {
        }
    }

    private static void assertStepStatus(
            CapturingIngestionJobRepository repository,
            String jobId,
            IngestionContentType contentType,
            IngestionStepStatus expectedStatus
    ) {
        assertEquals(
                expectedStatus,
                repository.steps.get(jobId).get(contentType).status()
        );
    }

    private static class CapturingIngestionJobRepository implements IngestionJobRepository {

        private final Map<String, IngestionJob> jobs = new HashMap<>();
        private final Map<String, EnumMap<IngestionContentType, IngestionJobStep>> steps = new HashMap<>();

        @Override
        public void create(IngestionJob job, List<IngestionJobStep> steps) {
            jobs.put(job.id(), job);
            EnumMap<IngestionContentType, IngestionJobStep> stepsByType = new EnumMap<>(IngestionContentType.class);
            for (IngestionJobStep step : steps) {
                stepsByType.put(step.contentType(), step);
            }
            this.steps.put(job.id(), stepsByType);
        }

        @Override
        public Optional<IngestionJob> findJob(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }

        @Override
        public List<IngestionJobStep> findSteps(String jobId) {
            return List.copyOf(steps.get(jobId).values());
        }

        @Override
        public void updateJobStatus(
                String jobId,
                IngestionJobStatus status,
                Instant updatedAt,
                Instant completedAt
        ) {
            IngestionJob job = jobs.get(jobId);
            jobs.put(jobId, new IngestionJob(
                    job.id(),
                    job.workspaceId(),
                    status,
                    job.createdAt(),
                    updatedAt,
                    completedAt
            ));
        }

        @Override
        public boolean updateStepStatus(
                String jobId,
                IngestionContentType contentType,
                IngestionStepStatus status,
                Instant startedAt,
                Instant completedAt,
                String errorMessage,
                String errorCode
        ) {
            IngestionJobStep existingStep = steps.get(jobId).get(contentType);
            if (existingStep.status() == IngestionStepStatus.COMPLETED
                    || existingStep.status() == IngestionStepStatus.FAILED
                    || existingStep.status() == IngestionStepStatus.SKIPPED) {
                return false;
            }

            steps.get(jobId).put(contentType, new IngestionJobStep(
                    existingStep.id(),
                    existingStep.jobId(),
                    existingStep.contentType(),
                    status,
                    startedAt == null ? existingStep.startedAt() : startedAt,
                    completedAt,
                    errorMessage,
                    errorCode
            ));
            return true;
        }
    }
}
