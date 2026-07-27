package com.ksh.features.practice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.SpeakingPromptDelivery;
import com.ksh.features.practice.assessment.SpeakingPromptDeliveryPresenter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingPlayerPayloadPrivacyTest {

    @Test
    void learnerQuestionSerializesOnlySharedDeliveryWithoutInternalContext()
            throws Exception {
        SpeakingPromptDelivery delivery =
                new SpeakingPromptDeliveryPresenter().present(
                        QuestionContent.speakingV2(
                                new QuestionContent.SpeakingDelivery(
                                        QuestionContent
                                                .SpeakingPromptInputType
                                                .AUDIO_UPLOAD,
                                        QuestionContent.SpeakingDeliveryMode
                                                .AUDIO_ONLY,
                                        "/practice/materials/7/content",
                                        QuestionContent.SpeakingAudioOrigin
                                                .TEACHER_UPLOAD,
                                        1,
                                        30,
                                        60)),
                        "SECRET_LECTURER_TRANSCRIPT",
                        null);
        PracticeService.SpeakingPlayerQuestion question =
                new PracticeService.SpeakingPlayerQuestion(
                        11L,
                        1,
                        "Phần nói",
                        BigDecimal.ONE,
                        null,
                        delivery);

        JsonNode json = new ObjectMapper().readTree(
                new ObjectMapper().writeValueAsString(question));

        assertThat(json.has("delivery")).isTrue();
        assertThat(json.has("prompt")).isFalse();
        assertThat(json.has("promptAudioReference")).isFalse();
        assertThat(json.path("delivery").path("promptText").isMissingNode())
                .isTrue();
        assertThat(json.toString()).doesNotContain(
                "SECRET_LECTURER_TRANSCRIPT",
                "promptContext",
                "artifact",
                "provider",
                "fingerprint",
                "storage");
    }
}
