package com.aiworkspace.images.services;

import com.aiworkspace.images.models.GeneratedImage;
import com.aiworkspace.images.models.ImageDescription;
import com.aiworkspace.images.models.ImageDescriptionResponse;
import com.aiworkspace.images.models.ImageGenerationRequest;
import com.aiworkspace.images.providers.ImageGenerationProvider;
import com.aiworkspace.images.providers.ImageUnderstandingProvider;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageService {

    private final ImageUnderstandingProvider imageUnderstandingProvider;
    private final ImageGenerationProvider imageGenerationProvider;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final String descriptionPrompt;
    private final ImageValidator imageValidator;

    public ImageService(
            ImageUnderstandingProvider imageUnderstandingProvider,
            ImageGenerationProvider imageGenerationProvider,
            @Value("${ai-workspace.gemini.image-description-prompt:Describe this image clearly and concisely.}") String descriptionPrompt
    ) {
        this(imageUnderstandingProvider, imageGenerationProvider, null, null, null, descriptionPrompt, new ImageValidator());
    }

    @Autowired
    public ImageService(
            ImageUnderstandingProvider imageUnderstandingProvider,
            ImageGenerationProvider imageGenerationProvider,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            @Value("${ai-workspace.gemini.image-description-prompt:Describe this image clearly and concisely.}") String descriptionPrompt,
            ImageValidator imageValidator
    ) {
        this.imageUnderstandingProvider = imageUnderstandingProvider;
        this.imageGenerationProvider = imageGenerationProvider;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.descriptionPrompt = descriptionPrompt;
        this.imageValidator = imageValidator;
    }

    public ImageDescription describe(String filename, String contentType, byte[] imageContent)
            throws IOException, InterruptedException {
        imageValidator.validateImage(filename, imageContent);

        String mimeType = imageValidator.mimeType(filename, contentType);
        String description = imageUnderstandingProvider.describe(imageContent, mimeType, descriptionPrompt);

        return new ImageDescription(filename, mimeType, description);
    }

    public GeneratedImage generate(String description) throws IOException, InterruptedException {
        imageValidator.validateGenerationDescription(description);

        return imageGenerationProvider.generate(description.trim());
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
        imageValidator.validateGenerationRequest(request);

        return generate(request.description());
    }
}
