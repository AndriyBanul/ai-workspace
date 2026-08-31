package com.aiworkspace.documents.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GenericRestClient {

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final RestClient restClient;

    public GenericRestClient(@Qualifier("webPageRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public <T> T get(String rawUrl, RestResponseMapper<T> responseMapper) throws IOException {
        URI uri = parseSafeHttpUri(rawUrl);

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            WebResponse response = executeGet(uri);
            if (isRedirect(response.statusCode())) {
                uri = redirectUri(uri, response.headers());
                continue;
            }

            if (!response.statusCode().is2xxSuccessful()) {
                throw new UpstreamServiceException(
                        "Web page fetcher",
                        "Web page returned HTTP " + response.statusCode().value()
                );
            }

            validateContentType(response.headers());
            return responseMapper.map(uri.toString(), response.body());
        }

        throw new IllegalArgumentException("URL redirected too many times");
    }

    private WebResponse executeGet(URI uri) throws IOException {
        try {
            return restClient.get()
                    .uri(uri)
                    .exchange((request, response) -> {
                        long contentLength = response.getHeaders().getContentLength();
                        if (contentLength > MAX_RESPONSE_BYTES) {
                            throw new IllegalArgumentException("Web page response must not be larger than 2MB");
                        }

                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) {
                            throw new IllegalArgumentException("Web page response must not be larger than 2MB");
                        }

                        return new WebResponse(
                                response.getStatusCode(),
                                response.getHeaders(),
                                new String(body, StandardCharsets.UTF_8)
                        );
                    });
        } catch (RestClientException exception) {
            throw new UpstreamServiceException("Web page fetcher", "Failed to fetch web page", exception);
        }
    }

    private URI parseSafeHttpUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL must not be blank");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("URL must be a valid URI", exception);
        }

        String scheme = uri.getScheme();

        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are supported");
        }

        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new IllegalArgumentException("URL user info is not supported");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL host must not be blank");
        }

        validatePublicHost(uri.getHost());
        return uri;
    }

    private URI redirectUri(URI currentUri, HttpHeaders headers) {
        List<String> locations = headers.get(HttpHeaders.LOCATION);
        if (locations == null || locations.isEmpty() || locations.getFirst().isBlank()) {
            throw new UpstreamServiceException("Web page fetcher", "Web page redirect did not include a Location header");
        }

        return parseSafeHttpUri(currentUri.resolve(locations.getFirst()).toString());
    }

    private boolean isRedirect(HttpStatusCode statusCode) {
        int status = statusCode.value();
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private void validateContentType(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType == null) {
            return;
        }

        if ("text".equalsIgnoreCase(contentType.getType())
                || MediaType.APPLICATION_XHTML_XML.isCompatibleWith(contentType)) {
            return;
        }

        throw new IllegalArgumentException("URL must return HTML or text content");
    }

    private void validatePublicHost(String host) {
        if ("localhost".equalsIgnoreCase(host) || host.toLowerCase().endsWith(".localhost")) {
            throw new IllegalArgumentException("URL host must resolve to a public address");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new UpstreamServiceException("Web page fetcher", "URL host could not be resolved", exception);
        }

        if (addresses.length == 0) {
            throw new IllegalArgumentException("URL host must resolve to a public address");
        }

        for (InetAddress address : addresses) {
            if (isNonPublicAddress(address)) {
                throw new IllegalArgumentException("URL host must resolve to a public address");
            }
        }
    }

    private boolean isNonPublicAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNatIpv4(address)
                || isUniqueLocalIpv6(address);
    }

    private boolean isCarrierGradeNatIpv4(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }

        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }

        byte[] bytes = address.getAddress();
        return (bytes[0] & 0xfe) == 0xfc;
    }

    private record WebResponse(HttpStatusCode statusCode, HttpHeaders headers, String body) {
    }
}
