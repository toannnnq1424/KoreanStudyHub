package com.ksh.features.practice.assessment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingPromptDeliveryPresenterTest {
    private final SpeakingPromptDeliveryPresenter presenter =
            new SpeakingPromptDeliveryPresenter();

    @Test
    void v2AudioOnlyPublishesPlaybackPreparationRecordingWithoutTranscript() {
        SpeakingPromptDelivery delivery = presenter.present(
                content(
                        QuestionContent.SpeakingPromptInputType.AUDIO_UPLOAD,
                        QuestionContent.SpeakingDeliveryMode.AUDIO_ONLY,
                        QuestionContent.SpeakingAudioOrigin.TEACHER_UPLOAD,
                        "/practice/materials/7/content",
                        2),
                "이 문장은 강사 전사이며 학습자에게 노출되면 안 됩니다.",
                null);

        assertThat(delivery.promptText()).isNull();
        assertThat(delivery.promptAudioReference())
                .isEqualTo("/practice/materials/7/content");
        assertThat(delivery.promptVisibleBeforePlayback()).isFalse();
        assertThat(delivery.promptVisibleAfterPlayback()).isFalse();
        assertThat(delivery.steps()).containsExactly(
                SpeakingPromptDelivery.Step.PROMPT_PLAYBACK,
                SpeakingPromptDelivery.Step.PREPARATION,
                SpeakingPromptDelivery.Step.RECORDING);
    }

    @Test
    void v2TextAndAudioShowsExactTextAndUsesImmutableTtsAudio() {
        SpeakingPromptDelivery delivery = presenter.present(
                content(
                        QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                        QuestionContent.SpeakingDeliveryMode.TEXT_AND_AUDIO,
                        QuestionContent.SpeakingAudioOrigin.AI_TTS,
                        "/practice/materials/8/content",
                        1),
                "자기소개를 하세요.",
                null);

        assertThat(delivery.promptText()).isEqualTo("자기소개를 하세요.");
        assertThat(delivery.promptVisibleBeforePlayback()).isTrue();
        assertThat(delivery.promptVisibleAfterPlayback()).isTrue();
        assertThat(delivery.steps()).startsWith(
                SpeakingPromptDelivery.Step.PROMPT_PLAYBACK);
    }

    @Test
    void v2TextOnlyHasNoInventedPlaybackOrPlayLimit() {
        SpeakingPromptDelivery delivery = presenter.present(
                content(
                        QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                        QuestionContent.SpeakingDeliveryMode.TEXT_ONLY,
                        QuestionContent.SpeakingAudioOrigin.NONE,
                        null,
                        null),
                "주말에 무엇을 합니까?",
                null);

        assertThat(delivery.promptText()).isEqualTo("주말에 무엇을 합니까?");
        assertThat(delivery.promptAudioReference()).isNull();
        assertThat(delivery.promptPlayLimit()).isNull();
        assertThat(delivery.steps()).containsExactly(
                SpeakingPromptDelivery.Step.PREPARATION,
                SpeakingPromptDelivery.Step.RECORDING);
    }

    @Test
    void historicalV1KeepsAudioFirstDualReadBehavior() {
        QuestionContent v1 = new QuestionContent(
                QuestionContent.SCHEMA_VERSION_V1,
                List.of(),
                List.of(),
                null,
                null,
                new QuestionContent.SpeakingDelivery(
                        "/practice/materials/5/content", 1, 30, 60));

        SpeakingPromptDelivery delivery = presenter.present(
                v1, "기존 공개 문구", null);

        assertThat(delivery.contentSchemaVersion())
                .isEqualTo(QuestionContent.SCHEMA_VERSION_V1);
        assertThat(delivery.steps().get(0))
                .isEqualTo(SpeakingPromptDelivery.Step.PROMPT_PLAYBACK);
        assertThat(delivery.promptVisibleBeforePlayback()).isFalse();
        assertThat(delivery.promptVisibleAfterPlayback()).isTrue();
    }

    @Test
    void incoherentTextOnlyAudioFailsClosed() {
        assertThatThrownBy(() -> presenter.present(
                content(
                        QuestionContent.SpeakingPromptInputType.MANUAL_TEXT,
                        QuestionContent.SpeakingDeliveryMode.TEXT_ONLY,
                        QuestionContent.SpeakingAudioOrigin.NONE,
                        "/practice/materials/9/content",
                        1),
                "질문입니다.",
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static QuestionContent content(
            QuestionContent.SpeakingPromptInputType input,
            QuestionContent.SpeakingDeliveryMode mode,
            QuestionContent.SpeakingAudioOrigin origin,
            String audio,
            Integer playLimit) {
        return QuestionContent.speakingV2(
                new QuestionContent.SpeakingDelivery(
                        input, mode, audio, origin, playLimit, 30, 60));
    }
}
