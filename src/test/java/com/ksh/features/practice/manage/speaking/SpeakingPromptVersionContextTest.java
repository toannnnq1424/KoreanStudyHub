package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.assessment.QuestionContent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpeakingPromptVersionContextTest {

    @Test
    void exactContextIntegrityRejectsTextOrFingerprintMismatch() {
        SpeakingPromptVersionContext.ImmutableData exact =
                manualContext("자기소개를 하세요.");
        SpeakingPromptVersionContext stored =
                SpeakingPromptVersionContext.create(101L, exact, 7L);

        assertThat(stored.getQuestionVersionId()).isEqualTo(101L);
        assertThat(stored.getPromptContextText())
                .isEqualTo("자기소개를 하세요.");
        stored.verifyIntegrity();

        SpeakingPromptVersionContext.ImmutableData tampered =
                new SpeakingPromptVersionContext.ImmutableData(
                        exact.ownerLecturerId(),
                        exact.inputType(),
                        exact.deliveryMode(),
                        exact.audioOrigin(),
                        exact.promptContextSource(),
                        "다른 질문",
                        exact.promptContextSha256(),
                        exact.promptContextFingerprint(),
                        exact.originalAudioAssetId(),
                        exact.activeAudioAssetId(),
                        exact.sttArtifactId(),
                        exact.ttsArtifactId(),
                        exact.sttProviderCode(),
                        exact.sttModelCode(),
                        exact.sttContractVersion(),
                        exact.sttPurposeCode(),
                        exact.sttRetentionCode(),
                        exact.ttsProviderCode(),
                        exact.ttsModelCode(),
                        exact.ttsContractVersion(),
                        exact.ttsPurposeCode(),
                        exact.ttsRetentionCode());

        assertThatThrownBy(() -> SpeakingPromptVersionContext.create(
                102L, tampered, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã băm");
    }

    @Test
    void republishAndOldAttemptsResolveTheirOwnQuestionVersionContexts() {
        SpeakingPromptVersionContextRepository repository =
                mock(SpeakingPromptVersionContextRepository.class);
        SpeakingPromptVersionContext oldContext =
                SpeakingPromptVersionContext.create(
                        101L, manualContext("이전 질문"), 7L);
        SpeakingPromptVersionContext republishedContext =
                SpeakingPromptVersionContext.create(
                        202L, manualContext("새 질문"), 7L);
        when(repository.findById(101L)).thenReturn(Optional.of(oldContext));
        when(repository.findById(202L))
                .thenReturn(Optional.of(republishedContext));
        SpeakingPromptEvaluationContextService service =
                new SpeakingPromptEvaluationContextService(repository);

        SpeakingPromptEvaluationContextService.EvaluatorContext oldAttempt =
                service.resolve(
                        101L, QuestionContent.SCHEMA_VERSION_V2, "");
        SpeakingPromptEvaluationContextService.EvaluatorContext newAttempt =
                service.resolve(
                        202L, QuestionContent.SCHEMA_VERSION_V2, "");

        assertThat(oldAttempt.promptContext()).isEqualTo("이전 질문");
        assertThat(newAttempt.promptContext()).isEqualTo("새 질문");
        assertThat(oldAttempt.promptContextFingerprint())
                .isNotEqualTo(newAttempt.promptContextFingerprint());
    }

    @Test
    void v2NeverFallsBackToMutableOrQuestionPromptState() {
        SpeakingPromptVersionContextRepository repository =
                mock(SpeakingPromptVersionContextRepository.class);
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                new SpeakingPromptEvaluationContextService(repository)
                        .resolve(
                                404L,
                                QuestionContent.SCHEMA_VERSION_V2,
                                "mutable text must not be used"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thiếu ngữ cảnh đề bất biến");
    }

    @Test
    void historicalV1UsesOnlyImmutableVersionPromptWithoutBackfill() {
        SpeakingPromptVersionContextRepository repository =
                mock(SpeakingPromptVersionContextRepository.class);
        SpeakingPromptEvaluationContextService service =
                new SpeakingPromptEvaluationContextService(repository);

        SpeakingPromptEvaluationContextService.EvaluatorContext context =
                service.resolve(
                        55L,
                        QuestionContent.SCHEMA_VERSION_V1,
                        "기존 버전 질문");

        assertThat(context.promptContext()).isEqualTo("기존 버전 질문");
        assertThat(context.promptContextContractIdentity())
                .isEqualTo(SpeakingPromptEvaluationContextService
                        .LEGACY_CONTRACT_IDENTITY);
        verifyNoInteractions(repository);
    }

    private static SpeakingPromptVersionContext.ImmutableData manualContext(
            String text) {
        return new SpeakingPromptVersionContext.ImmutableData(
                7L,
                "manual_text",
                "text_only",
                "none",
                "manual_text",
                text,
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null).withFingerprint();
    }
}
