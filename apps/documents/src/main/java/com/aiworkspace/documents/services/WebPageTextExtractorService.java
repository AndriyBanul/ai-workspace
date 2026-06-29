package com.aiworkspace.documents.services;

import com.aiworkspace.documents.client.RestClient;
import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.FetchedWebPage;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class WebPageTextExtractorService {

    private final RestClient restClient;

    public WebPageTextExtractorService(RestClient restClient) {
        this.restClient = restClient;
    }

    public ExtractedWebPage extract(String rawUrl) throws IOException, InterruptedException {
        FetchedWebPage fetchedWebPage = restClient.get(rawUrl, FetchedWebPage::new);

        Document document = Jsoup.parse(fetchedWebPage.html(), fetchedWebPage.url());
        String content = document.body() == null ? document.text() : document.body().text();

        return new ExtractedWebPage(fetchedWebPage.url(), document.title(), content);
    }
}
