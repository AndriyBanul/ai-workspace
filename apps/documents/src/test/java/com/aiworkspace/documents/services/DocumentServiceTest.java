package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.GenericRestClient;
import com.aiworkspace.documents.client.RestResponseMapper;
import com.aiworkspace.documents.models.FetchedWebPage;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentServiceTest {

    @Test
    void parsesUtf8TextDocumentAndRemovesBom() {
        DocumentService service = new DocumentService(new TestRestClient());

        var document = service.parseTextDocument("notes.txt", "\uFEFFHello workspace".getBytes());

        assertEquals("notes.txt", document.filename());
        assertEquals("Hello workspace", document.content());
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
}
