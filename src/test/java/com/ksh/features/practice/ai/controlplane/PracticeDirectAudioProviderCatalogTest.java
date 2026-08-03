package com.ksh.features.practice.ai.controlplane;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeDirectAudioProviderCatalogTest {

    @Test
    void developerCandidateRequiresExactSecureEndpointAndModel() {
        var candidate = PracticeDirectAudioProviderCatalog.match(
                "https://generativelanguage.googleapis.com/v1beta/openai/",
                "gemini-3.6-flash").orElseThrow();

        assertThat(candidate.code()).isEqualTo("GEMINI_DEVELOPER_DIRECT_AUDIO");
        assertThat(candidate.credentialMode())
                .isEqualTo(PracticeDirectAudioProviderCatalog.CredentialMode.STATIC_BEARER);
        assertThat(candidate.runtimeAuthReady()).isTrue();
        assertThat(PracticeDirectAudioProviderCatalog.match(
                "http://generativelanguage.googleapis.com/v1beta/openai",
                "gemini-3.6-flash")).isEmpty();
        assertThat(PracticeDirectAudioProviderCatalog.match(
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "gemini-3.5-flash")).isEmpty();
    }

    @Test
    void enterpriseCandidateRequiresConcreteProjectLocationAndAdc() {
        var candidate = PracticeDirectAudioProviderCatalog.match(
                "https://asia-southeast1-aiplatform.googleapis.com/v1/projects/"
                        + "ksh-project/locations/asia-southeast1/endpoints/openapi",
                "gemini-3.5-flash").orElseThrow();

        assertThat(candidate.code()).isEqualTo("GEMINI_ENTERPRISE_DIRECT_AUDIO");
        assertThat(candidate.credentialMode())
                .isEqualTo(PracticeDirectAudioProviderCatalog.CredentialMode.GOOGLE_CLOUD_ADC);
        assertThat(candidate.runtimeAuthReady()).isFalse();
        assertThat(PracticeDirectAudioProviderCatalog.match(
                "https://aiplatform.googleapis.com/v1/projects/PROJECT_ID/"
                        + "locations/LOCATION/endpoints/openapi",
                "gemini-3.5-flash")).isEmpty();
    }

    @Test
    void arbitraryGoogleOrLookalikeHostsAreNotCandidates() {
        assertThat(PracticeDirectAudioProviderCatalog.match(
                "https://evil.example/v1/projects/ksh/locations/global/endpoints/openapi",
                "gemini-3.5-flash")).isEmpty();
        assertThat(PracticeDirectAudioProviderCatalog.match(
                "https://aiplatform.googleapis.com.evil.example/v1/projects/ksh/"
                        + "locations/global/endpoints/openapi",
                "gemini-3.5-flash")).isEmpty();
    }
}
