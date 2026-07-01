package com.aiworkspace.orchestrator.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.services.KnowledgeService;
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

    private final DocumentService documentService;
    private final AudioService audioService;
    private final ImageService imageService;
    private final VideoService videoService;
    private final KnowledgeService knowledgeService;
    private final Executor executor;

    public OrchestratorService(
            DocumentService documentService,
            AudioService audioService,
            ImageService imageService,
            VideoService videoService,
            KnowledgeService knowledgeService,
            @Qualifier("orchestratorTaskExecutor") Executor executor
    ) {
        this.documentService = documentService;
        this.audioService = audioService;
        this.imageService = imageService;
        this.videoService = videoService;
        this.knowledgeService = knowledgeService;
        this.executor = executor;
    }

    public OrchestrationSubmission process(
            OrchestrationContent document,
            OrchestrationContent audio,
            OrchestrationContent image,
            OrchestrationContent video
    ) {
        List<String> submitted = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        submitIfPresent("documents", document, submitted, skipped, () -> processDocument(document));
        submitIfPresent("audio", audio, submitted, skipped, () -> processAudio(audio));
        submitIfPresent("images", image, submitted, skipped, () -> processImage(image));
        submitIfPresent("videos", video, submitted, skipped, () -> processVideo(video));

        return new OrchestrationSubmission(List.copyOf(submitted), List.copyOf(skipped));
    }

    private void submitIfPresent(
            String module,
            OrchestrationContent content,
            List<String> submitted,
            List<String> skipped,
            OrchestrationTask task
    ) {
        if (content == null || content.isEmpty()) {
            skipped.add(module);
            return;
        }

        submitted.add(module);
        CompletableFuture.runAsync(() -> runTask(module, task), executor);
    }

    private void runTask(String module, OrchestrationTask task) {
        try {
            task.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while running {} orchestration task", module, exception);
        } catch (Exception exception) {
            log.warn("Failed to run {} orchestration task", module, exception);
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
