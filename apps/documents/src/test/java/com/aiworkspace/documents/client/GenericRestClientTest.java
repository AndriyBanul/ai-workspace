package com.aiworkspace.documents.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GenericRestClientTest {

    private final GenericRestClient client = new GenericRestClient(RestClient.builder().build());

    @Test
    void rejectsLocalhostUrls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://localhost/private", (url, body) -> body)
        );
    }

    @Test
    void rejectsLoopbackAddresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://127.0.0.1/private", (url, body) -> body)
        );
    }

    @Test
    void rejectsPrivateNetworkAddresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("http://192.168.1.10/private", (url, body) -> body)
        );
    }

    @Test
    void rejectsUnsupportedSchemes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("file:///etc/passwd", (url, body) -> body)
        );
    }

    @Test
    void rejectsUrlUserInfo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("https://user:password@example.com/page", (url, body) -> body)
        );
    }
}
