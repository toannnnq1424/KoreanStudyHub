package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.PracticeModelCapabilityProfile;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationPort;
import com.ksh.features.practice.ai.transport.PracticeStructuredGenerationResponse;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAim8PdfBindingRevisionCompatibilityTest {

    @Test
    void enabledFirstPersistedRevisionZeroReachesOnlyItsExactPdfTransport()
            throws Exception {
        PracticeStructuredGenerationPort port = mock(PracticeStructuredGenerationPort.class);
        when(port.identity(PracticeAiPurpose.PRACTICE_PDF_AUTHORING))
                .thenReturn(identity(0L));
        when(port.generate(any())).thenReturn(
                new PracticeStructuredGenerationResponse(
                        new ObjectMapper().readTree("{}"),
                        "AIM8_FAKE_PROFILE",
                        "aim8-fake-model",
                        "stop",
                        "aim8-fake-request"));
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                new ObjectMapper(),
                mock(PracticeAiRequestAuditRepository.class),
                port);

        PracticePdfAiOrchestrator.GenerationResult result =
                orchestrator.generate(request());

        assertThat(result.aiExecution().path("bindingRevision").asLong()).isZero();
        assertThat(result.sourceRevision()).contains("-b0-");
        verify(port, times(1)).generate(any());
    }

    @Test
    void negativeBindingRevisionStillFailsClosedBeforeTransport() {
        PracticeStructuredGenerationPort port = mock(PracticeStructuredGenerationPort.class);
        when(port.identity(PracticeAiPurpose.PRACTICE_PDF_AUTHORING))
                .thenReturn(identity(-1L));
        PracticePdfAiOrchestrator orchestrator = new PracticePdfAiOrchestrator(
                new ObjectMapper(),
                mock(PracticeAiRequestAuditRepository.class),
                port);

        PracticeAiContractException failure = assertThrows(
                PracticeAiContractException.class,
                () -> orchestrator.generate(request()));

        assertThat(failure.category()).isEqualTo("PROVIDER_PURPOSE_UNAVAILABLE");
        verify(port, never()).generate(any());
    }

    private static PracticeStructuredGenerationPort.ProviderIdentity identity(
            long bindingRevision) {
        return new PracticeStructuredGenerationPort.ProviderIdentity(
                "OPENAI_COMPATIBLE",
                "aim8-fake-model",
                PracticeModelCapabilityProfile.openAiAssessmentV1(),
                true,
                bindingRevision,
                0L,
                "AIM8_FAKE_PROFILE");
    }

    private static PracticePdfAuthoringRequest request() {
        String source = "AIM8_FAKE_TEXT_ONLY_SOURCE";
        return new PracticePdfAuthoringRequest(
                PracticePdfAuthoringRequest.SourceType.TEXT,
                SourceOperation.GENERATE,
                "aim8-fake.txt",
                "sha256:" + "8".repeat(64),
                new TargetRoute(91L, 1, "READING", "R1"),
                "AIM8_FAKE_LECTURER_REQUIREMENT",
                List.of(new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "source-1", null, source.length(), source)),
                Map.of("trust", "UNTRUSTED_SOURCE_CONTENT", "text", source),
                List.of(),
                null);
    }
}
