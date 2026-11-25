package com.stocksense.config;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpSupport {

    private HttpSupport() {
    }

    /** RestClient builder with a base URL, connect timeout and read timeout. */
    public static RestClient.Builder configure(RestClient.Builder builder, String baseUrl, int readTimeoutSeconds) {
        HttpClient http = HttpClient.newBuilder()
                // HTTP/1.1 only: the JDK's default h2c upgrade makes uvicorn drop POST bodies (FastAPI 422 "body missing")
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return builder.baseUrl(baseUrl).requestFactory(factory);
    }
}
