package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.GenericRestClient;
import com.aiworkspace.documents.config.DocumentExtractionProperties;
import com.aiworkspace.documents.config.WebPageFetchProperties;
import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.documents.models.FetchedWebPage;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentServiceTest {

    @Test
    void sendsOnlyScannedPdfPagesToImagesOcrAndPreservesPageOrder() throws IOException {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var limits = new DocumentExtractionProperties(null, null, null, null, null);
        var fallback = new PdfOcrFallback((image, mime) -> {
            calls.incrementAndGet();
            return new com.aiworkspace.images.models.OcrResult(List.of(
                    new com.aiworkspace.images.models.OcrRegion("Recovered scan text", null, null)));
        }, new com.aiworkspace.documents.config.PdfOcrProperties(true, null, null, null), limits);
        var service = new DocumentService(new TestRestClient(), new DocumentValidator(),
                limits, new DocxStructureExtractor(), new WebPageContentExtractor(), new DocumentChunker(), fallback);
        try (var pdf = Loader.loadPDF(pdfWithText("Original text page")); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            try (var stream = new PDPageContentStream(pdf, page)) {
                stream.drawImage(LosslessFactory.createFromImage(pdf, image), 0, 0, 100, 100);
            }
            pdf.save(output);
            var result = service.extractDocumentText("mixed.pdf", "application/pdf", output.toByteArray());
            assertEquals(1, calls.get());
            assertTrue(result.content().contains("[Page 1]"));
            assertTrue(result.content().contains("[Page 2]"));
            assertTrue(result.content().indexOf("Original text page") < result.content().indexOf("Recovered scan text"));
            assertTrue(result.parserVersion().endsWith("/image-ocr-v1"));
            assertTrue(service.chunkForKnowledge(result).stream().anyMatch(chunk -> Integer.valueOf(2).equals(chunk.pageNumber())
                    && chunk.content().contains("Recovered scan text")));
        }
    }

    @Test
    void extractsLegacyDocFromContentDespiteMisleadingFilename() throws IOException {
        var service = new DocumentService(new TestRestClient());
        try (var input = getClass().getResourceAsStream("/documents/simple.doc")) {
            var document = service.extractDocumentText("disguised.txt", "text/plain", input.readAllBytes());
            assertEquals("application/msword", document.detectedContentType());
            assertTrue(document.content().contains("This is a simple file"), document.content());
            assertTrue(!service.chunkForKnowledge(document).isEmpty());
        }
    }

    @Test
    void extractsLegacyXlsWithSheetLocationsAndTableRows() throws IOException {
        var service = new DocumentService(new TestRestClient());
        try (var workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            for (String name : List.of("North", "South")) {
                var row = workbook.createSheet(name).createRow(0);
                row.createCell(0).setCellValue(name + " revenue");
                row.createCell(1).setCellValue(42);
            }
            workbook.write(output);
            var document = service.extractDocumentText("metrics.xls", "application/octet-stream", output.toByteArray());
            assertEquals("application/vnd.ms-excel", document.detectedContentType());
            assertEquals(List.of("North", "South"), document.blocks().stream()
                    .map(block -> block.sheetName()).filter(value -> value != null).distinct().toList());
            assertTrue(document.content().contains("North revenue | 42"), document.content());
            assertTrue(service.chunkForKnowledge(document).stream().anyMatch(chunk -> "South".equals(chunk.sheetName())));
        }
    }

    @Test
    void extractsLegacyPptWithSlideLocations() throws IOException {
        var service = new DocumentService(new TestRestClient());
        try (var presentation = new org.apache.poi.hslf.usermodel.HSLFSlideShow();
                var output = new ByteArrayOutputStream()) {
            for (String text : List.of("First legacy slide", "Second legacy slide")) {
                var slide = presentation.createSlide();
                var textBox = new org.apache.poi.hslf.usermodel.HSLFTextBox();
                textBox.setAnchor(new Rectangle(50, 50, 400, 100));
                textBox.setText(text);
                slide.addShape(textBox);
            }
            presentation.write(output);
            var document = service.extractDocumentText("slides.ppt", "application/octet-stream", output.toByteArray());
            assertEquals("application/vnd.ms-powerpoint", document.detectedContentType());
            assertTrue(document.content().contains("First legacy slide"), document.content());
            assertTrue(document.content().contains("Second legacy slide"), document.content());
            assertEquals(List.of(1, 2), document.blocks().stream()
                    .map(block -> block.slideNumber()).filter(value -> value != null).distinct().toList());
            assertTrue(service.chunkForKnowledge(document).stream().anyMatch(chunk -> Integer.valueOf(2).equals(chunk.slideNumber())));
        }
    }

    @Test
    void preservesPlainTextParagraphsAndChaptersBeforeChunking() throws IOException {
        var service = new DocumentService(new TestRestClient());
        var document = service.parseTextDocument("book.txt",
                "I. FIRST STORY\n\nFirst sentence.\n\nSecond paragraph.\n\nII. SECOND STORY\n\nAnother sentence."
                        .getBytes(StandardCharsets.UTF_8));
        var chunks = service.chunkForKnowledge(document);
        assertEquals(2, chunks.size());
        assertEquals("I. FIRST STORY", chunks.getFirst().heading());
        assertTrue(chunks.getFirst().content().contains("First sentence.\n\nSecond paragraph."));
        assertEquals("II. SECOND STORY", chunks.getLast().heading());
        assertTrue(!chunks.getFirst().sectionId().equals(chunks.getLast().sectionId()));
    }

    @Test
    void parsesUtf8TextDocumentAndRemovesBom() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.parseTextDocument("notes.txt", "\uFEFFHello workspace".getBytes());

        assertEquals("notes.txt", document.filename());
        assertEquals("Hello workspace", document.content().trim());
        assertTrue(document.extractedAt() != null);
        assertEquals("ai-workspace-document-structure-v1/tika-3.2.3", document.parserVersion());
    }

    @Test
    void extractsUtf8TextDocumentWithTika() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText("notes.txt", "text/plain", "\uFEFFHello workspace".getBytes());

        assertEquals("notes.txt", document.filename());
        assertEquals("Hello workspace", document.content().trim());
    }

    @Test
    void extractsUtf16TextDocumentWithBom() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());
        byte[] text = "Résumé workspace".getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[text.length + 2];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xfe;
        System.arraycopy(text, 0, bytes, 2, text.length);

        var document = service.extractDocumentText("notes.txt", "text/plain", bytes);

        assertTrue(document.content().contains("Résumé workspace"), document.content());
    }

    @Test
    void extractsPdfTextWithTika() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText("deck.pdf", "application/pdf", pdfWithText("PDF workspace text"));

        assertEquals("deck.pdf", document.filename());
        assertEquals("application/pdf", document.detectedContentType());
        assertTrue(document.content().contains("PDF workspace text"));
        assertTrue(document.content().contains("[Page 1]"));
        assertTrue(document.blocks().stream().anyMatch(block -> Integer.valueOf(1).equals(block.pageNumber())),
                document.blocks().toString());
    }

    @Test
    void extractsDocxTextWithTika() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText(
                "memo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithText("DOCX workspace text")
        );

        assertEquals("memo.docx", document.filename());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                document.detectedContentType());
        assertTrue(document.content().contains("DOCX workspace text"));
        assertTrue(document.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.PARAGRAPH),
                document.blocks().toString());
    }

    @Test
    void preservesDocumentHeadingsAndTitleMetadata() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText(
                "memo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithHeading("Quarterly plan", "Detailed plan text")
        );

        assertEquals("Memo title", document.title());
        assertTrue(document.extractedAt() != null);
        assertTrue(document.parserVersion().startsWith("ai-workspace-docx-structure-v1/poi-"));
        assertTrue(document.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.HEADING
                && "Quarterly plan".equals(block.text())), document.blocks().toString());
        assertTrue(document.content().contains("# Quarterly plan"), document.content());
    }

    @Test
    void preservesDocxTableRowsAndEmptyCells() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText(
                "table.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithTable()
        );

        assertTrue(document.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.TABLE_ROW
                && "Metric | | Value".equals(block.text())), document.blocks().toString());
        assertTrue(document.content().contains("Metric | | Value"), document.content());
    }

    @Test
    void preservesPageSlideAndSheetLocationsInOrder() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var pdf = service.extractDocumentText("pages.pdf", "application/pdf", pdfWithPages("Page one", "Page two"));
        var presentation = service.extractDocumentText(
                "slides.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                pptxWithSlides("Slide one", "Slide two")
        );
        var workbook = service.extractDocumentText(
                "sheets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxWithSheets("North", "South")
        );

        assertEquals(List.of(1, 2), pdf.blocks().stream()
                .map(block -> block.pageNumber()).filter(value -> value != null).distinct().toList());
        assertEquals(List.of(1, 2), presentation.blocks().stream()
                .map(block -> block.slideNumber()).filter(value -> value != null).distinct().toList());
        assertEquals(List.of("North", "South"), workbook.blocks().stream()
                .map(block -> block.sheetName()).filter(value -> value != null).distinct().toList());
    }

    @Test
    void extractsXlsxTextWithTika() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText(
                "metrics.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxWithText("XLSX workspace text")
        );

        assertEquals("metrics.xlsx", document.filename());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                document.detectedContentType());
        assertTrue(document.content().contains("XLSX workspace text"));
        assertTrue(document.content().contains("[Sheet: Data]"), document.content());
        assertTrue(document.blocks().stream().anyMatch(block -> block.type() == DocumentBlockType.TABLE_ROW),
                document.blocks().toString());
        assertTrue(document.blocks().stream().anyMatch(block -> block.text().contains("XLSX workspace text | Value")),
                document.blocks().toString());
        assertTrue(document.blocks().stream().anyMatch(block -> "Data".equals(block.sheetName())),
                document.blocks().toString());
    }

    @Test
    void extractsPptxTextWithTika() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText(
                "deck.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                pptxWithText("PPTX workspace text")
        );

        assertEquals("deck.pptx", document.filename());
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                document.detectedContentType());
        assertTrue(document.content().contains("PPTX workspace text"));
        assertTrue(document.content().contains("[Slide 1]"), document.content());
        assertTrue(document.blocks().stream().anyMatch(block -> Integer.valueOf(1).equals(block.slideNumber())),
                document.blocks().toString());
    }

    @Test
    void detectsActualFormatDespiteMisleadingFilenameAndMimeType() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText("picture.png", "image/png", pdfWithText("Actual PDF text"));

        assertTrue(document.content().contains("Actual PDF text"));
    }

    @Test
    void acceptsMissingOrMalformedClientMimeType() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());
        byte[] bytes = docxWithText("Detected DOCX text");

        assertTrue(service.extractDocumentText("upload", null, bytes).content().contains("Detected DOCX text"));
        assertTrue(service.extractDocumentText("upload", "not a MIME type", bytes).content().contains("Detected DOCX text"));
    }

    @Test
    void rejectsEmptyDocumentsAtExtractionBoundary() {
        assertFailure(DocumentFailureCode.EMPTY_DOCUMENT, "empty.txt", "text/plain", new byte[0]);
        assertFailure(DocumentFailureCode.EMPTY_DOCUMENT, "empty.txt", "text/plain", null);
    }

    @Test
    void rejectsImageDisguisedAsSupportedDocument() {
        byte[] pngSignature = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

        assertFailure(DocumentFailureCode.UNSUPPORTED_DOCUMENT_FORMAT, "notes.txt", "text/plain", pngSignature);
    }

    @Test
    void rejectsHtmlDisguisedAsPlainText() {
        assertFailure(DocumentFailureCode.UNSUPPORTED_DOCUMENT_FORMAT, "notes.txt", "text/plain",
                "<!doctype html><html><body>Page text</body></html>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsGenericZipDisguisedAsDocx() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("notes.txt"));
            zip.write("Text inside an archive".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertFailure(DocumentFailureCode.UNSUPPORTED_DOCUMENT_FORMAT, "memo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", output.toByteArray());
    }

    @Test
    void classifiesCorruptPdf() {
        assertFailure(DocumentFailureCode.CORRUPT_DOCUMENT, "broken.pdf", "application/pdf",
                "%PDF-1.7\nThis is not a valid PDF\n%%EOF".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void classifiesPasswordProtectedPdf() throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfWithText("Private text"));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.protect(new StandardProtectionPolicy("owner-password", "reader-password", new AccessPermission()));
            document.save(output);

            assertFailure(DocumentFailureCode.PASSWORD_PROTECTED_DOCUMENT, "protected.pdf", "application/pdf",
                    output.toByteArray());
        }
    }

    @Test
    void classifiesPasswordProtectedOfficeDocument() throws Exception {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var encryptor = new EncryptionInfo(EncryptionMode.agile).getEncryptor();
            encryptor.confirmPassword("reader-password");
            try (OutputStream encrypted = encryptor.getDataStream(fileSystem)) {
                encrypted.write(docxWithText("Private Office text"));
            }
            fileSystem.writeFilesystem(output);

            assertFailure(DocumentFailureCode.PASSWORD_PROTECTED_DOCUMENT, "protected.docx",
                    "application/octet-stream", output.toByteArray());
        }
    }

    @Test
    void classifiesWhitespaceOnlyText() {
        assertFailure(DocumentFailureCode.NO_EXTRACTABLE_TEXT, "blank.txt", "text/plain",
                " \n\t ".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void classifiesImageOnlyPdfWithoutAttemptingOcr() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(LosslessFactory.createFromImage(document, image), 0, 0, 100, 100);
            }
            document.save(output);

            assertFailure(DocumentFailureCode.NO_EXTRACTABLE_TEXT, "scan.pdf", "application/pdf", output.toByteArray());
        }
    }

    @Test
    void rejectsDocumentWhenExtractedTextExceedsConfiguredLimit() {
        DocumentService service = new DocumentService(
                new TestRestClient(),
                new DocumentExtractionProperties(32, null, null, null, null)
        );

        DocumentProcessingException exception = assertThrows(
                DocumentProcessingException.class,
                () -> service.extractDocumentText(
                        "large.txt",
                        "text/plain",
                        "A".repeat(64).getBytes(StandardCharsets.UTF_8)
                )
        );

        assertEquals(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED, exception.code());
        assertEquals(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED.message(), exception.getMessage());
    }

    @Test
    void rejectsDocumentWhenStructuralBlockCountExceedsConfiguredLimit() throws IOException {
        DocumentService service = new DocumentService(
                new TestRestClient(),
                new DocumentExtractionProperties(1_000, 2, null, null, null)
        );

        DocumentProcessingException exception = assertThrows(
                DocumentProcessingException.class,
                () -> service.extractDocumentText(
                        "many-paragraphs.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docxWithParagraphs("First", "Second", "Third")
                )
        );

        assertEquals(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED, exception.code());
    }

    @Test
    void appliesCharacterLimitToStructuredDocxExtraction() throws IOException {
        DocumentService service = new DocumentService(
                new TestRestClient(),
                new DocumentExtractionProperties(32, 100, null, null, null)
        );

        DocumentProcessingException exception = assertThrows(
                DocumentProcessingException.class,
                () -> service.extractDocumentText(
                        "large.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docxWithText("A".repeat(64))
                )
        );

        assertEquals(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED, exception.code());
    }

    private void assertFailure(DocumentFailureCode expected, String filename, String contentType, byte[] bytes) {
        DocumentService service = new DocumentService(new TestRestClient());
        DocumentProcessingException exception = assertThrows(DocumentProcessingException.class,
                () -> service.extractDocumentText(filename, contentType, bytes));

        assertEquals(expected, exception.code());
        assertEquals(expected.message(), exception.getMessage());
    }

    @Test
    void extractsTextFromFetchedWebPage() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var page = service.extractWebPage(" https://example.com/page ");

        assertEquals("https://example.com/page", page.url());
        assertEquals("Demo page", page.title());
        assertEquals("Main text\n\nSecond sentence", page.content());
        assertTrue(page.extractedAt() != null);
        assertEquals("ai-workspace-web-extractor-v2/jsoup-1.18.3", page.parserVersion());
    }

    @Test
    void extractsArticleContentAndExcludesPageChrome() throws IOException {
        String html = """
                <html><head><title>Article</title></head><body>
                <nav>Navigation text</nav>
                <main><article><header><h1>Report</h1></header><p>First paragraph.</p><p>Second paragraph.</p>
                <table><tr><th>Metric</th><th>Value</th></tr><tr><td>Users</td><td>42</td></tr></table>
                </article></main><footer>Footer text</footer>
                </body></html>
                """;
        DocumentService service = new DocumentService(TestRestClient.html(html));

        var page = service.extractWebPage("https://example.com/article");

        assertEquals("# Report\n\nFirst paragraph.\n\nSecond paragraph.\n\nMetric | Value\n\nUsers | 42",
                page.content());
        assertTrue(!page.content().contains("Navigation text"));
        assertTrue(!page.content().contains("Footer text"));
    }

    @Test
    void respectsDeclaredPlainTextCharsetAndParagraphs() throws IOException {
        byte[] body = "Café\r\n\r\nSecond paragraph".getBytes(StandardCharsets.ISO_8859_1);
        DocumentService service = new DocumentService(new TestRestClient(new FetchedWebPage(
                "https://example.com/notes",
                "text/plain",
                "ISO-8859-1",
                body
        )));

        var page = service.extractWebPage("https://example.com/notes");

        assertEquals("text/plain", page.contentType());
        assertEquals(null, page.title());
        assertEquals("Café\n\nSecond paragraph", page.content());
    }

    @Test
    void respectsHtmlMetaCharsetWhenHeaderOmitsCharset() throws IOException {
        String html = "<html><head><meta charset=windows-1252></head>"
                + "<body><article><p>Price: 10 €</p></article></body></html>";
        DocumentService service = new DocumentService(new TestRestClient(new FetchedWebPage(
                "https://example.com/prices",
                "text/html",
                null,
                html.getBytes(java.nio.charset.Charset.forName("windows-1252"))
        )));

        assertEquals("Price: 10 €", service.extractWebPage("https://example.com/prices").content());
    }

    @Test
    void detectsHtmlWhenResponseOmitsContentType() throws IOException {
        String html = "<!-- proxy removed headers --><html><body><main><p>Recovered page</p></main></body></html>";
        DocumentService service = new DocumentService(new TestRestClient(new FetchedWebPage(
                "https://example.com/headerless",
                null,
                null,
                html.getBytes(StandardCharsets.UTF_8)
        )));

        var page = service.extractWebPage("https://example.com/headerless");

        assertEquals("Recovered page", page.content());
    }

    @Test
    void preservesFullExtractedWebContentWithoutFalseTruncation() throws IOException {
        String content = "A".repeat(25_000);
        DocumentService service = new DocumentService(new TestRestClient(new FetchedWebPage(
                        "https://example.com/large",
                        "text/plain",
                        "UTF-8",
                        content.getBytes(StandardCharsets.UTF_8)
                )));

        var page = service.extractWebPage("https://example.com/large");

        assertEquals(25_000, page.content().length());
        assertEquals(content, page.content());
    }

    private static class TestRestClient extends GenericRestClient {

        private final FetchedWebPage page;

        TestRestClient() {
            this(defaultPage());
        }

        TestRestClient(FetchedWebPage page) {
            super(RestClient.builder().build(), new WebPageFetchProperties(null, null));
            this.page = page;
        }

        static TestRestClient html(String html) {
            return new TestRestClient(new FetchedWebPage(
                    "https://example.com/article",
                    "text/html",
                    "UTF-8",
                    html.getBytes(StandardCharsets.UTF_8)
            ));
        }

        @Override
        public FetchedWebPage get(String rawUrl) {
            return page;
        }

        private static FetchedWebPage defaultPage() {
            String html = """
                    <!doctype html>
                    <html>
                    <head><title>Demo page</title></head>
                    <body><main><p>Main text</p><p>Second sentence</p></main></body>
                    </html>
                    """;
            return new FetchedWebPage(
                    "https://example.com/page",
                    "text/html",
                    "UTF-8",
                    html.getBytes(StandardCharsets.UTF_8)
            );
        }
    }


    private byte[] pdfWithText(String text) throws IOException {
        return pdfWithPages(text);
    }

    private byte[] pdfWithPages(String... texts) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : texts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithText(String text) throws IOException {
        return docxWithParagraphs(text);
    }

    private byte[] docxWithParagraphs(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithHeading(String heading, String paragraph) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getProperties().getCoreProperties().setTitle("Memo title");
            var headingParagraph = document.createParagraph();
            headingParagraph.setStyle("Heading1");
            headingParagraph.createRun().setText(heading);
            document.createParagraph().createRun().setText(paragraph);
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] xlsxWithText(String text) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("Data").createRow(0);
            row.createCell(0).setCellValue(text);
            row.createCell(1).setCellValue("Value");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithTable() throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = document.createTable(1, 3).getRow(0);
            row.getCell(0).setText("Metric");
            row.getCell(2).setText("Value");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] xlsxWithSheets(String... names) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String name : names) {
                workbook.createSheet(name).createRow(0).createCell(0).setCellValue(name + " content");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] pptxWithText(String text) throws IOException {
        return pptxWithSlides(text);
    }

    private byte[] pptxWithSlides(String... texts) throws IOException {
        try (XMLSlideShow presentation = new XMLSlideShow();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : texts) {
                XSLFTextBox textBox = presentation.createSlide().createTextBox();
                textBox.setAnchor(new Rectangle(50, 50, 400, 100));
                textBox.setText(text);
            }
            presentation.write(output);
            return output.toByteArray();
        }
    }
}
