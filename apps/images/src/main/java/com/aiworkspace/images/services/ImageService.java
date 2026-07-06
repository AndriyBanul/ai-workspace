package com.aiworkspace.images.services;

import com.aiworkspace.images.client.FluxImageClient;
import com.aiworkspace.images.client.GeminiImageClient;
import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.models.ImageDescriptionResponse;
import com.aiworkspace.images.models.ImageGenerationRequest;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_GENERATION_DESCRIPTION_LENGTH = 4_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private final GeminiImageClient geminiImageClient;
    private final FluxImageClient fluxImageClient;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final String descriptionPrompt;

    public ImageService(
            GeminiImageClient geminiImageClient,
            FluxImageClient fluxImageClient,
            @Value("${ai-workspace.gemini.image-description-prompt:Describe this image clearly and concisely.}") String descriptionPrompt
    ) {
        this(geminiImageClient, fluxImageClient, null, null, null, descriptionPrompt);
    }

    @Autowired
    public ImageService(
            GeminiImageClient geminiImageClient,
            FluxImageClient fluxImageClient,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            @Value("${ai-workspace.gemini.image-description-prompt:Describe this image clearly and concisely.}") String descriptionPrompt
    ) {
        this.geminiImageClient = geminiImageClient;
        this.fluxImageClient = fluxImageClient;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.descriptionPrompt = descriptionPrompt;
    }

    public ImageDescription describe(String filename, String contentType, byte[] imageContent)
            throws IOException, InterruptedException {
        validateImage(filename, imageContent);

        String mimeType = mimeType(filename, contentType);
        String description = geminiImageClient.describe(imageContent, mimeType, descriptionPrompt);

        return new ImageDescription(filename, mimeType, description);
    }

    public GeneratedImage generate(String description) throws IOException, InterruptedException {
        validateGenerationDescription(description);

        return fluxImageClient.generate(description.trim());
    }

    public ImageDescriptionResponse describeWorkspaceImage(
            String ownerId,
            String workspaceId,
            String filename,
            String contentType,
            byte[] content
    ) throws IOException, InterruptedException {
        Workspace workspace = workspaceService.getWorkspace(ownerId, workspaceId);
        WorkspaceFile workspaceFile = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspace.id())
                .sourceType(WorkspaceFileSourceType.IMAGE)
                .originalFilename(filename)
                .contentType(contentType)
                .content(new ByteArrayInputStream(content))
                .build());
        workspaceFileService.markProcessing(workspace.id(), workspaceFile.id());

        ImageDescription description;
        try {
            description = describe(filename, contentType, content);
            knowledgeService.recordImagesInfo(workspace.id(), description.filename(), null, description.description());
            workspaceFileService.markProcessed(workspace.id(), workspaceFile.id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        } catch (IOException | RuntimeException exception) {
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        }

        return new ImageDescriptionResponse(
                description.filename(),
                content.length,
                description.mimeType(),
                description.description()
        );
    }

    public GeneratedImage generate(ImageGenerationRequest request) throws IOException, InterruptedException {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be empty");
        }

        return generate(request.description());
    }

    private void validateImage(String filename, byte[] imageContent) {
        if (imageContent == null || imageContent.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (imageContent.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File must not be larger than 10MB");
        }

        String extension = extension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only JPG, PNG, and WEBP images are supported");
        }
    }

    private void validateGenerationDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }

        if (description.length() > MAX_GENERATION_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must not be longer than 4000 characters");
        }
    }

    private String mimeType(String filename, String contentType) {
        String mimeType = MIME_TYPES_BY_EXTENSION.get(extension(filename));
        if (contentType != null && contentType.equals(mimeType)) {
            return contentType;
        }

        return mimeType;
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
