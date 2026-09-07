package com.aiworkspace.documents.client;

import com.aiworkspace.documents.config.WebPageFetchProperties;
import com.aiworkspace.documents.models.FetchedWebPage;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GenericRestClient {

    private final RestClient restClient;
    private final WebPageFetchProperties properties;

    @Autowired
    public GenericRestClient(
            @Qualifier("webPageRestClient") RestClient restClient,
            WebPageFetchProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    GenericRestClient(RestClient restClient) {
        this(restClient, new WebPageFetchProperties(null, null));
    }

    public FetchedWebPage get(String rawUrl) throws IOException {
        URI uri = parseSafeHttpUri(rawUrl);

        for (int redirectCount = 0; redirectCount <= properties.maxRedirects(); redirectCount++) {
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

            MediaType contentType = validateContentType(response.headers());
            return new FetchedWebPage(
                    uri.toString(),
                    contentType == null ? null : contentType.getType() + "/" + contentType.getSubtype(),
                    contentType == null || contentType.getCharset() == null
                            ? null
                            : contentType.getCharset().name(),
                    response.body()
            );
        }

        throw new IllegalArgumentException("URL redirected too many times");
    }

    private WebResponse executeGet(URI uri) throws IOException {
        try {
            return restClient.get()
                    .uri(uri)
                    .exchange((request, response) -> {
                        long contentLength = response.getHeaders().getContentLength();
                        if (contentLength > properties.maxResponseBytes()) {
                            throw new IllegalArgumentException(maxResponseSizeMessage());
                        }

                        byte[] body = response.getBody().readNBytes(properties.maxResponseBytes() + 1);
                        if (body.length > properties.maxResponseBytes()) {
                            throw new IllegalArgumentException(maxResponseSizeMessage());
                        }

                        return new WebResponse(
                                response.getStatusCode(),
                                response.getHeaders(),
                                body
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

    private MediaType validateContentType(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType == null) {
            return null;
        }

        if ("text".equalsIgnoreCase(contentType.getType())
                || MediaType.APPLICATION_XHTML_XML.isCompatibleWith(contentType)) {
            return contentType;
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
                || isReservedIpv4(address)
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

    private boolean isReservedIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }

        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;

        return first == 0
                || first >= 240
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113);
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }

        byte[] bytes = address.getAddress();
        return (bytes[0] & 0xfe) == 0xfc || isDocumentationIpv6(bytes);
    }

    private boolean isDocumentationIpv6(byte[] bytes) {
        return (bytes[0] & 0xff) == 0x20
                && (bytes[1] & 0xff) == 0x01
                && (bytes[2] & 0xff) == 0x0d
                && (bytes[3] & 0xff) == 0xb8;
    }

    private String maxResponseSizeMessage() {
        return "Web page response must not be larger than " + properties.maxResponseBytes() + " bytes";
    }

    private record WebResponse(HttpStatusCode statusCode, HttpHeaders headers, byte[] body) {
    }
}
