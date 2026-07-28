package com.ksh.features.practice.service.audio;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Narrow OpenAI audio HTTP primitive shared by learner transcription and
 * lecturer prompt authoring adapters. Domain authorization, retries and
 * provider-result mapping stay with each caller.
 */
public final class OpenAiAudioHttpTransport {

    private final RestClient restClient;

    public OpenAiAudioHttpTransport(
            String baseUrl,
            String apiKey,
            Duration connectTimeout,
            Duration readTimeout) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
    }

    public String postForString(
            String path,
            MediaType contentType,
            Object body) {
        return restClient.post()
                .uri(path)
                .contentType(contentType)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public BoundedResponse postBounded(
            String path,
            MediaType contentType,
            Object body,
            long maximumResponseBytes) {
        if (maximumResponseBytes <= 0
                || maximumResponseBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "maximumResponseBytes must be within the supported range");
        }
        return restClient.post()
                .uri(path)
                .contentType(contentType)
                .body(body)
                .exchange((request, response) -> {
                    byte[] bytes = response.getBody().readNBytes(
                            Math.toIntExact(maximumResponseBytes + 1L));
                    if (bytes.length > maximumResponseBytes) {
                        throw new ResponseTooLargeException();
                    }
                    return new BoundedResponse(
                            response.getStatusCode().value(),
                            bytes,
                            response.getHeaders().getFirst("Content-Type"));
                });
    }

    private static SimpleClientHttpRequestFactory requestFactory(
            Duration connectTimeout,
            Duration readTimeout) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis(connectTimeout));
        factory.setReadTimeout(timeoutMillis(readTimeout));
        return factory;
    }

    private static int timeoutMillis(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "OpenAI audio HTTP timeout must be positive");
        }
        return Math.toIntExact(Math.min(
                value.toMillis(),
                Integer.MAX_VALUE));
    }

    public static final class BoundedResponse {
        private final int status;
        private final byte[] body;
        private final String contentType;

        private BoundedResponse(
                int status,
                byte[] body,
                String contentType) {
            this.status = status;
            this.body = body == null ? new byte[0] : body.clone();
            this.contentType = contentType;
        }

        public int status() {
            return status;
        }

        public byte[] body() {
            return body.clone();
        }

        public String contentType() {
            return contentType;
        }

        @Override
        public String toString() {
            return "BoundedResponse{status=" + status
                    + ", byteSize=" + body.length
                    + ", contentTypePresent=" + (contentType != null)
                    + '}';
        }
    }

    public static final class ResponseTooLargeException
            extends RuntimeException {
        public ResponseTooLargeException() {
            super("OpenAI audio response exceeded its configured bound");
        }
    }
}
