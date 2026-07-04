package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.GenericRestClient;
import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.FetchedWebPage;
import com.aiworkspace.documents.models.ParsedTextDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

@Service
public class DocumentService {

    private final GenericRestClient restClient;
    private final AutoDetectParser parser = new AutoDetectParser();

    public DocumentService(GenericRestClient restClient) {
        this.restClient = restClient;
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

    private String removeBom(String content) {
        if (content.startsWith("\uFEFF")) {
            return content.substring(1);
        }

        return content;
    }
}
