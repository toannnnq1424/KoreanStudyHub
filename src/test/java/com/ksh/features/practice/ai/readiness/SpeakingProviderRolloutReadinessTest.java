package com.ksh.features.practice.ai.readiness;

import com.ksh.features.practice.ai.speaking.SpeakingEvaluatorProperties;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingProviderRolloutReadinessTest {

    @Test
    void disabledDefaultsBlockLiveRolloutWithoutCallingProvider() {
        var readiness = new SpeakingProviderRolloutReadiness(
                transcription(false, "openai", ""),
                evaluator(false, "openai-compatible", ""));

        AiReadinessReport report = readiness.assessLiveSpeakingProviderReadiness();

        assertThat(report.rolloutAllowed()).isFalse();
        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("SPEAKING_AI_GATES_DISABLED");
        assertThat(render(report)).doesNotContain("SECRET", "Bearer", "Authorization");
    }

    @Test
    void bothGatesAndAllowedProvidersAndKeysPassProviderGateOnly() {
        var readiness = new SpeakingProviderRolloutReadiness(
                transcription(true, "openai", "TRANSCRIPTION_SECRET"),
                evaluator(true, "openai-compatible", "EVALUATOR_SECRET"));

        AiReadinessReport report = readiness.assessLiveSpeakingProviderReadiness();

        assertThat(report.blockers()).isEmpty();
        assertThat(report.rolloutAllowed()).isTrue();
        assertThat(report.issues())
                .extracting(AiReadinessIssue::code)
                .contains("LIVE_PROVIDER_PATH_CONFIGURED");
        assertThat(render(report)).doesNotContain("TRANSCRIPTION_SECRET", "EVALUATOR_SECRET");
    }

    @Test
    void rejectsNewProvidersIn8FA() {
        var readiness = new SpeakingProviderRolloutReadiness(
                transcription(true, "deepgram", "TRANSCRIPTION_SECRET"),
                evaluator(true, "native-gemini", "EVALUATOR_SECRET"));

        AiReadinessReport report = readiness.assessLiveSpeakingProviderReadiness();

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("UNSUPPORTED_SPEAKING_TRANSCRIPTION_PROVIDER",
                        "UNSUPPORTED_SPEAKING_EVALUATOR_PROVIDER");
    }

    @Test
    void enabledGateRequiresMatchingApiKey() {
        var readiness = new SpeakingProviderRolloutReadiness(
                transcription(true, "openai", ""),
                evaluator(false, "openai-compatible", ""));

        AiReadinessReport report = readiness.assessLiveSpeakingProviderReadiness();

        assertThat(report.blockers())
                .extracting(AiReadinessIssue::code)
                .contains("SPEAKING_AI_GATE_MISMATCH", "MISSING_TRANSCRIPTION_API_KEY");
    }

    private static SpeakingTranscriptionProperties transcription(boolean enabled, String provider, String apiKey) {
        return new SpeakingTranscriptionProperties(
                enabled,
                provider,
                "https://api.openai.com/v1",
                apiKey,
                "gpt-4o-mini-transcribe",
                "ko",
                25_000_000L,
                Duration.ofSeconds(30),
                2,
                true,
                "audio/webm,audio/mp4");
    }

    private static SpeakingEvaluatorProperties evaluator(boolean enabled, String provider, String apiKey) {
        return new SpeakingEvaluatorProperties(
                enabled,
                provider,
                "https://generativelanguage.googleapis.com/v1beta/openai",
                apiKey,
                "models/gemini-2.5-flash",
                Duration.ofSeconds(30),
                2,
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1");
    }

    private static String render(Object value) {
        return String.valueOf(value);
    }
}
