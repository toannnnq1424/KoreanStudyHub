package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.CapabilityTestResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeAiControlPlaneContractTest {

    private final PracticeAiControlPlaneCodec codec =
            new PracticeAiControlPlaneCodec(new ObjectMapper());

    @Test
    void exactSixPurposesHaveIndependentCapabilityContracts() {
        assertThat(PracticeAiPurpose.values()).extracting(Enum::name)
                .containsExactly(
                        "PRACTICE_PDF_AUTHORING",
                        "PRACTICE_RL_EXPLANATION",
                        "PRACTICE_WRITING_EVALUATION",
                        "PRACTICE_SPEAKING_EVALUATION",
                        "PRACTICE_SPEAKING_STT",
                        "PRACTICE_SPEAKING_TTS");
        for (PracticeAiPurpose purpose : PracticeAiPurpose.values()) {
            PracticeAiCapabilitySet capabilities = codec.parseCapabilities(
                    purpose, codec.capabilityJson(purpose, false));
            assertThat(capabilities.enabledCodes())
                    .containsAll(purpose.requiredCapabilities());
        }
        assertThat(codec.parseCapabilities(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING,
                codec.capabilityJson(
                        PracticeAiPurpose.PRACTICE_PDF_AUTHORING, false))
                .imageInput()).isFalse();
    }

    @Test
    void incompatiblePurposeCapabilityFailsClosed() {
        String onlyJson = codec.capabilityJson(
                PracticeAiPurpose.PRACTICE_PDF_AUTHORING, false);
        assertThatThrownBy(() -> codec.parseCapabilities(
                PracticeAiPurpose.PRACTICE_SPEAKING_STT, onlyJson))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error).errorCode())
                .isEqualTo("PROVIDER_CAPABILITY_INCOMPATIBLE");
    }

    @Test
    void missingDisabledAndUnknownPurposeAuthorityFailClosed() {
        PracticeAiPurposeBindingRepository repository =
                mock(PracticeAiPurposeBindingRepository.class);
        PracticeAiBindingResolver resolver = new PracticeAiBindingResolver(repository, codec);
        assertThatThrownBy(() -> resolver.resolve(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error).errorCode())
                .isEqualTo("PROVIDER_PURPOSE_UNAVAILABLE");

        PracticeAiProviderProfile disabled = new PracticeAiProviderProfile(
                "PRACTICE_PRIMARY", "Primary", "OPENAI_COMPATIBLE",
                "https://provider.invalid/v1", "TOP_SECRET", false, 1L);
        PracticeAiPurposeBinding binding = binding(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION, disabled);
        when(repository.findDetailed(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION.name()))
                .thenReturn(Optional.of(binding));
        assertThat(resolver.availableSnapshot(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION)).isEmpty();
    }

    @Test
    void resolutionReturnsImmutableRedactedRevisionSnapshot() {
        PracticeAiPurposeBindingRepository repository =
                mock(PracticeAiPurposeBindingRepository.class);
        PracticeAiProviderProfile profile = new PracticeAiProviderProfile(
                "PRACTICE_PRIMARY", "Primary", "OPENAI_COMPATIBLE",
                "https://provider.invalid/v1/", "TOP_SECRET", true, 1L);
        PracticeAiPurposeBinding binding = binding(
                PracticeAiPurpose.PRACTICE_SPEAKING_EVALUATION, profile);
        when(repository.findDetailed(
                PracticeAiPurpose.PRACTICE_SPEAKING_EVALUATION.name()))
                .thenReturn(Optional.of(binding));
        PracticeAiResolvedBinding resolved =
                new PracticeAiBindingResolver(repository, codec).resolve(
                        PracticeAiPurpose.PRACTICE_SPEAKING_EVALUATION);

        assertThat(resolved.snapshot().purpose())
                .isEqualTo(PracticeAiPurpose.PRACTICE_SPEAKING_EVALUATION);
        assertThat(resolved.snapshot().bindingRevision()).isZero();
        assertThat(resolved.snapshot().providerProfileRevision()).isZero();
        assertThat(resolved.snapshot().providerProfileCode())
                .isEqualTo("PRACTICE_PRIMARY");
        assertThat(resolved.toString()).doesNotContain("TOP_SECRET");
        assertThat(resolved.baseUrl()).isEqualTo(URI.create("https://provider.invalid/v1/"));
    }

    @Test
    void capabilityTestUsesInjectedFakeAndPersistsRevisionAudit() {
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiCapabilityProbe probe = mock(PracticeAiCapabilityProbe.class);
        PracticeAiCapabilityTestRunRepository repository =
                mock(PracticeAiCapabilityTestRunRepository.class);
        PracticeAiResolvedBinding binding = resolved(
                PracticeAiPurpose.PRACTICE_SPEAKING_TTS);
        when(resolver.resolve(PracticeAiPurpose.PRACTICE_SPEAKING_TTS))
                .thenReturn(binding);
        doNothing().when(resolver).assertCurrent(binding.snapshot());
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        CapabilityTestResult result = new PracticeAiCapabilityTestService(
                resolver, probe, repository).test(
                        PracticeAiPurpose.PRACTICE_SPEAKING_TTS, 17L);

        assertThat(result.ok()).isTrue();
        assertThat(result.bindingRevision()).isEqualTo(7L);
        verify(probe).probe(binding);
        verify(repository, org.mockito.Mockito.times(2)).saveAndFlush(any());
    }

    @Test
    void missingBindingNeverTouchesCapabilityProbe() {
        PracticeAiBindingResolver resolver = mock(PracticeAiBindingResolver.class);
        PracticeAiCapabilityProbe probe = mock(PracticeAiCapabilityProbe.class);
        PracticeAiCapabilityTestRunRepository repository =
                mock(PracticeAiCapabilityTestRunRepository.class);
        when(resolver.resolve(any())).thenThrow(new PracticeAiControlPlaneException(
                "PROVIDER_PURPOSE_UNAVAILABLE", false));
        PracticeAiCapabilityTestService service = new PracticeAiCapabilityTestService(
                resolver, probe, repository);

        assertThatThrownBy(() -> service.test(
                PracticeAiPurpose.PRACTICE_SPEAKING_STT, 17L))
                .isInstanceOf(PracticeAiControlPlaneException.class);
        verify(probe, never()).probe(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void centralTransportRejectsUnknownPathsAndOversizedJsonBeforeNetwork() {
        RestClientPracticeAiProviderTransport transport =
                new RestClientPracticeAiProviderTransport(new ObjectMapper());
        PracticeAiResolvedBinding binding = resolved(
                PracticeAiPurpose.PRACTICE_WRITING_EVALUATION);

        assertThatThrownBy(() -> transport.exchange(
                binding,
                "/models",
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_JSON,
                "{}",
                java.util.Map.of()))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error)
                        .errorCode())
                .isEqualTo("PROVIDER_TRANSPORT_PATH_INVALID");
        assertThatThrownBy(() -> transport.exchange(
                binding,
                "/chat/completions",
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_JSON,
                "x".repeat(1_025),
                java.util.Map.of()))
                .isInstanceOf(PracticeAiControlPlaneException.class)
                .extracting(error -> ((PracticeAiControlPlaneException) error)
                        .errorCode())
                .isEqualTo("PROVIDER_REQUEST_TOO_LARGE");
    }

    private PracticeAiPurposeBinding binding(
            PracticeAiPurpose purpose,
            PracticeAiProviderProfile profile) {
        return new PracticeAiPurposeBinding(
                purpose,
                profile,
                "purpose-model",
                "OPENAI_COMPATIBLE_V1",
                codec.capabilityJson(purpose, true),
                codec.limitsJson(5_000, 60_000, 2, 8_388_608, 2_097_152),
                "PURPOSE_RETENTION_V1",
                true,
                1L);
    }

    private static PracticeAiResolvedBinding resolved(PracticeAiPurpose purpose) {
        PracticeAiCapabilitySet capabilities = switch (purpose) {
            case PRACTICE_SPEAKING_TTS ->
                    new PracticeAiCapabilitySet(false, false, false, false, true);
            default -> new PracticeAiCapabilitySet(true, true, true, false, false);
        };
        return new PracticeAiResolvedBinding(
                new PracticeAiExecutionSnapshot(
                        purpose,
                        7,
                        3,
                        "OPENAI_COMPATIBLE",
                        "PRACTICE_PRIMARY",
                        "purpose-model",
                        "OPENAI_COMPATIBLE_V1",
                        capabilities,
                        new PracticeAiLimits(5_000, 60_000, 1, 1024, 16_384),
                        "a".repeat(64),
                        "b".repeat(64),
                        "PURPOSE_RETENTION_V1"),
                URI.create("https://provider.invalid/v1"),
                "TOP_SECRET");
    }
}
