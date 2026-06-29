package com.aiworkspace.documents.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class GenericRestClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "My-Space";

    private final org.springframework.web.client.RestClient restClient;

    public GenericRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.restClient = org.springframework.web.client.RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    public <T> T get(String rawUrl, RestResponseMapper<T> responseMapper) throws IOException {
        URI uri = parseHttpUri(rawUrl);

        try {
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            return responseMapper.map(uri.toString(), body == null ? "" : body);
        } catch (RestClientException exception) {
            throw new IOException("Failed to execute GET request to " + uri, exception);
        }
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
