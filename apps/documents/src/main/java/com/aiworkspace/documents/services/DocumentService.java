package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.GenericRestClient;
import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.FetchedWebPage;
import com.aiworkspace.documents.models.ParsedTextDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final GenericRestClient restClient;

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

    public ExtractedWebPage extractWebPage(String rawUrl) throws IOException {
        FetchedWebPage fetchedWebPage = restClient.get(rawUrl, FetchedWebPage::new);

        Document document = Jsoup.parse(fetchedWebPage.html(), fetchedWebPage.url());
        String content = document.body() == null ? document.text() : document.body().text();

        return new ExtractedWebPage(fetchedWebPage.url(), document.title(), content);
    }
}
