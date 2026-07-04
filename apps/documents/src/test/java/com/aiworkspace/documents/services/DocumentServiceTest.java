package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.GenericRestClient;
import com.aiworkspace.documents.client.RestResponseMapper;
import com.aiworkspace.documents.models.FetchedWebPage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentServiceTest {

    @Test
    void parsesUtf8TextDocumentAndRemovesBom() {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.parseTextDocument("notes.txt", "\uFEFFHello workspace".getBytes());

        assertEquals("notes.txt", document.filename());
        assertEquals("Hello workspace", document.content());
    }

    @Test
    void extractsUtf8TextDocumentWithTika() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText("notes.txt", "text/plain", "\uFEFFHello workspace".getBytes());

        assertEquals("notes.txt", document.filename());
        assertEquals("Hello workspace", document.content().trim());
    }

    @Test
    void extractsPdfTextWithTika() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.extractDocumentText("deck.pdf", "application/pdf", pdfWithText("PDF workspace text"));

        assertEquals("deck.pdf", document.filename());
        assertTrue(document.content().contains("PDF workspace text"));
    }

    @Test
    void extractsTextFromFetchedWebPage() throws IOException {
        DocumentService service = new DocumentService(new TestRestClient());

        var page = service.extractWebPage(" https://example.com/page ");

        assertEquals("https://example.com/page", page.url());
        assertEquals("Demo page", page.title());
        assertEquals("Main text Second sentence", page.content());
    }

    private static class TestRestClient extends GenericRestClient {

        TestRestClient() {
            super(RestClient.builder().build());
        }

        @Override
        public <T> T get(String rawUrl, RestResponseMapper<T> responseMapper) throws IOException {
            String html = """
                    <!doctype html>
                    <html>
                    <head><title>Demo page</title></head>
                    <body><main>Main text</main><p>Second sentence</p></body>
                    </html>
                    """;
            return responseMapper.map(rawUrl.trim(), html);
        }
    }

    private byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
