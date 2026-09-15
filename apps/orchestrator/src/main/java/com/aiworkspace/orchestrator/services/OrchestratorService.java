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
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.videos.models.VideoDescriptionResponse;
import com.aiworkspace.videos.models.YouTubeVideoIngestionRequest;
import com.aiworkspace.videos.models.YouTubeVideoIngestionResponse;
import com.aiworkspace.videos.services.VideoService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.CreateWorkspaceUrlSourceRequest;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
            @Qualifier("orchestratorTaskExecutor") Executor executor,
            OrchestratorValidator orchestratorValidator,
            SourceLifecycleCoordinator lifecycleCoordinator,
            WorkspaceSourceProcessingService sourceProcessingService
    ) {
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
        IngestionJobDetails job = ingestionJobService.createJob(trimmedWorkspaceId, submittedTypes, skippedTypes);

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

    public TextDocumentUploadResponse ingestDocument(String ownerId, String workspaceId, String filename,
            String contentType, byte[] content) throws IOException {
        SubmittedContent submitted = createUploadedSource(
                ownerId, workspaceId, IngestionContentType.DOCUMENTS, WorkspaceFileSourceType.DOCUMENT,
                new OrchestrationContent(filename, contentType, content));
        try {
            return (TextDocumentUploadResponse) processSynchronously(submitted);
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
        return (AudioTranscriptionResponse) processSynchronously(submitted);
    }

    public ImageDescriptionResponse ingestImage(String ownerId, String workspaceId, String filename,
            String contentType, byte[] content) throws IOException, InterruptedException {
        SubmittedContent submitted = createUploadedSource(
                ownerId, workspaceId, IngestionContentType.IMAGES, WorkspaceFileSourceType.IMAGE,
                new OrchestrationContent(filename, contentType, content));
        return (ImageDescriptionResponse) processSynchronously(submitted);
    }

    public VideoDescriptionResponse ingestVideo(String ownerId, String workspaceId, String filename,
            String contentType, byte[] content) throws IOException, InterruptedException {
        SubmittedContent submitted = createUploadedSource(
                ownerId, workspaceId, IngestionContentType.VIDEOS, WorkspaceFileSourceType.VIDEO,
                new OrchestrationContent(filename, contentType, content));
        return (VideoDescriptionResponse) processSynchronously(submitted);
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
            return (WebPageExtractResponse) processSynchronously(submitted);
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
        return (YouTubeVideoIngestionResponse) processSynchronously(
                new SubmittedContent(IngestionContentType.VIDEOS, source, null));
    }

    public OrchestrationSubmission reprocessSource(String ownerId, String workspaceId, String sourceId)
            throws IOException {
        WorkspaceFile source = workspaceService.getFile(ownerId, workspaceId, sourceId);
        IngestionContentType contentType = contentType(source.sourceType());
        OrchestrationContent original = source.urlBacked() ? null : readOriginal(source);
        SubmittedContent submitted = new SubmittedContent(contentType, source, original);
        IngestionJobDetails job = singleSourceJob(source.workspaceId(), contentType);
        submit(job, submitted);
        return new OrchestrationSubmission(
                job.jobId(), job.workspaceId(), job.status(), List.of(contentType.apiName()), List.of(),
                Map.of(contentType.apiName(), source.id()));
    }

    private Object processSynchronously(SubmittedContent submitted) throws IOException, InterruptedException {
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

    private OrchestrationContent contentFrom(MultipartFile file) throws IOException {
        if (file == null) {
            return null;
        }

        String filename = file.getOriginalFilename();
        if (file.isEmpty() && (filename == null || filename.isBlank())) {
            return null;
        }

        return new OrchestrationContent(filename, file.getContentType(), file.getBytes());
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
        CompletableFuture.runAsync(() -> runTask(job, content), executor);
    }

    private void runTask(IngestionJobDetails job, SubmittedContent content) {
        try {
            lifecycleCoordinator.process(
                    job, content.contentType(), content.file(), () -> processSource(job, content));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while running {} orchestration task", content.contentType().apiName(), exception);
        } catch (Exception exception) {
            log.warn("Failed to run {} orchestration task", content.contentType().apiName(), exception);
        }
    }

    private Object processSource(IngestionJobDetails job, SubmittedContent content) throws Exception {
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
