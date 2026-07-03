package com.ksh.features.practice.manage.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.OpenAiProperties;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticePdfAiOrchestratorTest {

    @Test
    void providerHttpErrorLogOmitsPayloadAndKeepsSafeMetadata() {
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                properties(), new ObjectMapper(), mock(PracticeAiRequestAuditRepository.class));
        ReflectionTestUtils.setField(orchestrator, "restClient", httpErrorRestClient(
                "PRIVATE_PROVIDER_RESPONSE PRIVATE_PDF_DOCUMENT_TEXT PRIVATE_PROVIDER_REQUEST SECRET_API_KEY_VALUE PRIVATE_FILE_PATH PRIVATE_USER_EMAIL"));

        PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo = new PracticePdfAiPayloadBuilder.PayloadInfo(
                new AiDocumentImportRequest(),
                "PRIVATE_PDF_DOCUMENT_TEXT",
                List.of(),
                Map.of("finalSentTextCharacters", 25, "estimatedImageBytes", 0),
                List.of()
        );

        String logs = captureLogs(PracticePdfAiOrchestrator.class, () ->
                assertThrows(IllegalStateException.class, () ->
                        orchestrator.callAi(payloadInfo, 123L, "SAFE_STRATEGY")));

        assertFalse(logs.contains("PRIVATE_PROVIDER_RESPONSE"));
        assertFalse(logs.contains("PRIVATE_PDF_DOCUMENT_TEXT"));
        assertFalse(logs.contains("PRIVATE_PROVIDER_REQUEST"));
        assertFalse(logs.contains("SECRET_API_KEY_VALUE"));
        assertFalse(logs.contains("PRIVATE_FILE_PATH"));
        assertFalse(logs.contains("PRIVATE_USER_EMAIL"));
        assertTrue(logs.contains("sessionId=123"));
        assertTrue(logs.contains("operation=provider-call"));
        assertTrue(logs.contains("model=safe-model"));
        assertTrue(logs.contains("status=400"));
    }

    @Test
    void retryableProviderErrorLogOmitsBodyAndReturnsSuccessfulGeneratedContent() {
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                properties(), new ObjectMapper(), mock(PracticeAiRequestAuditRepository.class));
        ReflectionTestUtils.setField(orchestrator, "restClient", retryThenSuccessRestClient(
                "PRIVATE_PROVIDER_RESPONSE PRIVATE_PDF_DOCUMENT_TEXT PRIVATE_PROVIDER_REQUEST SECRET_API_KEY_VALUE PRIVATE_FILE_PATH PRIVATE_USER_EMAIL",
                "PRIVATE_GENERATED_QUESTION"));

        PracticePdfAiPayloadBuilder.PayloadInfo payloadInfo = new PracticePdfAiPayloadBuilder.PayloadInfo(
                new AiDocumentImportRequest(),
                "PRIVATE_PDF_DOCUMENT_TEXT",
                List.of(),
                Map.of("finalSentTextCharacters", 25, "estimatedImageBytes", 0),
                List.of()
        );

        final String[] result = new String[1];
        String logs = captureLogs(PracticePdfAiOrchestrator.class, () ->
                result[0] = orchestrator.callAi(payloadInfo, 123L, "SAFE_STRATEGY"));

        assertTrue(result[0].contains("PRIVATE_GENERATED_QUESTION"));
        assertFalse(logs.contains("PRIVATE_PROVIDER_RESPONSE"));
        assertFalse(logs.contains("PRIVATE_PDF_DOCUMENT_TEXT"));
        assertFalse(logs.contains("PRIVATE_PROVIDER_REQUEST"));
        assertFalse(logs.contains("SECRET_API_KEY_VALUE"));
        assertFalse(logs.contains("PRIVATE_FILE_PATH"));
        assertFalse(logs.contains("PRIVATE_USER_EMAIL"));
        assertFalse(logs.contains("PRIVATE_GENERATED_QUESTION"));
        assertTrue(logs.contains("operation=provider-call"));
        assertTrue(logs.contains("model=safe-model"));
        assertTrue(logs.contains("status=429"));
        assertTrue(logs.contains("retryable=true"));
    }

    private OpenAiProperties properties() {
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.apiKey()).thenReturn("SECRET_API_KEY_VALUE");
        when(properties.baseUrl()).thenReturn("http://localhost");
        when(properties.evaluatorModel()).thenReturn("safe-model");
        return properties;
    }

    private RestClient httpErrorRestClient(String responseBody) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(), any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class))).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));
        return restClient;
    }

    private RestClient retryThenSuccessRestClient(String responseBody, String generatedContent) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(), any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(Class.class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        responseBody.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8
                ))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"" + generatedContent + "\"}}]}");
        return restClient;
    }

    private static String captureLogs(Class<?> loggerClass, Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        StringBuilder logs = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            logs.append(event.getFormattedMessage()).append('\n');
        }
        return logs.toString();
    }
}
