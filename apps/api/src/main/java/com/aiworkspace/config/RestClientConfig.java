package com.aiworkspace.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
public class RestClientConfig {

    @Bean
    @Primary
    public RestClient restClient(HttpClientProperties properties) {
        return restClient(properties, HttpClient.Redirect.NORMAL);
    }

    @Bean
    public RestClient webPageRestClient(HttpClientProperties properties) {
        return restClient(properties, HttpClient.Redirect.NEVER);
    }

    private RestClient restClient(HttpClientProperties properties, HttpClient.Redirect redirectPolicy) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(redirectPolicy)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }
}
