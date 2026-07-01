package com.aiworkspace.orchestrator.services;

import com.aiworkspace.audio.models.AudioTranscription;
import com.aiworkspace.audio.services.AudioService;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.services.DocumentService;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.services.ImageService;
import com.aiworkspace.knowledge.models.WorkspaceKnowledge;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeField;
import com.aiworkspace.knowledge.repositories.KnowledgeRepository;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.orchestrator.models.OrchestrationContent;
import com.aiworkspace.videos.models.VideoDescription;
import com.aiworkspace.videos.services.VideoService;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrchestratorServiceTest {

    @Test
    void processesProvidedContentAndSkipsMissingContent() {
        CapturingKnowledgeRepository knowledgeRepository = new CapturingKnowledgeRepository();
        OrchestratorService service = new OrchestratorService(
                new TestDocumentService(),
                new TestAudioService(),
                new TestImageService(),
                new TestVideoService(),
                new KnowledgeService(knowledgeRepository, (question, context) -> "Answer"),
                Runnable::run
        );

        var submission = service.process(
                new OrchestrationContent("document.txt", "text/plain", "Document input".getBytes()),
                null,
                new OrchestrationContent("image.png", "image/png", new byte[] {1, 2, 3}),
                new OrchestrationContent("video.mp4", "video/mp4", new byte[0])
        );

        assertEquals(java.util.List.of("documents", "images"), submission.submitted());
        assertEquals(java.util.List.of("audio", "videos"), submission.skipped());
        assertEquals("Parsed document text", knowledgeRepository.values.get(WorkspaceKnowledgeField.DOCUMENTS_INFO));
        assertEquals("Image description", knowledgeRepository.values.get(WorkspaceKnowledgeField.IMAGES_INFO));
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

    private static class CapturingKnowledgeRepository implements KnowledgeRepository {

        private final EnumMap<WorkspaceKnowledgeField, String> values = new EnumMap<>(WorkspaceKnowledgeField.class);

        @Override
        public Optional<WorkspaceKnowledge> findByWorkspaceId(String workspaceId) {
            return Optional.empty();
        }

        @Override
        public void updateWorkspaceKnowledgeField(String workspaceId, WorkspaceKnowledgeField field, String value)
                throws IOException {
            values.put(field, value);
        }
    }
}
