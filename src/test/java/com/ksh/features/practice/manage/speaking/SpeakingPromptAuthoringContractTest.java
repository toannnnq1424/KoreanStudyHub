package com.ksh.features.practice.manage.speaking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
import java.text.Normalizer;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingPromptAuthoringContractTest {

    @Test
    void disabledDefaultsAreBoundedProviderNeutralAndSecretSafe() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();

        properties.afterPropertiesSet();

        assertThat(properties.sttConfig().enabled()).isFalse();
        assertThat(properties.ttsConfig().enabled()).isFalse();
        assertThat(properties.sttConfig().allowedMimeTypes()).contains(
                "audio/mpeg", "audio/wav", "audio/mp4", "audio/ogg", "audio/webm");
        assertThat(properties.ttsConfig().allowedOutputFormats()).containsExactlyInAnyOrder(
                "mp3", "wav");
        assertThat(properties.isWorkerEnabled()).isFalse();
        assertThat(properties.taskBounds().leaseDuration()).isEqualTo(Duration.ofMinutes(3));
        assertThat(properties.taskBounds().maxActiveTasksPerLecturer()).isEqualTo(4);
        assertThat(properties.taskBounds().maxActiveTasksPerDraft()).isEqualTo(2);
        assertThat(properties.taskBounds().maxRequestsPerLecturerPerHour()).isEqualTo(20);
        assertThat(properties.taskBounds().manualRetryCooldown())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.sttConfig().toString()).contains("apiKeyPresent=false");
        assertThat(properties.ttsConfig().toString()).contains("apiKeyPresent=false");
    }

    @Test
    void enqueueOperationalGateFailsClosedUntilWorkerAndProviderAreReady() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();

        assertThatThrownBy(() -> properties.requireOperational(
                SpeakingPromptAiContract.Operation.STT))
                .isInstanceOf(SpeakingPromptAiContract.ProviderFailure.class)
                .satisfies(failure -> assertThat(
                        ((SpeakingPromptAiContract.ProviderFailure) failure)
                                .publicCategory())
                        .isEqualTo(
                                SpeakingPromptAiContract.PublicErrorCategory
                                        .CONFIGURATION));

        configureOpenAi(properties);
        assertThatThrownBy(() -> properties.requireOperational(
                SpeakingPromptAiContract.Operation.TTS))
                .isInstanceOf(SpeakingPromptAiContract.ProviderFailure.class);

        properties.setWorkerEnabled(true);
        properties.requireOperational(SpeakingPromptAiContract.Operation.STT);
        properties.requireOperational(SpeakingPromptAiContract.Operation.TTS);
    }

    @Test
    void enabledButIncompleteProviderStillMapsToUnavailableBeforeEnqueue() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        properties.setWorkerEnabled(true);
        properties.getStt().setEnabled(true);
        properties.getStt().setProvider("openai");

        properties.afterPropertiesSet();

        assertThatThrownBy(() -> properties.requireOperational(
                SpeakingPromptAiContract.Operation.STT))
                .isInstanceOf(SpeakingPromptAiContract.ProviderFailure.class)
                .satisfies(failure -> {
                    SpeakingPromptAiContract.ProviderFailure unavailable =
                            (SpeakingPromptAiContract.ProviderFailure) failure;
                    assertThat(unavailable.publicCategory()).isEqualTo(
                            SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION);
                    assertThat(unavailable.retryable()).isFalse();
                });
    }

    @Test
    void leaseMustStrictlyExceedOneProviderCallEnvelopeAndSafetyMargin() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        properties.setLeaseDuration(Duration.ofSeconds(125));

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "lease must exceed the maximum single-call timeout envelope");

        properties.setLeaseDuration(Duration.ofSeconds(126));
        properties.afterPropertiesSet();
    }

    @Test
    void byteDurationRetryAndSpeedBoundsFailClosed() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        properties.getStt().setMaxInputBytes(
                SpeakingPromptAuthoringAiProperties.MAX_AUTHORING_AUDIO_BYTES + 1);

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STT max input bytes");

        SpeakingPromptAuthoringAiProperties invalidSpeed =
                new SpeakingPromptAuthoringAiProperties();
        invalidSpeed.getTts().setSpeed(new BigDecimal("4.01"));
        assertThatThrownBy(invalidSpeed::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTS speed");
    }

    @Test
    void promptContractsPreserveTextAndKeepPromptSeparateFromLearnerEvidence() {
        String prompt = "  질문을 듣고 답하세요.\n두 문장을 말하세요.  ";
        String hash = SpeakingPromptAiContract.unicodeNfcUtf8Sha256(prompt);
        SpeakingPromptAiContract.TtsRequest request =
                new SpeakingPromptAiContract.TtsRequest(
                        prompt,
                        hash,
                        "ko",
                        "default",
                        BigDecimal.ONE,
                        "mp3",
                        SpeakingPromptAiContract.CONTRACT_VERSION);

        assertThat(request.promptText()).isEqualTo(prompt);
        assertThat(request.toString())
                .doesNotContain(prompt)
                .contains("promptTextLength=");
        assertThatThrownBy(() -> new SpeakingPromptAiContract.TtsRequest(
                prompt,
                "a".repeat(64),
                "ko",
                "default",
                BigDecimal.ONE,
                "mp3",
                SpeakingPromptAiContract.CONTRACT_VERSION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match Unicode-NFC UTF-8");
        String nfc = Normalizer.normalize("가", Normalizer.Form.NFC);
        String nfd = Normalizer.normalize("가", Normalizer.Form.NFD);
        assertThat(SpeakingPromptAiContract.unicodeNfcUtf8Sha256(nfc))
                .isEqualTo(SpeakingPromptAiContract.unicodeNfcUtf8Sha256(nfd));
        assertThat(SpeakingPromptAiContract.class.getDeclaredClasses())
                .extracting(Class::getSimpleName)
                .doesNotContain("LearnerAnswer", "AcousticEvidence");
    }

    @Test
    void fingerprintsUseExactVerifiedAudioAndOnlyNfcForOriginalPromptText() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        configureOpenAi(properties);
        SpeakingPromptFingerprintService fingerprints =
                new SpeakingPromptFingerprintService();

        String firstAudioHash = SpeakingPromptAiContract.exactBytesSha256(
                "audio-one".getBytes(StandardCharsets.UTF_8));
        String secondAudioHash = SpeakingPromptAiContract.exactBytesSha256(
                "audio-two".getBytes(StandardCharsets.UTF_8));
        String firstStt = fingerprints.sttFingerprint(
                41L, 71L, firstAudioHash, properties.sttConfig());
        assertThat(firstStt)
                .isEqualTo(fingerprints.sttFingerprint(
                        41L, 71L, firstAudioHash, properties.sttConfig()))
                .isNotEqualTo(fingerprints.sttFingerprint(
                        41L, 72L, firstAudioHash, properties.sttConfig()))
                .isNotEqualTo(fingerprints.sttFingerprint(
                        41L, 71L, secondAudioHash, properties.sttConfig()))
                .isNotEqualTo(fingerprints.sttFingerprint(
                        42L, 71L, firstAudioHash, properties.sttConfig()));

        String composed = "  가? 한 글자!\n";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        String firstTts = fingerprints.ttsFingerprint(
                41L, composed, properties.ttsConfig());
        assertThat(firstTts)
                .isEqualTo(fingerprints.ttsFingerprint(
                        41L, decomposed, properties.ttsConfig()))
                .isNotEqualTo(fingerprints.ttsFingerprint(
                        41L, composed.trim(), properties.ttsConfig()))
                .isNotEqualTo(fingerprints.ttsFingerprint(
                        41L, "  각? 한 글자!\n", properties.ttsConfig()))
                .isNotEqualTo(fingerprints.ttsFingerprint(
                        42L, composed, properties.ttsConfig()));
    }

    @Test
    void fingerprintsTreatPurposeAndRetentionAsProviderPolicyIdentity() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        configureOpenAi(properties);
        SpeakingPromptFingerprintService fingerprints =
                new SpeakingPromptFingerprintService();
        String audioHash = SpeakingPromptAiContract.exactBytesSha256(
                "policy-bound-audio".getBytes(StandardCharsets.UTF_8));
        String prompt = "정책이 적용되는 발음";

        String baselineStt = fingerprints.sttFingerprint(
                41L, 71L, audioHash, properties.sttConfig());
        String baselineTts = fingerprints.ttsFingerprint(
                41L, prompt, properties.ttsConfig());
        String sttPurpose = properties.sttConfig().purposeCode();
        String sttRetention = properties.sttConfig().retentionCode();
        String ttsPurpose = properties.ttsConfig().purposeCode();
        String ttsRetention = properties.ttsConfig().retentionCode();

        properties.getStt().setPurposeCode(sttPurpose + "_v2");
        assertThat(fingerprints.sttFingerprint(
                41L, 71L, audioHash, properties.sttConfig()))
                .isNotEqualTo(baselineStt);
        properties.getStt().setPurposeCode(sttPurpose);
        properties.getStt().setRetentionCode(sttRetention + "_v2");
        assertThat(fingerprints.sttFingerprint(
                41L, 71L, audioHash, properties.sttConfig()))
                .isNotEqualTo(baselineStt);

        properties.getTts().setPurposeCode(ttsPurpose + "_v2");
        assertThat(fingerprints.ttsFingerprint(
                41L, prompt, properties.ttsConfig()))
                .isNotEqualTo(baselineTts);
        properties.getTts().setPurposeCode(ttsPurpose);
        properties.getTts().setRetentionCode(ttsRetention + "_v2");
        assertThat(fingerprints.ttsFingerprint(
                41L, prompt, properties.ttsConfig()))
                .isNotEqualTo(baselineTts);
    }

    @Test
    void privatePayloadTypesAndStringRepresentationsDoNotExposeSecrets() {
        SpeakingPromptAuthoringAiProperties configured =
                new SpeakingPromptAuthoringAiProperties();
        configureOpenAi(configured);
        String privatePrompt = "비공개 전체 프롬프트";
        String promptHash =
                SpeakingPromptAiContract.unicodeNfcUtf8Sha256(privatePrompt);
        SpeakingPromptAiContract.TtsRequest request =
                new SpeakingPromptAiContract.TtsRequest(
                        privatePrompt,
                        promptHash,
                        "ko",
                        "alloy",
                        BigDecimal.ONE,
                        "mp3",
                        SpeakingPromptAiContract.CONTRACT_VERSION);
        SpeakingPromptAiContract.SttResult result =
                new SpeakingPromptAiContract.SttResult(
                        "비공개 전체 전사",
                        new BigDecimal("0.91"),
                        "openai",
                        "transcribe-model",
                        "ko",
                        "provider-request-secret",
                        "speaking_prompt_stt",
                        "provider_default");

        assertThat(Modifier.isPublic(
                SpeakingPromptAiContract.TtsRequest.class.getModifiers()))
                .isFalse();
        assertThat(Modifier.isPublic(
                SpeakingPromptAiContract.VerifiedAudio.class.getModifiers()))
                .isFalse();
        assertThat(request.toString())
                .doesNotContain(privatePrompt)
                .doesNotContain(promptHash);
        assertThat(result.toString())
                .doesNotContain("비공개 전체 전사")
                .doesNotContain("provider-request-secret");
        assertThat(new SpeakingPromptAiContract.ProviderFailure(
                SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                true,
                "provider-request-secret",
                null).toString())
                .doesNotContain("provider-request-secret");
        assertThat(configured.sttConfig().toString())
                .doesNotContain("stt-secret")
                .doesNotContain("https://provider.invalid");
        assertThat(configured.ttsConfig().toString())
                .doesNotContain("tts-secret")
                .doesNotContain("https://provider.invalid");
    }

    private static void configureOpenAi(
            SpeakingPromptAuthoringAiProperties properties) {
        properties.getStt().setEnabled(true);
        properties.getStt().setProvider("openai");
        properties.getStt().setBaseUrl("https://provider.invalid");
        properties.getStt().setApiKey("stt-secret");
        properties.getStt().setModel("transcribe-model");
        properties.getTts().setEnabled(true);
        properties.getTts().setProvider("openai");
        properties.getTts().setBaseUrl("https://provider.invalid");
        properties.getTts().setApiKey("tts-secret");
        properties.getTts().setModel("speech-model");
        properties.getTts().setVoice("alloy");
    }
}
