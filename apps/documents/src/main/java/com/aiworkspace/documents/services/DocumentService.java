package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.GenericRestClient;
import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.FetchedWebPage;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.models.TextDocumentUploadResponse;
import com.aiworkspace.documents.models.WebPageExtractRequest;
import com.aiworkspace.documents.models.WebPageExtractResponse;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

@Service
public class DocumentService {

    private static final int MAX_LOGGED_CHARACTERS = 20_000;
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final GenericRestClient restClient;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final DocumentValidator documentValidator;
    private final AutoDetectParser parser = new AutoDetectParser();

    public DocumentService(GenericRestClient restClient) {
        this(restClient, null, null, null, new DocumentValidator());
    }

    @Autowired
    public DocumentService(
            GenericRestClient restClient,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            DocumentValidator documentValidator
    ) {
        this.restClient = restClient;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.documentValidator = documentValidator;
    }

    public ParsedTextDocument parseTextDocument(String filename, byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);

        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        return new ParsedTextDocument(filename, content);
    }

    public ParsedTextDocument extractDocumentText(String filename, String contentType, byte[] bytes) throws IOException {
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, MediaType.parse(contentType).toString());
        }

        BodyContentHandler handler = new BodyContentHandler(-1);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            parser.parse(input, handler, metadata, new ParseContext());
        } catch (TikaException | SAXException exception) {
            throw new IOException("Failed to extract document text", exception);
        }

        String content = removeBom(handler.toString());
        return new ParsedTextDocument(filename, content);
    }

    public ExtractedWebPage extractWebPage(String rawUrl) throws IOException {
        FetchedWebPage fetchedWebPage = restClient.get(rawUrl, FetchedWebPage::new);

        Document document = Jsoup.parse(fetchedWebPage.html(), fetchedWebPage.url());
        String content = document.body() == null ? document.text() : document.body().text();

        return new ExtractedWebPage(fetchedWebPage.url(), document.title(), content);
    }

    public TextDocumentUploadResponse uploadTextDocument(
            String ownerId,
            String workspaceId,
            String filename,
            String contentType,
            byte[] content
    ) throws IOException {
        documentValidator.validateUploadContent(content);

        Workspace workspace = workspaceService.getWorkspace(ownerId, workspaceId);
        WorkspaceFile workspaceFile = workspaceFileService.createFile(CreateWorkspaceFileRequest.builder()
                .workspaceId(workspace.id())
                .sourceType(WorkspaceFileSourceType.DOCUMENT)
                .originalFilename(filename)
                .contentType(contentType)
                .content(new ByteArrayInputStream(content))
                .build());
        workspaceFileService.markProcessing(workspace.id(), workspaceFile.id());

        ParsedTextDocument document;
        try {
            document = extractDocumentText(filename, contentType, content);
            knowledgeService.recordDocumentsInfo(workspace.id(), document.filename(), null, document.content());
            workspaceFileService.markProcessed(workspace.id(), workspaceFile.id());
        } catch (IOException | RuntimeException exception) {
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        }

        log.info("Extracted document text from '{}':\n{}", document.filename(), document.content());
        return new TextDocumentUploadResponse(document.filename(), content.length, document.content().length());
    }

    public WebPageExtractResponse extractWebPage(String ownerId, WebPageExtractRequest request) throws IOException {
        documentValidator.validateWebPageExtractRequest(request);

        workspaceService.getWorkspace(ownerId, request.workspaceId());
        ExtractedWebPage page = extractWebPage(request.url());
        String loggedContent = contentForLog(page.content());
        boolean truncated = loggedContent.length() < page.content().length();

        log.info("Extracted web page '{}' from '{}':\n{}", page.title(), page.url(), loggedContent);
        knowledgeService.recordDocumentsInfo(request.workspaceId(), page.title(), null, page.content());

        return new WebPageExtractResponse(
                page.url(),
                page.title(),
                page.content().length(),
                loggedContent.length(),
                truncated
        );
    }

    private String removeBom(String content) {
        if (content.startsWith("\uFEFF")) {
            return content.substring(1);
        }

        return content;
    }

    private String contentForLog(String content) {
        if (content.length() <= MAX_LOGGED_CHARACTERS) {
            return content;
        }

        return content.substring(0, MAX_LOGGED_CHARACTERS);
    }
}
