package com.aiworkspace.orchestrator.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.config.OrchestratorProperties;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.services.VideoService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private final DocumentService documentService;
    private final AudioService audioService;
    private final ImageService imageService;
    private final VideoService videoService;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final IngestionJobService ingestionJobService;
    private final WorkspaceService workspaceService;
    private final OrchestratorProperties properties;
    private final Executor executor;
    private final OrchestratorValidator orchestratorValidator;

    public OrchestratorService(
            DocumentService documentService,
            AudioService audioService,
            ImageService imageService,
            VideoService videoService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            IngestionJobService ingestionJobService,
            WorkspaceService workspaceService,
            OrchestratorProperties properties,
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
                properties,
                executor,
                new OrchestratorValidator()
        );
    }

    @Autowired
    public OrchestratorService(
            DocumentService documentService,
            AudioService audioService,
            ImageService imageService,
            VideoService videoService,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            IngestionJobService ingestionJobService,
            WorkspaceService workspaceService,
            OrchestratorProperties properties,
            @Qualifier("orchestratorTaskExecutor") Executor executor,
            OrchestratorValidator orchestratorValidator
    ) {
        this.documentService = documentService;
        this.audioService = audioService;
        this.imageService = imageService;
        this.videoService = videoService;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.ingestionJobService = ingestionJobService;
        this.workspaceService = workspaceService;
        this.properties = properties;
        this.executor = executor;
        this.orchestratorValidator = orchestratorValidator;
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
        IngestionJobDetails job = ingestionJobService.createJob(trimmedWorkspaceId, submittedTypes, skippedTypes);

        submittedContent.forEach(content -> submit(job, content));

        return new OrchestrationSubmission(
                job.jobId(),
                job.workspaceId(),
                job.status(),
                submittedTypes.stream().map(IngestionContentType::apiName).toList(),
                skippedTypes.stream().map(IngestionContentType::apiName).toList()
        );
    }

    public OrchestrationSubmission processUploads(
            String ownerId,
            String workspaceId,
            MultipartFile document,
            MultipartFile audio,
            MultipartFile image,
            MultipartFile video
    ) throws IOException {
        return process(
                ownerId,
                workspaceId,
                contentFrom(document),
                contentFrom(audio),
                contentFrom(image),
                contentFrom(video)
        );
    }

    public IngestionJobDetails findJob(String ownerId, String jobId) {
        orchestratorValidator.validateOwnerId(ownerId);
        IngestionJobDetails job = ingestionJobService.getJob(jobId);
        workspaceService.getWorkspace(ownerId.trim(), job.workspaceId());
        return job;
    }

    private OrchestrationContent contentFrom(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        return new OrchestrationContent(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    private void collectContent(
            IngestionContentType contentType,
            WorkspaceFileSourceType sourceType,
            OrchestrationContent content,
            String workspaceId,
            List<SubmittedContent> submitted,
            List<IngestionContentType> skipped
    ) throws IOException {
        if (content == null || content.isEmpty()) {
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
        CompletableFuture.runAsync(() -> runTask(job, content), executor)
                .orTimeout(properties.taskTimeout().toSeconds(), TimeUnit.SECONDS)
                .exceptionally(exception -> {
                    if (isTimeout(exception)) {
                        TimeoutException timeout = new TimeoutException(
                                content.contentType().apiName() + " orchestration task timed out after "
                                        + properties.taskTimeout().toSeconds() + " seconds"
                        );
                        ingestionJobService.markStepFailed(job.jobId(), content.contentType(), timeout);
                        workspaceFileService.markFailed(job.workspaceId(), content.file().id());
                        log.warn("Timed out while running {} orchestration task", content.contentType().apiName(), timeout);
                    } else {
                        Throwable cause = rootCause(exception);
                        RuntimeException failure = new RuntimeException(
                                content.contentType().apiName() + " orchestration task failed unexpectedly",
                                cause
                        );
                        ingestionJobService.markStepFailed(job.jobId(), content.contentType(), failure);
                        workspaceFileService.markFailed(job.workspaceId(), content.file().id());
                        log.warn("Unhandled failure while running {} orchestration task",
                                content.contentType().apiName(), cause);
                    }

                    return null;
                });
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }

        return current == null ? exception : current;
    }

    private void runTask(IngestionJobDetails job, SubmittedContent content) {
        try {
            ingestionJobService.markStepRunning(job.jobId(), content.contentType());
            workspaceFileService.markProcessing(job.workspaceId(), content.file().id());
            process(job, content);
            workspaceFileService.markProcessed(job.workspaceId(), content.file().id());
            ingestionJobService.markStepCompleted(job.jobId(), content.contentType());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workspaceFileService.markFailed(job.workspaceId(), content.file().id());
            ingestionJobService.markStepFailed(job.jobId(), content.contentType(), exception);
            log.warn("Interrupted while running {} orchestration task", content.contentType().apiName(), exception);
        } catch (Exception exception) {
            workspaceFileService.markFailed(job.workspaceId(), content.file().id());
            ingestionJobService.markStepFailed(job.jobId(), content.contentType(), exception);
            log.warn("Failed to run {} orchestration task", content.contentType().apiName(), exception);
        }
    }

    private void process(IngestionJobDetails job, SubmittedContent content) throws Exception {
        switch (content.contentType()) {
            case DOCUMENTS -> processDocument(job, content);
            case AUDIO -> processAudio(job, content);
            case IMAGES -> processImage(job, content);
            case VIDEOS -> processVideo(job, content);
        }
    }

    private void processDocument(IngestionJobDetails job, SubmittedContent content) throws Exception {
        byte[] bytes = readContent(job.workspaceId(), content.file().id());
        ParsedTextDocument parsedDocument = documentService.extractDocumentText(
                content.original().filename(),
                content.original().contentType(),
                bytes
        );
        knowledgeService.recordDocumentsInfo(
                job.workspaceId(),
                content.original().filename(),
                job.jobId(),
                parsedDocument.content()
        );
    }

    private void processAudio(IngestionJobDetails job, SubmittedContent content) throws Exception {
        byte[] bytes = readContent(job.workspaceId(), content.file().id());
        AudioTranscription transcription = audioService.transcribe(content.original().filename(), bytes);
        knowledgeService.recordAudioInfo(job.workspaceId(), content.original().filename(), job.jobId(), transcription.text());
    }

    private void processImage(IngestionJobDetails job, SubmittedContent content) throws Exception {
        byte[] bytes = readContent(job.workspaceId(), content.file().id());
        ImageDescription description = imageService.describe(
                content.original().filename(),
                content.original().contentType(),
                bytes
        );
        knowledgeService.recordImagesInfo(
                job.workspaceId(),
                content.original().filename(),
                job.jobId(),
                description.description()
        );
    }

    private void processVideo(IngestionJobDetails job, SubmittedContent content) throws Exception {
        byte[] bytes = readContent(job.workspaceId(), content.file().id());
        VideoDescription description = videoService.describe(
                content.original().filename(),
                content.original().contentType(),
                bytes
        );
        knowledgeService.recordVideoInfo(
                job.workspaceId(),
                content.original().filename(),
                job.jobId(),
                description.description()
        );
    }

    private byte[] readContent(String workspaceId, String fileId) throws IOException {
        try (InputStream input = workspaceFileService.readContent(workspaceId, fileId)) {
            return input.readAllBytes();
        }
    }

    private record SubmittedContent(
            IngestionContentType contentType,
            WorkspaceFile file,
            OrchestrationContent original
    ) {
    }
}
