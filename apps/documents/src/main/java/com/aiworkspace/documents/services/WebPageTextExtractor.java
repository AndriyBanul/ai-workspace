package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.WebPageRestClient;
import com.aiworkspace.documents.domain.ExtractedWebPage;
import com.aiworkspace.documents.domain.FetchedWebPage;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class WebPageTextExtractor {

    private final WebPageRestClient webPageRestClient;

    public WebPageTextExtractor(WebPageRestClient webPageRestClient) {
        this.webPageRestClient = webPageRestClient;
    }

    public ExtractedWebPage extract(String rawUrl) throws IOException, InterruptedException {
        FetchedWebPage fetchedWebPage = webPageRestClient.fetch(rawUrl);

        Document document = Jsoup.parse(fetchedWebPage.html(), fetchedWebPage.url());
        String content = document.body() == null ? document.text() : document.body().text();

        return new ExtractedWebPage(fetchedWebPage.url(), document.title(), content);
    }
}
