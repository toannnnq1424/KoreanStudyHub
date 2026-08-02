package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Component
public class RestClientPracticeAiProviderTransport
        implements PracticeAiProviderTransport {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/chat/completions",
            "/audio/transcriptions",
            "/audio/speech");

    private final ObjectMapper objectMapper;

    public RestClientPracticeAiProviderTransport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderResponse exchange(
            PracticeAiResolvedBinding binding,
            String path,
            MediaType contentType,
            MediaType accept,
            Object body,
            Map<String, String> headers) {
        if (!ALLOWED_PATHS.contains(path)) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_TRANSPORT_PATH_INVALID", false);
        }
        PracticeAiLimits limits = binding.snapshot().limits();
        requireBoundedJsonRequest(contentType, body, limits.maxRequestBytes());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(limits.connectTimeoutMs());
        factory.setReadTimeout(limits.readTimeoutMs());
        RestClient client = RestClient.builder()
                .baseUrl(binding.baseUrl().toString())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + binding.credentialSecret())
                .requestFactory(factory)
                .build();
        return client.post()
                .uri(path)
                .contentType(contentType)
                .accept(accept)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .body(body)
                .exchange((request, response) -> {
                    byte[] responseBody = bounded(
                            response.getBody(), limits.maxResponseBytes());
                    String responseContentType = response.getHeaders().getContentType() == null
                            ? ""
                            : response.getHeaders().getContentType().toString();
                    String requestReference = firstHeader(
                            response.getHeaders(), "x-request-id", "request-id");
                    return new ProviderResponse(
                            response.getStatusCode().value(),
                            responseBody,
                            responseContentType,
                            requestReference);
                });
    }

    private void requireBoundedJsonRequest(
            MediaType contentType,
            Object body,
            int maximum) {
        if (contentType == null || !MediaType.APPLICATION_JSON.includes(contentType)) {
            return;
        }
        byte[] bytes;
        try {
            bytes = body instanceof String text
                    ? text.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException exception) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_REQUEST_INVALID", false, exception);
        }
        if (bytes.length > maximum) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_REQUEST_TOO_LARGE", false);
        }
    }

    private static byte[] bounded(java.io.InputStream input, int maximum) {
        if (input == null) {
            return new byte[0];
        }
        try (input) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) {
                throw new PracticeAiControlPlaneException(
                        "PROVIDER_RESPONSE_TOO_LARGE", false);
            }
            return bytes;
        } catch (IOException exception) {
            throw new PracticeAiControlPlaneException(
                    "PROVIDER_TRANSPORT_ERROR", true, exception);
        }
    }

    private static String firstHeader(HttpHeaders headers, String... names) {
        for (String name : names) {
            String value = headers.getFirst(name);
            if (value != null && !value.isBlank()) {
                return value.length() <= 255 ? value : value.substring(0, 255);
            }
        }
        return "";
    }
}
