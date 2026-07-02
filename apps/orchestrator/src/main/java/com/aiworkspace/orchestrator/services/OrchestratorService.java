package com.aiworkspace.orchestrator.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJobDetails;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.models.OrchestrationSubmission;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.services.VideoService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
    private static final String HARDCODED_WORKSPACE_ID = "default-workspace";

    private final DocumentService documentService;
    private final AudioService audioService;
    private final ImageService imageService;
    private final VideoService videoService;
    private final KnowledgeService knowledgeService;
    private final IngestionJobService ingestionJobService;
    private final Executor executor;

    public OrchestratorService(
            DocumentService documentService,
            AudioService audioService,
            ImageService imageService,
            VideoService videoService,
            KnowledgeService knowledgeService,
            IngestionJobService ingestionJobService,
            @Qualifier("orchestratorTaskExecutor") Executor executor
    ) {
        this.documentService = documentService;
        this.audioService = audioService;
        this.imageService = imageService;
        this.videoService = videoService;
        this.knowledgeService = knowledgeService;
        this.ingestionJobService = ingestionJobService;
        this.executor = executor;
    }

    public OrchestrationSubmission process(
            OrchestrationContent document,
            OrchestrationContent audio,
            OrchestrationContent image,
            OrchestrationContent video
    ) {
        List<IngestionContentType> submittedTypes = new ArrayList<>();
        List<IngestionContentType> skippedTypes = new ArrayList<>();

        collectContentType(IngestionContentType.DOCUMENTS, document, submittedTypes, skippedTypes);
        collectContentType(IngestionContentType.AUDIO, audio, submittedTypes, skippedTypes);
        collectContentType(IngestionContentType.IMAGES, image, submittedTypes, skippedTypes);
        collectContentType(IngestionContentType.VIDEOS, video, submittedTypes, skippedTypes);

        IngestionJobDetails job = ingestionJobService.createJob(HARDCODED_WORKSPACE_ID, submittedTypes, skippedTypes);

        submitIfPresent(job.jobId(), IngestionContentType.DOCUMENTS, document, () -> processDocument(document));
        submitIfPresent(job.jobId(), IngestionContentType.AUDIO, audio, () -> processAudio(audio));
        submitIfPresent(job.jobId(), IngestionContentType.IMAGES, image, () -> processImage(image));
        submitIfPresent(job.jobId(), IngestionContentType.VIDEOS, video, () -> processVideo(video));

        return new OrchestrationSubmission(
                job.jobId(),
                job.workspaceId(),
                job.status(),
                submittedTypes.stream().map(IngestionContentType::apiName).toList(),
                skippedTypes.stream().map(IngestionContentType::apiName).toList()
        );
    }

    public IngestionJobDetails findJob(String jobId) {
        return ingestionJobService.getJob(jobId);
    }

    private void collectContentType(
            IngestionContentType contentType,
            OrchestrationContent content,
            List<IngestionContentType> submitted,
            List<IngestionContentType> skipped
    ) {
        if (content == null || content.isEmpty()) {
            skipped.add(contentType);
            return;
        }

        submitted.add(contentType);
    }

    private void submitIfPresent(
            String jobId,
            IngestionContentType contentType,
            OrchestrationContent content,
            OrchestrationTask task
    ) {
        if (content == null || content.isEmpty()) {
            return;
        }

        CompletableFuture.runAsync(() -> runTask(jobId, contentType, task), executor);
    }

    private void runTask(String jobId, IngestionContentType contentType, OrchestrationTask task) {
        try {
            ingestionJobService.markStepRunning(jobId, contentType);
            task.run();
            ingestionJobService.markStepCompleted(jobId, contentType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ingestionJobService.markStepFailed(jobId, contentType, exception);
            log.warn("Interrupted while running {} orchestration task", contentType.apiName(), exception);
        } catch (Exception exception) {
            ingestionJobService.markStepFailed(jobId, contentType, exception);
            log.warn("Failed to run {} orchestration task", contentType.apiName(), exception);
        }
    }

    private void processDocument(OrchestrationContent document) throws Exception {
        ParsedTextDocument parsedDocument = documentService.parseTextDocument(document.filename(), document.content());
        knowledgeService.recordDocumentsInfo(parsedDocument.content());
    }

    private void processAudio(OrchestrationContent audio) throws Exception {
        AudioTranscription transcription = audioService.transcribe(audio.filename(), audio.content());
        knowledgeService.recordAudioInfo(transcription.text());
    }

    private void processImage(OrchestrationContent image) throws Exception {
        ImageDescription description = imageService.describe(image.filename(), image.contentType(), image.content());
        knowledgeService.recordImagesInfo(description.description());
    }

    private void processVideo(OrchestrationContent video) throws Exception {
        VideoDescription description = videoService.describe(video.filename(), video.contentType(), video.content());
        knowledgeService.recordVideoInfo(description.description());
    }

    @FunctionalInterface
    private interface OrchestrationTask {

        void run() throws Exception;
    }
}
