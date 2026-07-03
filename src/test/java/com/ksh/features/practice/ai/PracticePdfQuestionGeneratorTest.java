package com.ksh.features.practice.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticePdfQuestionGeneratorTest {

    @Test
    void providerHttpErrorLogOmitsPayloadAndKeepsSafeMetadata() {
        PracticePdfQuestionGenerator generator = new PracticePdfQuestionGenerator(properties(), new ObjectMapper());
        ReflectionTestUtils.setField(generator, "restClient", httpErrorRestClient(
                "PRIVATE_PROVIDER_RESPONSE PRIVATE_PDF_DOCUMENT_TEXT PRIVATE_PROVIDER_REQUEST SECRET_API_KEY_VALUE PRIVATE_FILE_PATH PRIVATE_USER_EMAIL"));

        String logs = captureLogs(PracticePdfQuestionGenerator.class, () ->
                assertThrows(IllegalStateException.class, () ->
                        generator.generate("PRIVATE_PDF_DOCUMENT_TEXT PRIVATE_FILE_PATH PRIVATE_USER_EMAIL", "READING", "TOPIK_I", "PRIVATE_PROVIDER_REQUEST")));

        assertFalse(logs.contains("PRIVATE_PROVIDER_RESPONSE"));
        assertFalse(logs.contains("PRIVATE_PDF_DOCUMENT_TEXT"));
        assertFalse(logs.contains("PRIVATE_PROVIDER_REQUEST"));
        assertFalse(logs.contains("SECRET_API_KEY_VALUE"));
        assertFalse(logs.contains("PRIVATE_FILE_PATH"));
        assertFalse(logs.contains("PRIVATE_USER_EMAIL"));
        assertTrue(logs.contains("examTemplate=OTHER"));
        assertTrue(logs.contains("operation=provider-call"));
        assertTrue(logs.contains("model=safe-model"));
        assertTrue(logs.contains("status=400"));
        assertTrue(logs.contains("textChars="));
    }

    @Test
    void malformedProviderJsonLogOmitsGeneratedContentPreviewButKeepsCounts() {
        PracticePdfQuestionGenerator generator = new PracticePdfQuestionGenerator(properties(), new ObjectMapper());
        ReflectionTestUtils.setField(generator, "restClient", successRestClient("PRIVATE_GENERATED_QUESTION not json"));

        String logs = captureLogs(PracticePdfQuestionGenerator.class, () ->
                assertThrows(IllegalStateException.class, () ->
                        generator.generate("PRIVATE_PDF_DOCUMENT_TEXT PRIVATE_FILE_PATH PRIVATE_USER_EMAIL", "READING", "TOPIK_I")));

        assertFalse(logs.contains("PRIVATE_GENERATED_QUESTION"));
        assertFalse(logs.contains("PRIVATE_PDF_DOCUMENT_TEXT"));
        assertFalse(logs.contains("PRIVATE_FILE_PATH"));
        assertFalse(logs.contains("PRIVATE_USER_EMAIL"));
        assertTrue(logs.contains("rawResponseChars="));
        assertTrue(logs.contains("contentChars="));
        assertTrue(logs.contains("operation=provider-parse"));
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

    private RestClient successRestClient(String content) {
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
        when(responseSpec.body(any(Class.class))).thenReturn("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}");
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
