package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.assessment.QuestionContent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptEvaluationContextServiceTest {

    @Test
    void v2ResolvesOnlyExactImmutableQuestionVersionContext() {
        SpeakingPromptVersionContextRepository repository =
                mock(SpeakingPromptVersionContextRepository.class);
        SpeakingPromptVersionContext context =
                SpeakingPromptVersionContext.create(
                        101L,
                        manualContext("자기소개를 하세요."),
                        20L);
        when(repository.findById(101L)).thenReturn(Optional.of(context));

        SpeakingPromptEvaluationContextService.EvaluatorContext resolved =
                new SpeakingPromptEvaluationContextService(repository)
                        .resolve(
                                101L,
                                QuestionContent.SCHEMA_VERSION_V2,
                                "MUTABLE_SOURCE_MUST_NOT_BE_USED");

        assertThat(resolved.questionVersionId()).isEqualTo(101L);
        assertThat(resolved.promptContext())
                .isEqualTo("자기소개를 하세요.");
        assertThat(resolved.promptContextFingerprint())
                .isEqualTo(context.getPromptContextFingerprint());
        assertThat(resolved.promptContextContractIdentity())
                .isEqualTo(
                        SpeakingPromptContextIdentity.CONTRACT_IDENTITY);
    }

    @Test
    void missingV2ContextFailsClosedWithoutLegacyBackfill() {
        SpeakingPromptVersionContextRepository repository =
                mock(SpeakingPromptVersionContextRepository.class);
        when(repository.findById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                new SpeakingPromptEvaluationContextService(repository)
                        .resolve(
                                101L,
                                QuestionContent.SCHEMA_VERSION_V2,
                                "질문입니다."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thiếu ngữ cảnh đề bất biến");
    }

    @Test
    void v3LanguageAuthorityUsesTheSameImmutableVersionContext() {
        SpeakingPromptVersionContextRepository repository =
                mock(SpeakingPromptVersionContextRepository.class);
        SpeakingPromptVersionContext context =
                SpeakingPromptVersionContext.create(
                        102L,
                        manualContext("주말에 무엇을 합니까?"),
                        20L);
        when(repository.findById(102L)).thenReturn(Optional.of(context));

        SpeakingPromptEvaluationContextService.EvaluatorContext resolved =
                new SpeakingPromptEvaluationContextService(repository)
                        .resolve(
                                102L,
                                QuestionContent.SCHEMA_VERSION_V3,
                                "MUTABLE_SOURCE_MUST_NOT_BE_USED");

        assertThat(resolved.promptContext())
                .isEqualTo("주말에 무엇을 합니까?");
        assertThat(resolved.promptContextContractIdentity())
                .isEqualTo(
                        SpeakingPromptContextIdentity.CONTRACT_IDENTITY);
    }

    @Test
    void historicalV1UsesOnlyImmutableVersionPromptAndNeverBackfills() {
        SpeakingPromptVersionContextRepository repository =
                mock(SpeakingPromptVersionContextRepository.class);
        SpeakingPromptEvaluationContextService service =
                new SpeakingPromptEvaluationContextService(repository);

        SpeakingPromptEvaluationContextService.EvaluatorContext first =
                service.resolve(
                        77L,
                        QuestionContent.SCHEMA_VERSION_V1,
                        "기존 질문");
        SpeakingPromptEvaluationContextService.EvaluatorContext second =
                service.resolve(
                        77L,
                        QuestionContent.SCHEMA_VERSION_V1,
                        "기존 질문");

        assertThat(first).isEqualTo(second);
        assertThat(first.promptContext()).isEqualTo("기존 질문");
        assertThat(first.promptContextContractIdentity())
                .isEqualTo(
                        SpeakingPromptEvaluationContextService
                                .LEGACY_CONTRACT_IDENTITY);
        verify(repository, never()).findById(
                org.mockito.ArgumentMatchers.anyLong());
        verify(repository, never()).save(
                org.mockito.ArgumentMatchers.any());
    }

    private static SpeakingPromptVersionContext.ImmutableData manualContext(
            String text) {
        return new SpeakingPromptVersionContext.ImmutableData(
                20L,
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
