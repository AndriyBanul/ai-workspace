package com.aiworkspace.orchestrator.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.models.IngestionContentType;
import com.aiworkspace.orchestrator.models.IngestionJob;
import com.aiworkspace.orchestrator.models.IngestionJobStatus;
import com.aiworkspace.orchestrator.models.IngestionJobStep;
import com.aiworkspace.orchestrator.models.IngestionStepStatus;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.orchestrator.repositories.IngestionJobRepository;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.services.VideoService;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.IOException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrchestratorServiceTest {

    @Test
    void processesProvidedContentAndSkipsMissingContent() {
        CapturingKnowledgeRepository knowledgeRepository = new CapturingKnowledgeRepository();
        CapturingIngestionJobRepository jobRepository = new CapturingIngestionJobRepository();
        OrchestratorService service = new OrchestratorService(
                new TestDocumentService(),
                new TestAudioService(),
                new TestImageService(),
                new TestVideoService(),
                new KnowledgeService(knowledgeRepository, (question, context) -> "Answer"),
                new IngestionJobService(jobRepository),
                new TestWorkspaceService(),
                Runnable::run
        );

        var submission = service.process(
                "workspace-1",
                new OrchestrationContent("document.txt", "text/plain", "Document input".getBytes()),
                null,
                new OrchestrationContent("image.png", "image/png", new byte[] {1, 2, 3}),
                new OrchestrationContent("video.mp4", "video/mp4", new byte[0])
        );

        assertNotNull(submission.jobId());
        assertEquals("workspace-1", submission.workspaceId());
        assertEquals(IngestionJobStatus.RUNNING, submission.status());
        assertEquals(List.of("documents", "images"), submission.submitted());
        assertEquals(List.of("audio", "videos"), submission.skipped());
        assertEquals("Parsed document text", knowledgeRepository.items.get(0).content());
        assertEquals("Image description", knowledgeRepository.items.get(1).content());

        var job = service.findJob(submission.jobId());
        assertEquals(IngestionJobStatus.COMPLETED, job.status());
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.DOCUMENTS, IngestionStepStatus.COMPLETED);
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.AUDIO, IngestionStepStatus.SKIPPED);
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.IMAGES, IngestionStepStatus.COMPLETED);
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.VIDEOS, IngestionStepStatus.SKIPPED);
    }

    @Test
    void marksFailedStepAndPartiallyFailedJobWhenTaskFails() {
        CapturingKnowledgeRepository knowledgeRepository = new CapturingKnowledgeRepository();
        CapturingIngestionJobRepository jobRepository = new CapturingIngestionJobRepository();
        OrchestratorService service = new OrchestratorService(
                new TestDocumentService(),
                new FailingAudioService(),
                new TestImageService(),
                new TestVideoService(),
                new KnowledgeService(knowledgeRepository, (question, context) -> "Answer"),
                new IngestionJobService(jobRepository),
                new TestWorkspaceService(),
                Runnable::run
        );

        var submission = service.process(
                "workspace-1",
                new OrchestrationContent("document.txt", "text/plain", "Document input".getBytes()),
                new OrchestrationContent("audio.mp3", "audio/mpeg", new byte[] {1}),
                null,
                null
        );

        var job = service.findJob(submission.jobId());
        assertEquals(IngestionJobStatus.PARTIALLY_FAILED, job.status());
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.DOCUMENTS, IngestionStepStatus.COMPLETED);
        assertStepStatus(jobRepository, submission.jobId(), IngestionContentType.AUDIO, IngestionStepStatus.FAILED);
    }

    private static class TestDocumentService extends DocumentService {

        TestDocumentService() {
            super(null);
        }

        @Override
        public ParsedTextDocument parseTextDocument(String filename, byte[] bytes) {
            return new ParsedTextDocument(filename, "Parsed document text");
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
            super(null);
        }

        @Override
        public Workspace getWorkspace(String workspaceId) {
            return new Workspace(workspaceId, "Test workspace", Instant.now(), Instant.now());
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
        public void updateStepStatus(
                String jobId,
                IngestionContentType contentType,
                IngestionStepStatus status,
                Instant startedAt,
                Instant completedAt,
                String errorMessage
        ) {
            IngestionJobStep existingStep = steps.get(jobId).get(contentType);
            steps.get(jobId).put(contentType, new IngestionJobStep(
                    existingStep.id(),
                    existingStep.jobId(),
                    existingStep.contentType(),
                    status,
                    startedAt == null ? existingStep.startedAt() : startedAt,
                    completedAt,
                    errorMessage
            ));
        }
    }
}
