package com.ksh.features.practice.ai.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeDirectAudioCapabilityRegistryTest {

    @Test
    void developerPresetCarriesImmutableCapabilityAndAuthEvidence() {
        var verification = PracticeDirectAudioCapabilityRegistry.assess(
                "https://generativelanguage.googleapis.com/v1beta/openai/",
                "gemini-3.6-flash");

        assertThat(verification.verified()).isTrue();
        assertThat(verification.code()).isEqualTo("GEMINI_DEVELOPER_DIRECT_AUDIO");
        assertThat(verification.credentialMode())
                .isEqualTo(PracticeAiCredentialMode.STATIC_BEARER);
        assertThat(verification.audioInputEvidenceId()).isNotBlank();
        assertThat(verification.strictStructuredOutputEvidenceId()).isNotBlank();
        assertThat(verification.authEndpointEvidenceId()).isNotBlank();
        assertThat(verification.runtimeAuthReady()).isTrue();
        assertThat(PracticeDirectAudioCapabilityRegistry.assess(
                "http://generativelanguage.googleapis.com/v1beta/openai",
                "gemini-3.6-flash").verified()).isFalse();
        assertThat(PracticeDirectAudioCapabilityRegistry.assess(
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "gemini-3.5-flash").verified()).isFalse();
    }

    @Test
    void enterpriseCandidateRequiresConcreteProjectLocationAndAdc() {
        var verification = PracticeDirectAudioCapabilityRegistry.assess(
                "https://asia-southeast1-aiplatform.googleapis.com/v1/projects/"
                        + "ksh-project/locations/asia-southeast1/endpoints/openapi",
                "gemini-3.5-flash");

        assertThat(verification.verified()).isTrue();
        assertThat(verification.code()).isEqualTo("GEMINI_ENTERPRISE_DIRECT_AUDIO");
        assertThat(verification.credentialMode())
                .isEqualTo(PracticeAiCredentialMode.GOOGLE_CLOUD_ADC);
        assertThat(verification.runtimeAuthReady()).isFalse();
        assertThat(PracticeDirectAudioCapabilityRegistry.assess(
                "https://aiplatform.googleapis.com/v1/projects/PROJECT_ID/"
                        + "locations/LOCATION/endpoints/openapi",
                "gemini-3.5-flash").verified()).isFalse();
    }

    @Test
    void customAndLookalikePairsRemainExplicitlyUnverified() {
        assertThat(PracticeDirectAudioCapabilityRegistry.assess(
                "https://provider.example/v1", "future-audio-model").state())
                .isEqualTo(PracticeDirectAudioCapabilityRegistry.State.UNVERIFIED);
        assertThat(PracticeDirectAudioCapabilityRegistry.assess(
                "https://evil.example/v1/projects/ksh/locations/global/endpoints/openapi",
                "gemini-3.5-flash").verified()).isFalse();
        assertThat(PracticeDirectAudioCapabilityRegistry.assess(
                "https://aiplatform.googleapis.com.evil.example/v1/projects/ksh/"
                        + "locations/global/endpoints/openapi",
                "gemini-3.5-flash").verified()).isFalse();
    }

    @Test
    void versionedArtifactMapsEveryRegistryEvidenceIdToOfficialSources()
            throws Exception {
        String artifact = Files.readString(Path.of(
                "docs/evidence/practice-direct-audio-capability-verification-v1.json"));

        assertThat(new ObjectMapper().readTree(artifact).path("presets")).hasSize(2);
        assertThat(artifact)
                .contains(PracticeDirectAudioCapabilityRegistry.REGISTRY_ARTIFACT_ID)
                .contains(PracticeDirectAudioCapabilityRegistry.DEVELOPER_AUDIO_INPUT_EVIDENCE)
                .contains(PracticeDirectAudioCapabilityRegistry.DEVELOPER_STRICT_OUTPUT_EVIDENCE)
                .contains(PracticeDirectAudioCapabilityRegistry.DEVELOPER_AUTH_ENDPOINT_EVIDENCE)
                .contains(PracticeDirectAudioCapabilityRegistry.ENTERPRISE_AUDIO_INPUT_EVIDENCE)
                .contains(PracticeDirectAudioCapabilityRegistry.ENTERPRISE_STRICT_OUTPUT_EVIDENCE)
                .contains(PracticeDirectAudioCapabilityRegistry.ENTERPRISE_AUTH_ENDPOINT_EVIDENCE)
                .contains("https://ai.google.dev/gemini-api/docs/openai")
                .contains("auth-and-credentials")
                .contains("\"policy_evidence_required_separately\": true")
                .contains("\"custom_model_state\": \"UNVERIFIED\"");
    }
}
