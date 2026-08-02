package com.ksh.features.practice.ai.controlplane;

import org.springframework.http.MediaType;

import java.util.Map;

public interface PracticeAiProviderTransport {

    ProviderResponse exchange(
            PracticeAiResolvedBinding binding,
            String path,
            MediaType contentType,
            MediaType accept,
            Object body,
            Map<String, String> headers);

    record ProviderResponse(
            int status,
            byte[] body,
            String contentType,
            String requestReference
    ) {
        public ProviderResponse {
            body = body == null ? new byte[0] : body.clone();
            contentType = contentType == null ? "" : contentType;
            requestReference = requestReference == null ? "" : requestReference;
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        public boolean successful() {
            return status >= 200 && status < 300;
        }
    }
}
