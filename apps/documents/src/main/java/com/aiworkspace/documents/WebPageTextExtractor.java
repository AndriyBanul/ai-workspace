package com.aiworkspace.documents;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class WebPageTextExtractor {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "AI-Workspace/0.1";

    private final HttpClient httpClient;

    public WebPageTextExtractor() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    WebPageTextExtractor(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ExtractedWebPage extract(String rawUrl) throws IOException, InterruptedException {
        URI uri = parseHttpUri(rawUrl);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unexpected HTTP status: " + response.statusCode());
        }

        Document document = Jsoup.parse(response.body(), uri.toString());
        String content = document.body() == null ? document.text() : document.body().text();

        return new ExtractedWebPage(uri.toString(), document.title(), content);
    }

    private URI parseHttpUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }

        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme();

        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are supported");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL host must not be blank");
        }

        return uri;
    }
}
