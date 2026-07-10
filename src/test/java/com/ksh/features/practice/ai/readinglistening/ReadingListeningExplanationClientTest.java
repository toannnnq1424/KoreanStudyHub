package com.ksh.features.practice.ai.readinglistening;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.features.practice.ai.OpenAiProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadingListeningExplanationClientTest {

    @Test
    void providerErrorLogOmitsResponseAndRequestBodyButKeepsSafeMetadata() {
        OpenAiProperties properties = mock(OpenAiProperties.class);
        when(properties.apiKey()).thenReturn("SECRET_API_KEY_VALUE");
        when(properties.baseUrl()).thenReturn("http://localhost");
        when(properties.evaluatorModel()).thenReturn("safe-model");
        ReadingListeningExplanationClient client = new ReadingListeningExplanationClient(properties, new ObjectMapper());
        ReflectionTestUtils.setField(client, "restClient", httpErrorRestClient(
                "PRIVATE_PROVIDER_RESPONSE PRIVATE_PROMPT_TEXT PRIVATE_CACHE_JSON"));
        PracticeQuestion question = new PracticeQuestion(
                10L,
                1,
                "MCQ",
                "PRIVATE_PROMPT_TEXT",
                "[\"A\",\"B\"]",
                "1",
                "stored explanation",
                BigDecimal.ONE,
                0
        );

        String logs = captureLogs(ReadingListeningExplanationClient.class, () -> {
            String result = client.explain(question, "PRIVATE_CACHE_JSON passage", "READING", "NUMERIC");
            assertNull(result);
        });

        assertFalse(logs.contains("PRIVATE_PROVIDER_RESPONSE"));
        assertFalse(logs.contains("PRIVATE_PROMPT_TEXT"));
        assertFalse(logs.contains("PRIVATE_CACHE_JSON"));
        assertFalse(logs.contains("SECRET_API_KEY_VALUE"));
        assertTrue(logs.contains("status=400"));
        assertTrue(logs.contains("model=safe-model"));
        assertTrue(logs.contains("skill=READING"));
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
