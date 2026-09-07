package com.aiworkspace.documents.client;

import com.aiworkspace.documents.config.WebPageFetchProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GenericRestClientTest {

    private final GenericRestClient client = new GenericRestClient(RestClient.builder().build());

    @Test
    void rejectsLocalhostUrls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://localhost/private")
        );
    }

    @Test
    void rejectsLoopbackAddresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://127.0.0.1/private")
        );
    }

    @Test
    void rejectsPrivateNetworkAddresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://192.168.1.10/private")
        );
    }

    @Test
    void rejectsCarrierGradeNatAddresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://100.64.0.10/private")
        );
    }

    @Test
    void rejectsDocumentationAddresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://192.0.2.10/private")
        );
    }

    @Test
    void rejectsUniqueLocalIpv6Addresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://[fc00::1]/private")
        );
    }

    @Test
    void rejectsUnsupportedSchemes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("file:///etc/passwd")
        );
    }

    @Test
    void rejectsUrlUserInfo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("https://user:password@example.com/page")
        );
    }

    @Test
    void preservesResponseBytesAndDeclaredCharset() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] body = "Café".getBytes(StandardCharsets.ISO_8859_1);
        MediaType contentType = MediaType.parseMediaType("text/plain;charset=ISO-8859-1");
        server.expect(requestTo("http://93.184.216.34/page"))
                .andRespond(withSuccess(body, contentType));
        GenericRestClient testedClient = new GenericRestClient(
                builder.build(),
                new WebPageFetchProperties(null, null)
        );

        var page = testedClient.get("http://93.184.216.34/page");

        assertEquals("text/plain", page.contentType());
        assertEquals("ISO-8859-1", page.charset());
        assertArrayEquals(body, page.body());
        server.verify();
    }
}
