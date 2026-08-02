package com.ksh.features.practice.manage.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.AiSystemPrompt;
import com.ksh.entities.PracticeAiRequestAudit;
import com.ksh.features.admin.settings.repository.AiSystemPromptRepository;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.PracticeModelCapabilityProfile;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationPort;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationRequest;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationResponse;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfAiOrchestratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void unavailableExactPurposeFailsClosedBeforeProviderCall() {
        PracticeStructuredGenerationPort port = mock(PracticeStructuredGenerationPort.class);
        when(port.identity(PracticeAiPurpose.PRACTICE_PDF_AUTHORING)).thenReturn(
                new PracticeStructuredGenerationPort.ProviderIdentity(
                        "", "", PracticeModelCapabilityProfile.openAiAssessmentV1(),
                        false, 0L, -1L, ""));
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                objectMapper, mock(PracticeAiRequestAuditRepository.class), port);

        PracticeAiContractException failure = assertThrows(
                PracticeAiContractException.class,
                () -> orchestrator.generate(request(123L, SourceOperation.EXTRACT)));

        assertThat(failure.category()).isEqualTo("PROVIDER_PURPOSE_UNAVAILABLE");
        verify(port, never()).generate(any());
    }

    @Test
    void requestUsesExactPurposeCodeOwnedSchemaAndSeparatedPromptLayers() throws Exception {
        PracticeStructuredGenerationPort port = availablePort();
        AiSystemPromptRepository prompts = mock(AiSystemPromptRepository.class);
        AiSystemPrompt prompt = new AiSystemPrompt(
                "PRACTICE_PDF_AUTHORING", "pedagogy", "ADMIN_PEDAGOGY_ONLY");
        when(prompts.findByNameAndEnabledTrue("PRACTICE_PDF_AUTHORING"))
                .thenReturn(Optional.of(prompt));
        when(port.generate(any())).thenReturn(new PracticeStructuredGenerationResponse(
                objectMapper.readTree("{\"schemaVersion\":\"practice-pdf-authoring-output-v1\"}"),
                "PRACTICE_PRIMARY", "safe-model", "stop", "fake-request"));
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                objectMapper, mock(PracticeAiRequestAuditRepository.class), port, prompts);

        PracticePdfAiOrchestrator.GenerationResult result = orchestrator.generate(
                request(null, SourceOperation.GENERATE));

        ArgumentCaptor<PracticeStructuredGenerationRequest> captor =
                ArgumentCaptor.forClass(PracticeStructuredGenerationRequest.class);
        verify(port).generate(captor.capture());
        PracticeStructuredGenerationRequest sent = captor.getValue();
        assertThat(sent.purpose()).isEqualTo(PracticeAiPurpose.PRACTICE_PDF_AUTHORING);
        assertThat(sent.operation()).isEqualTo("GENERATE");
        assertThat(sent.responseSchemaName())
                .isEqualTo(PracticePdfAuthoringJsonContract.RESPONSE_SCHEMA_NAME);
        assertThat(sent.responseSchema()).isEqualTo(PracticePdfAuthoringJsonContract.schema());
        assertThat(sent.systemInstruction())
                .contains("PRACTICE_PDF_AUTHORING", "không đáng tin cậy")
                .doesNotContain("ADMIN_PEDAGOGY_ONLY", "PRIVATE_PDF_DOCUMENT_TEXT");
        assertThat(sent.developerInstruction()).contains("ADMIN_PEDAGOGY_ONLY");
        assertThat(sent.input()).containsEntry("lecturerRequirements", "PRIVATE_LECTURER_REQUEST");
        assertThat(sent.input().get("untrustedSource").toString())
                .contains("PRIVATE_PDF_DOCUMENT_TEXT");
        assertThat(sent.authority().authorityIdentity())
                .contains("binding=4", "profile=PRACTICE_PRIMARY");
        assertThat(result.aiExecution().path("purpose").asText())
                .isEqualTo("PRACTICE_PDF_AUTHORING");
        assertThat(result.aiExecution().path("bindingRevision").asLong()).isEqualTo(4L);
        verify(prompts).findByNameAndEnabledTrue("PRACTICE_PDF_AUTHORING");
    }

    @Test
    void bindingRevisionChangeAfterProviderResponseFailsClosed() throws Exception {
        PracticeStructuredGenerationPort port = mock(PracticeStructuredGenerationPort.class);
        PracticeStructuredGenerationPort.ProviderIdentity before = identity(4L);
        PracticeStructuredGenerationPort.ProviderIdentity after = identity(5L);
        when(port.identity(PracticeAiPurpose.PRACTICE_PDF_AUTHORING))
                .thenReturn(before, after);
        when(port.generate(any())).thenReturn(new PracticeStructuredGenerationResponse(
                objectMapper.readTree("{}"), "PRACTICE_PRIMARY", "safe-model",
                "stop", "provider-request"));
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                objectMapper, mock(PracticeAiRequestAuditRepository.class), port);

        PracticeAiContractException failure = assertThrows(
                PracticeAiContractException.class,
                () -> orchestrator.generate(request(null, SourceOperation.EXTRACT)));

        assertThat(failure.category()).isEqualTo("PROVIDER_BINDING_CHANGED");
    }

    @Test
    void lecturerRequirementsParticipateInIdempotencyAndCandidateSourceRevision()
            throws Exception {
        PracticeStructuredGenerationPort port = availablePort();
        when(port.generate(any())).thenReturn(new PracticeStructuredGenerationResponse(
                objectMapper.readTree("{}"), "PRACTICE_PRIMARY", "safe-model",
                "stop", "provider-request"));
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                objectMapper, mock(PracticeAiRequestAuditRepository.class), port);

        PracticePdfAiOrchestrator.GenerationResult first = orchestrator.generate(
                request(null, SourceOperation.GENERATE, "Tạo ba câu"));
        PracticePdfAiOrchestrator.GenerationResult second = orchestrator.generate(
                request(null, SourceOperation.GENERATE, "Tạo năm câu"));

        ArgumentCaptor<PracticeStructuredGenerationRequest> sent =
                ArgumentCaptor.forClass(PracticeStructuredGenerationRequest.class);
        verify(port, times(2)).generate(sent.capture());
        assertThat(sent.getAllValues()).extracting(
                        PracticeStructuredGenerationRequest::idempotencyKey)
                .doesNotHaveDuplicates();
        assertThat(first.sourceRevision()).isNotEqualTo(second.sourceRevision());
    }

    @Test
    void providerFailureAuditAndLogsOmitSourcePromptAndProviderBody() {
        PracticeAiRequestAuditRepository audits =
                mock(PracticeAiRequestAuditRepository.class);
        PracticeStructuredGenerationPort port = availablePort();
        when(port.generate(any())).thenThrow(
                new PracticeAiContractException("PROVIDER_HTTP_ERROR", false));
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                objectMapper, audits, port);

        String logs = captureLogs(PracticePdfAiOrchestrator.class, () ->
                assertThrows(PracticeAiContractException.class, () ->
                        orchestrator.generate(request(123L, SourceOperation.EXTRACT))));

        assertThat(logs).contains("sessionId=123");
        assertThat(logs).doesNotContain(
                "PRIVATE_PROVIDER_RESPONSE", "PRIVATE_PDF_DOCUMENT_TEXT",
                "SECRET_API_KEY_VALUE", "PRIVATE_LECTURER_REQUEST");
        ArgumentCaptor<PracticeAiRequestAudit> audit =
                ArgumentCaptor.forClass(PracticeAiRequestAudit.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getErrorCode()).isEqualTo("PROVIDER_HTTP_ERROR");
        assertThat(audit.getValue().getPayloadSummaryJson())
                .contains("PRACTICE_PDF_AUTHORING", "bindingRevision")
                .doesNotContain("PRIVATE_PDF_DOCUMENT_TEXT", "PRIVATE_LECTURER_REQUEST");
    }

    private PracticeStructuredGenerationPort availablePort() {
        PracticeStructuredGenerationPort port = mock(PracticeStructuredGenerationPort.class);
        when(port.identity(PracticeAiPurpose.PRACTICE_PDF_AUTHORING))
                .thenReturn(identity(4L));
        return port;
    }

    private static PracticeStructuredGenerationPort.ProviderIdentity identity(long revision) {
        return new PracticeStructuredGenerationPort.ProviderIdentity(
                "OPENAI_COMPATIBLE", "safe-model",
                PracticeModelCapabilityProfile.openAiAssessmentV1(), true,
                revision, 2L, "PRACTICE_PRIMARY");
    }

    private static PracticePdfAuthoringRequest request(
            Long sessionId,
            SourceOperation operation) {
        return request(sessionId, operation, "PRIVATE_LECTURER_REQUEST");
    }

    private static PracticePdfAuthoringRequest request(
            Long sessionId,
            SourceOperation operation,
            String lecturerRequest) {
        String source = "PRIVATE_PDF_DOCUMENT_TEXT";
        return new PracticePdfAuthoringRequest(
                sessionId == null ? PracticePdfAuthoringRequest.SourceType.TEXT
                        : PracticePdfAuthoringRequest.SourceType.PDF,
                operation,
                sessionId == null ? "pasted-text.txt" : "private.pdf",
                "sha256:" + "6".repeat(64),
                new TargetRoute(91L, 1, "READING", "R1"),
                lecturerRequest,
                List.of(new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "source-1", null, source.length(), source)),
                Map.of("trust", "UNTRUSTED_SOURCE_CONTENT", "text", source),
                List.of(),
                sessionId);
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
