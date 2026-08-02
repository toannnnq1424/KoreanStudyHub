package com.ksh.features.practice.manage.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeAiRequestAudit;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.PracticeModelCapabilityProfile;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationPort;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationResponse;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfAiOrchestratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void purposeFailureLogOmitsPayloadSecretAndProviderBody() {
        PracticeAiRequestAuditRepository auditRepository =
                mock(PracticeAiRequestAuditRepository.class);
        PracticeStructuredGenerationPort port = port();
        when(port.generate(any())).thenThrow(
                new PracticeAiContractException("PROVIDER_HTTP_ERROR", false));
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                objectMapper, auditRepository, port);

        String logs = captureLogs(PracticePdfAiOrchestrator.class, () ->
                assertThrows(PracticeAiContractException.class, () ->
                        orchestrator.callAi(payload(), 123L, "SAFE_STRATEGY")));

        assertThat(logs).contains("sessionId=123");
        assertThat(logs).doesNotContain(
                "PRIVATE_PROVIDER_RESPONSE",
                "PRIVATE_PDF_DOCUMENT_TEXT",
                "SECRET_API_KEY_VALUE",
                "PRIVATE_USER_EMAIL");
        ArgumentCaptor<PracticeAiRequestAudit> auditCaptor =
                ArgumentCaptor.forClass(PracticeAiRequestAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getErrorCode())
                .isEqualTo("PROVIDER_HTTP_ERROR");
    }

    @Test
    void usesExactPdfPurposeAndReturnsFakeStructuredOutput() throws Exception {
        PracticeStructuredGenerationPort port = port();
        when(port.generate(any())).thenAnswer(invocation -> {
            PracticeStructuredGenerationRequest request = invocation.getArgument(0);
            assertThat(request.purpose())
                    .isEqualTo(PracticeAiPurpose.PRACTICE_PDF_AUTHORING);
            assertThat(request.images()).isEmpty();
            return new PracticeStructuredGenerationResponse(
                    objectMapper.readTree("{\"documentTitle\":\"PRIVATE_GENERATED_QUESTION\"}"),
                    "PRACTICE_PRIMARY",
                    "safe-model",
                    "stop",
                    "fake-request");
        });
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                objectMapper, mock(PracticeAiRequestAuditRepository.class), port);

        String logs = captureLogs(PracticePdfAiOrchestrator.class, () -> {
            String result = orchestrator.callAi(payload(), 123L, "SAFE_STRATEGY");
            assertThat(result).contains("PRIVATE_GENERATED_QUESTION");
        });

        assertThat(logs).doesNotContain(
                "PRIVATE_PDF_DOCUMENT_TEXT",
                "PRIVATE_GENERATED_QUESTION",
                "SECRET_API_KEY_VALUE");
    }

    private PracticeStructuredGenerationPort port() {
        PracticeStructuredGenerationPort port = mock(PracticeStructuredGenerationPort.class);
        when(port.identity(PracticeAiPurpose.PRACTICE_PDF_AUTHORING)).thenReturn(
                new PracticeStructuredGenerationPort.ProviderIdentity(
                        "OPENAI_COMPATIBLE",
                        "safe-model",
                        PracticeModelCapabilityProfile.openAiAssessmentV1(),
                        true,
                        4L,
                        2L,
                        "PRACTICE_PRIMARY"));
        return port;
    }

    private PracticePdfAiPayloadBuilder.PayloadInfo payload() {
        return new PracticePdfAiPayloadBuilder.PayloadInfo(
                new AiDocumentImportRequest(),
                "PRIVATE_PDF_DOCUMENT_TEXT",
                List.of(),
                Map.of("finalSentTextCharacters", 25, "estimatedImageBytes", 0),
                List.of());
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
