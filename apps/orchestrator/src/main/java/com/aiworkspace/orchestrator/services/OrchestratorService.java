package com.aiworkspace.orchestrator.services;

import com.aiworkspace.audio.models.AudioTranscriptionResponse;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.documents.models.TextDocumentUploadResponse;
import com.aiworkspace.documents.models.WebPageExtractRequest;
import com.aiworkspace.documents.models.WebPageExtractResponse;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.images.models.ImageDescriptionResponse;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.orchestrator.models.RecoveryClaim;
import com.aiworkspace.orchestrator.models.SourceRecoveryTask;
import com.aiworkspace.orchestrator.models.SourceProcessingResult;
import com.aiworkspace.videos.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.YouTubeVideoIngestionRequest;
import com.aiworkspace.videos.models.YouTubeVideoIngestionResponse;
import com.aiworkspace.videos.services.VideoService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.CreateWorkspaceUrlSourceRequest;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.models.WorkspaceFileStatus;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final DocumentService documentService;
    private final VideoService videoService;
    private final WorkspaceFileService workspaceFileService;
    private final IngestionJobService ingestionJobService;
    private final WorkspaceService workspaceService;
    private final Executor executor;
    private final OrchestratorValidator orchestratorValidator;
    private final SourceLifecycleCoordinator lifecycleCoordinator;
    private final WorkspaceSourceProcessingService sourceProcessingService;

    public OrchestratorService(
            DocumentService documentService,
            AudioService audioService,
            ImageService imageService,
            VideoService videoService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            IngestionJobService ingestionJobService,
            WorkspaceService workspaceService,
            @Qualifier("orchestratorTaskExecutor") Executor executor
    ) {
        this(
                documentService,
                audioService,
                imageService,
                videoService,
                workspaceFileService,
                knowledgeService,
                ingestionJobService,
                workspaceService,
                executor,
                new OrchestratorValidator(),
                new SourceLifecycleCoordinator(ingestionJobService, workspaceFileService),
                new WorkspaceSourceProcessingService(documentService, audioService, imageService, videoService,
                        workspaceFileService, knowledgeService)
        );
    }

    public OrchestratorService(
            DocumentService documentService,
            AudioService audioService,
            ImageService imageService,
            VideoService videoService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            IngestionJobService ingestionJobService,
            WorkspaceService workspaceService,
            Executor executor,
            OrchestratorValidator orchestratorValidator
    ) {
        this(documentService, audioService, imageService, videoService, workspaceFileService, knowledgeService,
                ingestionJobService, workspaceService, executor, orchestratorValidator,
                new SourceLifecycleCoordinator(ingestionJobService, workspaceFileService),
                new WorkspaceSourceProcessingService(documentService, audioService, imageService, videoService,
                        workspaceFileService, knowledgeService));
    }

    public OrchestratorService(
            DocumentService documentService,
            AudioService audioService,
            ImageService imageService,
            VideoService videoService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            IngestionJobService ingestionJobService,
            WorkspaceService workspaceService,
            @Qualifier("orchestratorTaskExecutor") Executor executor,
            OrchestratorValidator orchestratorValidator,
            SourceLifecycleCoordinator lifecycleCoordinator,
            WorkspaceSourceProcessingService sourceProcessingService
    ) {
        this(documentService, videoService, workspaceFileService, ingestionJobService, workspaceService,
                executor, orchestratorValidator, lifecycleCoordinator, sourceProcessingService);
    }

    @Autowired
    public OrchestratorService(DocumentService documentService, VideoService videoService,
            WorkspaceFileService workspaceFileService, IngestionJobService ingestionJobService,
            WorkspaceService workspaceService, @Qualifier("orchestratorTaskExecutor") Executor executor,
            OrchestratorValidator orchestratorValidator, SourceLifecycleCoordinator lifecycleCoordinator,
            WorkspaceSourceProcessingService sourceProcessingService) {
        this.documentService = documentService;
        this.videoService = videoService;
        this.workspaceFileService = workspaceFileService;
        this.ingestionJobService = ingestionJobService;
        this.workspaceService = workspaceService;
        this.executor = executor;
        this.orchestratorValidator = orchestratorValidator;
        this.lifecycleCoordinator = lifecycleCoordinator;
        this.sourceProcessingService = sourceProcessingService;
    }

    public OrchestrationSubmission process(
            String ownerId,
            String workspaceId,
            OrchestrationContent document,
            OrchestrationContent audio,
            OrchestrationContent image,
            OrchestrationContent video
    ) throws IOException {
        orchestratorValidator.validateOwnerId(ownerId);
        orchestratorValidator.validateWorkspaceId(workspaceId);
        String trimmedOwnerId = ownerId.trim();
        String trimmedWorkspaceId = workspaceId.trim();
        workspaceService.getWorkspace(trimmedOwnerId, trimmedWorkspaceId);
        List<SubmittedContent> submittedContent = new ArrayList<>();
        List<IngestionContentType> skippedTypes = new ArrayList<>();

        collectContent(IngestionContentType.DOCUMENTS, WorkspaceFileSourceType.DOCUMENT, document, trimmedWorkspaceId,
                submittedContent, skippedTypes);
        collectContent(IngestionContentType.AUDIO, WorkspaceFileSourceType.AUDIO, audio, trimmedWorkspaceId,
                submittedContent, skippedTypes);
        collectContent(IngestionContentType.IMAGES, WorkspaceFileSourceType.IMAGE, image, trimmedWorkspaceId,
                submittedContent, skippedTypes);
        collectContent(IngestionContentType.VIDEOS, WorkspaceFileSourceType.VIDEO, video, trimmedWorkspaceId,
                submittedContent, skippedTypes);

        List<IngestionContentType> submittedTypes = submittedContent.stream()
                .map(SubmittedContent::contentType)
                .toList();
        Map<String, String> sourceIds = new LinkedHashMap<>();
        submittedContent.forEach(content -> sourceIds.put(
                content.contentType().apiName(),
                content.file().id()
        ));
        IngestionJobDetails job = lifecycleCoordinator.queueSubmission(trimmedWorkspaceId, submittedTypes,
                skippedTypes, submittedContent.stream().map(SubmittedContent::file).toList());

        submittedContent.forEach(content -> submit(job, content));

        return new OrchestrationSubmission(
                job.jobId(),
                job.workspaceId(),
                job.status(),
                submittedTypes.stream().map(IngestionContentType::apiName).toList(),
                skippedTypes.stream().map(IngestionContentType::apiName).toList(),
                sourceIds
        );
    }

    public IngestionJobDetails findJob(String ownerId, String jobId) {
        orchestratorValidator.validateOwnerId(ownerId);
        IngestionJobDetails job = ingestionJobService.getJob(jobId);
        workspaceService.getWorkspace(ownerId.trim(), job.workspaceId());
        return job;
    }

    public List<IngestionJobDetails> listJobs(String ownerId, String workspaceId, int limit) {
        String ownedId = workspaceService.getWorkspace(ownerId, workspaceId).id();
        return ingestionJobService.listRecentJobs(ownedId, limit);
    }

    public TextDocumentUploadResponse ingestDocument(String ownerId, String workspaceId, String filename,
            String contentType, byte[] content) throws IOException {
        SubmittedContent submitted = createUploadedSource(
                ownerId, workspaceId, IngestionContentType.DOCUMENTS, WorkspaceFileSourceType.DOCUMENT,
                new OrchestrationContent(filename, contentType, content));
        try {
            return ((SourceProcessingResult.Document) processSynchronously(submitted)).response();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Document ingestion was interrupted", exception);
        }
    }

    public AudioTranscriptionResponse ingestAudio(String ownerId, String workspaceId, String filename,
            String contentType, byte[] content) throws IOException, InterruptedException {
        SubmittedContent submitted = createUploadedSource(
                ownerId, workspaceId, IngestionContentType.AUDIO, WorkspaceFileSourceType.AUDIO,
                new OrchestrationContent(filename, contentType, content));
        return ((SourceProcessingResult.Audio) processSynchronously(submitted)).response();
    }

    public ImageDescriptionResponse ingestImage(String ownerId, String workspaceId, String filename,
            String contentType, byte[] content) throws IOException, InterruptedException {
        SubmittedContent submitted = createUploadedSource(
                ownerId, workspaceId, IngestionContentType.IMAGES, WorkspaceFileSourceType.IMAGE,
                new OrchestrationContent(filename, contentType, content));
        return ((SourceProcessingResult.Image) processSynchronously(submitted)).response();
    }

    public VideoDescriptionResponse ingestVideo(String ownerId, String workspaceId, String filename,
            String contentType, byte[] content) throws IOException, InterruptedException {
        SubmittedContent submitted = createUploadedSource(
                ownerId, workspaceId, IngestionContentType.VIDEOS, WorkspaceFileSourceType.VIDEO,
                new OrchestrationContent(filename, contentType, content));
        return ((SourceProcessingResult.Video) processSynchronously(submitted)).response();
    }

    public WebPageExtractResponse ingestWebPage(String ownerId, WebPageExtractRequest request) throws IOException {
        documentService.validateWebPageRequest(request);
        String workspaceId = workspaceService.getWorkspace(ownerId, request.workspaceId()).id();
        WorkspaceFile source = workspaceFileService.createUrlSource(CreateWorkspaceUrlSourceRequest.builder()
                .workspaceId(workspaceId)
                .sourceType(WorkspaceFileSourceType.WEB_PAGE)
                .displayName("Web page")
                .sourceUrl(request.url())
                .contentType("text/html")
                .build());
        SubmittedContent submitted = new SubmittedContent(IngestionContentType.DOCUMENTS, source, null);
        try {
            return ((SourceProcessingResult.WebPage) processSynchronously(submitted)).response();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Web page ingestion was interrupted", exception);
        }
    }

    public YouTubeVideoIngestionResponse ingestYouTube(String ownerId, YouTubeVideoIngestionRequest request)
            throws IOException, InterruptedException {
        if (request == null) {
            throw new IllegalArgumentException("YouTube ingestion request must not be null");
        }
        String workspaceId = workspaceService.getWorkspace(ownerId, request.workspaceId()).id();
        String canonicalUrl = videoService.canonicalYouTubeUrl(request.url());
        WorkspaceFile source = workspaceFileService.createUrlSource(CreateWorkspaceUrlSourceRequest.builder()
                .workspaceId(workspaceId)
                .sourceType(WorkspaceFileSourceType.YOUTUBE)
                .displayName("YouTube video " + videoService.youtubeVideoId(canonicalUrl))
                .sourceUrl(canonicalUrl)
                .contentType("video/youtube")
                .build());
        return ((SourceProcessingResult.YouTube) processSynchronously(
                new SubmittedContent(IngestionContentType.VIDEOS, source, null))).response();
    }

    public OrchestrationSubmission reprocessSource(String ownerId, String workspaceId, String sourceId)
            throws IOException {
        WorkspaceFile source = workspaceService.getFile(ownerId, workspaceId, sourceId);
        IngestionContentType contentType = contentType(source.sourceType());
        OrchestrationContent original = source.urlBacked() ? null : readOriginal(source);
        SubmittedContent submitted = new SubmittedContent(contentType, source, original);
        IngestionJobDetails job = lifecycleCoordinator.queueSubmission(source.workspaceId(), List.of(contentType),
                java.util.Arrays.stream(IngestionContentType.values()).filter(type -> type != contentType).toList(),
                List.of(source));
        submit(job, submitted);
        return new OrchestrationSubmission(
                job.jobId(), job.workspaceId(), job.status(), List.of(contentType.apiName()), List.of(),
                Map.of(contentType.apiName(), source.id()));
    }

    public void recoverSource(SourceRecoveryTask task) throws IOException {
        WorkspaceFile source = workspaceFileService.getFile(task.workspaceId(), task.sourceId());
        RecoveryClaim claim = lifecycleCoordinator.startRecoveryAttempt(source);
        SourceLeaseKeeper.Lease lease = null;
        try {
            lease = lifecycleCoordinator.keepRecoveryLease(source, claim);
            if (source.status() == WorkspaceFileStatus.PROCESSING) {
                source = workspaceFileService.markFailed(task.workspaceId(), task.sourceId());
            }
            IngestionContentType contentType = contentType(source.sourceType());
            SubmittedContent submitted = new SubmittedContent(
                    contentType, source, source.urlBacked() ? null : readOriginal(source));
            IngestionJobDetails job = pendingJob(task, contentType);
            if (job == null) {
                job = singleSourceJob(source.workspaceId(), contentType);
                lifecycleCoordinator.assignRecoveryJob(source, job.jobId(), claim);
            }
            submitRecovered(job, submitted, claim, lease);
        } catch (RuntimeException exception) {
            if (lease != null) lease.close();
            try {
                lifecycleCoordinator.failRecoveryAttempt(source, claim, exception);
            } catch (RuntimeException recoveryException) {
                exception.addSuppressed(recoveryException);
            }
            throw exception;
        }
    }

    private IngestionJobDetails pendingJob(SourceRecoveryTask task, IngestionContentType contentType) {
        if (task.jobId() == null) {
            return null;
        }
        IngestionJobDetails job;
        try {
            job = ingestionJobService.getJob(task.jobId());
        } catch (NoSuchElementException exception) {
            return null;
        }
        IngestionStepStatus status = job.steps().stream()
                .filter(step -> step.type().equals(contentType.apiName()))
                .map(step -> step.status())
                .findFirst().orElse(null);
        if (status == IngestionStepStatus.PENDING) {
            return job;
        }
        if (status == IngestionStepStatus.RUNNING) {
            ingestionJobService.markStepFailed(job.jobId(), contentType,
                    new IllegalStateException("Previous processing attempt expired"));
        }
        return null;
    }

    private SourceProcessingResult processSynchronously(SubmittedContent submitted)
            throws IOException, InterruptedException {
        IngestionJobDetails job = singleSourceJob(submitted.file().workspaceId(), submitted.contentType());
        try {
            return lifecycleCoordinator.process(
                    job, submitted.contentType(), submitted.file(), () -> processSource(job, submitted));
        } catch (InterruptedException exception) {
            throw exception;
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Failed to process workspace source", exception);
        }
    }

    private SubmittedContent createUploadedSource(String ownerId, String workspaceId, IngestionContentType contentType,
            WorkspaceFileSourceType sourceType, OrchestrationContent content) throws IOException {
        orchestratorValidator.validateOwnerId(ownerId);
        orchestratorValidator.validateWorkspaceId(workspaceId);
        String ownedWorkspaceId = workspaceService.getWorkspace(ownerId.trim(), workspaceId.trim()).id();
        WorkspaceFile file = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(ownedWorkspaceId)
                .sourceType(sourceType)
                .originalFilename(content.filename())
                .contentType(content.contentType())
                .content(new ByteArrayInputStream(content.content()))
                .build());
        return new SubmittedContent(contentType, file, content);
    }

    private IngestionJobDetails singleSourceJob(String workspaceId, IngestionContentType contentType) {
        List<IngestionContentType> skipped = java.util.Arrays.stream(IngestionContentType.values())
                .filter(type -> type != contentType)
                .toList();
        return ingestionJobService.createJob(workspaceId, List.of(contentType), skipped);
    }

    private void collectContent(
            IngestionContentType contentType,
            WorkspaceFileSourceType sourceType,
            OrchestrationContent content,
            String workspaceId,
            List<SubmittedContent> submitted,
            List<IngestionContentType> skipped
    ) throws IOException {
        if (content == null) {
            skipped.add(contentType);
            return;
        }

        WorkspaceFile file = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspaceId)
                .sourceType(sourceType)
                .originalFilename(content.filename())
                .contentType(content.contentType())
                .content(new ByteArrayInputStream(content.content()))
                .build());
        submitted.add(new SubmittedContent(contentType, file, content));
    }

    private void submit(IngestionJobDetails job, SubmittedContent content) {
        afterCommit(() -> {
            try {
                CompletableFuture.runAsync(() -> runTask(job.jobId(), content.file().workspaceId(),
                        content.file().id(), content.contentType()), executor);
            } catch (RejectedExecutionException exception) {
                log.warn("Ingestion worker queue is full; the persisted source will be dispatched by recovery "
                        + "workspaceId={} sourceId={} jobId={}", content.file().workspaceId(), content.file().id(),
                        job.jobId());
            }
        });
    }

    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private void submitRecovered(IngestionJobDetails job, SubmittedContent content, RecoveryClaim claim,
            SourceLeaseKeeper.Lease lease) {
        try {
            CompletableFuture.runAsync(() -> runRecoveredTask(job.jobId(), content.file().workspaceId(),
                    content.file().id(), content.contentType(), claim, lease), executor);
        } catch (RejectedExecutionException exception) {
            throw new java.io.UncheckedIOException(new IOException("Ingestion worker queue is full", exception));
        }
    }

    private void runTask(String jobId, String workspaceId, String sourceId, IngestionContentType contentType) {
        try {
            IngestionJobDetails job = ingestionJobService.getJob(jobId);
            WorkspaceFile source = workspaceFileService.getFile(workspaceId, sourceId);
            SubmittedContent content = new SubmittedContent(contentType, source,
                    source.urlBacked() ? null : readOriginal(source));
            lifecycleCoordinator.process(
                    job, contentType, source, () -> processSource(job, content));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while running {} orchestration task", contentType.apiName(), exception);
        } catch (NoSuchElementException exception) {
            ingestionJobService.markStepFailed(jobId, contentType, exception);
            log.warn("Queued source disappeared workspaceId={} sourceId={} jobId={}",
                    workspaceId, sourceId, jobId, exception);
        } catch (Exception exception) {
            log.warn("Failed to run {} orchestration task", contentType.apiName(), exception);
        }
    }

    private void runRecoveredTask(String jobId, String workspaceId, String sourceId,
            IngestionContentType contentType, RecoveryClaim claim, SourceLeaseKeeper.Lease lease) {
        try (lease) {
            IngestionJobDetails job = ingestionJobService.getJob(jobId);
            WorkspaceFile source = workspaceFileService.getFile(workspaceId, sourceId);
            SubmittedContent content = new SubmittedContent(contentType, source,
                    source.urlBacked() ? null : readOriginal(source));
            lifecycleCoordinator.processClaimedRecovery(
                    job, contentType, source, claim, () -> processSource(job, content));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while recovering {} orchestration task", contentType.apiName(), exception);
        } catch (Exception exception) {
            log.warn("Failed to recover {} orchestration task", contentType.apiName(), exception);
        }
    }

    private SourceProcessingResult processSource(IngestionJobDetails job, SubmittedContent content) throws Exception {
        return sourceProcessingService.process(job, content.file(), content.original());
    }

    private OrchestrationContent readOriginal(WorkspaceFile source) {
        return new OrchestrationContent(source.originalFilename(), source.contentType(), new byte[0]);
    }

    private IngestionContentType contentType(WorkspaceFileSourceType sourceType) {
        return switch (sourceType) {
            case DOCUMENT, WEB_PAGE -> IngestionContentType.DOCUMENTS;
            case AUDIO -> IngestionContentType.AUDIO;
            case IMAGE -> IngestionContentType.IMAGES;
            case VIDEO, YOUTUBE -> IngestionContentType.VIDEOS;
        };
    }

    private record SubmittedContent(
            IngestionContentType contentType,
            WorkspaceFile file,
            OrchestrationContent original
    ) {
    }
}
