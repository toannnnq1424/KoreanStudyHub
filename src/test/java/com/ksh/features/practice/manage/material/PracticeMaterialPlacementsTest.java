package com.ksh.features.practice.manage.material;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeMaterialPlacementsTest {

    @Test
    void preservesCanonicalSpeakingPlacementBytes() {
        assertThat(PracticeMaterialPlacements.SPEAKING_PROMPT_ORIGINAL)
                .isEqualTo("SPEAKING_PROMPT_ORIGINAL");
        assertThat(PracticeMaterialPlacements.SPEAKING_PROMPT_TTS)
                .isEqualTo("SPEAKING_PROMPT_TTS");
    }

    @Test
    void recognizesOnlySpeakingPromptPlacements() {
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt(
                PracticeMaterialPlacements.SPEAKING_PROMPT_ORIGINAL)).isTrue();
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt(
                PracticeMaterialPlacements.SPEAKING_PROMPT_TTS)).isTrue();
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt(
                "SPEAKING_PROMPT_EXCEL_STAGING")).isFalse();
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt("QUESTION_IMAGE"))
                .isFalse();
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt(null)).isFalse();
    }
}
