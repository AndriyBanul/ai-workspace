package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.GenericRestClient;
import com.aiworkspace.documents.config.DocumentExtractionProperties;
import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.ParsedTextDocument;
import com.aiworkspace.documents.models.TextDocumentUploadResponse;
import com.aiworkspace.documents.models.WebPageExtractRequest;
import com.aiworkspace.documents.models.WebPageExtractResponse;
import com.aiworkspace.knowledge.models.KnowledgeSourceMetadata;
import com.aiworkspace.knowledge.services.KnowledgeService;
import com.aiworkspace.workspaces.models.CreateWorkspaceFileRequest;
import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.models.WorkspaceFileSourceType;
import com.aiworkspace.workspaces.services.WorkspaceFileService;
import com.aiworkspace.workspaces.services.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

@Service
public class DocumentService {

    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String TIKA_PARSER_VERSION =
            parserVersion("ai-workspace-document-structure-v1/tika", AutoDetectParser.class);
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final GenericRestClient restClient;
    private final WorkspaceFileService workspaceFileService;
    private final KnowledgeService knowledgeService;
    private final WorkspaceService workspaceService;
    private final DocumentValidator documentValidator;
    private final DocumentExtractionProperties extractionProperties;
    private final DocxStructureExtractor docxStructureExtractor;
    private final WebPageContentExtractor webPageContentExtractor;
    private final AutoDetectParser parser = new AutoDetectParser();

    public DocumentService(GenericRestClient restClient) {
        this(restClient, new DocumentExtractionProperties(null, null, null, null));
    }

    DocumentService(GenericRestClient restClient, DocumentExtractionProperties extractionProperties) {
        this(
                restClient,
                null,
                null,
                null,
                new DocumentValidator(),
                extractionProperties,
                new DocxStructureExtractor(),
                new WebPageContentExtractor()
        );
    }

    public DocumentService(
            GenericRestClient restClient,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            DocumentValidator documentValidator,
            DocumentExtractionProperties extractionProperties,
            DocxStructureExtractor docxStructureExtractor
    ) {
        this(
                restClient,
                workspaceFileService,
                knowledgeService,
                workspaceService,
                documentValidator,
                extractionProperties,
                docxStructureExtractor,
                new WebPageContentExtractor()
        );
    }

    @Autowired
    public DocumentService(
            GenericRestClient restClient,
            WorkspaceFileService workspaceFileService,
            KnowledgeService knowledgeService,
            WorkspaceService workspaceService,
            DocumentValidator documentValidator,
            DocumentExtractionProperties extractionProperties,
            DocxStructureExtractor docxStructureExtractor,
            WebPageContentExtractor webPageContentExtractor
    ) {
        this.restClient = restClient;
        this.workspaceFileService = workspaceFileService;
        this.knowledgeService = knowledgeService;
        this.workspaceService = workspaceService;
        this.documentValidator = documentValidator;
        this.extractionProperties = extractionProperties;
        this.docxStructureExtractor = docxStructureExtractor;
        this.webPageContentExtractor = webPageContentExtractor;
    }

    public ParsedTextDocument parseTextDocument(String filename, byte[] bytes) throws IOException {
        return extractDocumentText(filename, null, bytes);
    }

    public ParsedTextDocument extractDocumentText(String filename, String contentType, byte[] bytes) throws IOException {
        documentValidator.validateUploadContent(bytes);

        // Detect from bytes only: filenames and client MIME types must not select a parser.
        Metadata metadata = new Metadata();
        DocumentStructureContentHandler structureHandler = new DocumentStructureContentHandler(
                extractionProperties.maxExtractedBlocks()
        );
        BodyContentHandler handler = new BodyContentHandler(new WriteOutContentHandler(
                structureHandler,
                extractionProperties.maxExtractedCharacters()
        ));
        String detectedContentType;
        try (TikaInputStream input = TikaInputStream.get(bytes)) {
            MediaType detectedType = parser.getDetector().detect(input, metadata).getBaseType();
            if ("application/x-tika-ooxml-protected".equals(detectedType.toString())) {
                throw new DocumentProcessingException(DocumentFailureCode.PASSWORD_PROTECTED_DOCUMENT);
            }
            documentValidator.validateDetectedContentType(detectedType.toString());
            detectedContentType = detectedType.toString();
            if (DOCX_MEDIA_TYPE.equals(detectedContentType)) {
                ExtractedDocumentStructure structure = docxStructureExtractor.extract(bytes, extractionProperties);
                documentValidator.validateExtractedText(structure.content());
                return new ParsedTextDocument(
                        filename,
                        detectedContentType,
                        structure.title(),
                        structure.content(),
                        structure.blocks(),
                        Instant.now(),
                        docxStructureExtractor.parserVersion()
                );
            }
            structureHandler.detectedContentType(detectedContentType);
            metadata.set(Metadata.CONTENT_TYPE, detectedContentType);
            parser.parse(input, handler, metadata, textExtractionContext());
        } catch (TikaException | SAXException | IOException exception) {
            throw documentFailure(exception);
        }

        String content = structureHandler.structuredText(extractionProperties.maxExtractedCharacters());
        documentValidator.validateExtractedText(content);
        return new ParsedTextDocument(
                filename,
                detectedContentType,
                normalizedOptionalValue(metadata.get(TikaCoreProperties.TITLE)),
                content,
                structureHandler.blocks(),
                Instant.now(),
                TIKA_PARSER_VERSION
        );
    }

    private ParseContext textExtractionContext() {
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        pdfConfig.setCatchIntermediateIOExceptions(false);
        pdfConfig.setMaxMainMemoryBytes(extractionProperties.maxPdfMainMemoryBytes());
        TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
        ocrConfig.setSkipOcr(true);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, pdfConfig);
        context.set(TesseractOCRConfig.class, ocrConfig);
        if (!extractionProperties.extractEmbeddedDocuments()) {
            context.set(EmbeddedDocumentExtractor.class, SkipEmbeddedDocumentExtractor.INSTANCE);
        }
        return context;
    }

    private DocumentProcessingException documentFailure(Exception exception) {
        if (WriteLimitReachedException.isWriteLimitReached(exception)) {
            return new DocumentProcessingException(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED, exception);
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof DocumentProcessingException documentException) {
                return documentException;
            }
            if (cause instanceof EncryptedDocumentException) {
                return new DocumentProcessingException(DocumentFailureCode.PASSWORD_PROTECTED_DOCUMENT, exception);
            }
        }
        return new DocumentProcessingException(DocumentFailureCode.CORRUPT_DOCUMENT, exception);
    }

    private enum SkipEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        INSTANCE;

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return false;
        }

        @Override
        public void parseEmbedded(InputStream stream, org.xml.sax.ContentHandler handler, Metadata metadata,
                boolean outputHtml) {
            // Embedded attachments are deliberately excluded unless explicitly enabled.
        }
    }

    public ExtractedWebPage extractWebPage(String rawUrl) throws IOException {
        return webPageContentExtractor.extract(restClient.get(rawUrl));
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
            knowledgeService.recordDocumentsInfo(
                    workspace.id(),
                    document.filename(),
                    null,
                    document.content(),
                    new KnowledgeSourceMetadata(
                            workspaceFile.id(),
                            null,
                            document.extractedAt(),
                            document.parserVersion()
                    )
            );
            workspaceFileService.markProcessed(workspace.id(), workspaceFile.id());
        } catch (IOException | RuntimeException exception) {
            workspaceFileService.markFailed(workspace.id(), workspaceFile.id());
            throw exception;
        }

        log.info(
                "Extracted document text for workspaceId={} fileId={} filename='{}' characterCount={}",
                workspace.id(),
                workspaceFile.id(),
                document.filename(),
                document.content().length()
        );
        return new TextDocumentUploadResponse(
                workspaceFile.id(),
                document.filename(),
                document.detectedContentType(),
                document.title(),
                content.length,
                document.content().length(),
                document.blocks().size(),
                document.extractedAt(),
                document.parserVersion()
        );
    }

    public WebPageExtractResponse extractWebPage(String ownerId, WebPageExtractRequest request) throws IOException {
        documentValidator.validateWebPageExtractRequest(request);

        workspaceService.getWorkspace(ownerId, request.workspaceId());
        ExtractedWebPage page = extractWebPage(request.url());
        String sourceId = UUID.randomUUID().toString();
        int storedCharacterCount = page.content().length();

        log.info(
                "Extracted web page text for workspaceId={} host={} characterCount={}",
                request.workspaceId().trim(),
                hostForLog(page.url()),
                storedCharacterCount
        );
        knowledgeService.recordDocumentsInfo(
                request.workspaceId(),
                page.title(),
                null,
                page.content(),
                new KnowledgeSourceMetadata(
                        sourceId,
                        page.url(),
                        page.extractedAt(),
                        page.parserVersion()
                )
        );

        return new WebPageExtractResponse(
                sourceId,
                page.url(),
                page.contentType(),
                page.title(),
                storedCharacterCount,
                storedCharacterCount,
                storedCharacterCount,
                false,
                page.extractedAt(),
                page.parserVersion()
        );
    }

    private String hostForLog(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? "(unknown)" : host;
        } catch (IllegalArgumentException exception) {
            return "(invalid)";
        }
    }

    private String normalizedOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String parserVersion(String parserName, Class<?> libraryType) {
        String libraryVersion = libraryType.getPackage().getImplementationVersion();
        return parserName + "-" + (libraryVersion == null ? "unknown" : libraryVersion);
    }
}
