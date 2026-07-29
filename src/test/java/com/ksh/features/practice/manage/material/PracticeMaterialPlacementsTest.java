package com.ksh.features.practice.manage.material;

import com.ksh.features.practice.manage.service.PracticeAssessmentExcelService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeMaterialPlacementsTest {

    @Test
    void preservesPersistedSpeakingPlacementBytesAndExcelAlias() {
        assertThat(PracticeMaterialPlacements.SPEAKING_PROMPT_ORIGINAL)
                .isEqualTo("SPEAKING_PROMPT_ORIGINAL");
        assertThat(PracticeMaterialPlacements.SPEAKING_PROMPT_TTS)
                .isEqualTo("SPEAKING_PROMPT_TTS");
        assertThat(PracticeMaterialPlacements.SPEAKING_PROMPT_EXCEL_STAGING)
                .isEqualTo("SPEAKING_PROMPT_EXCEL_STAGING")
                .isEqualTo(PracticeAssessmentExcelService.EXCEL_SPEAKING_STAGING);
    }

    @Test
    void recognizesOnlySpeakingPromptPlacements() {
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt(
                PracticeMaterialPlacements.SPEAKING_PROMPT_ORIGINAL)).isTrue();
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt(
                PracticeMaterialPlacements.SPEAKING_PROMPT_TTS)).isTrue();
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt(
                PracticeMaterialPlacements.SPEAKING_PROMPT_EXCEL_STAGING)).isTrue();
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt("QUESTION_IMAGE"))
                .isFalse();
        assertThat(PracticeMaterialPlacements.isSpeakingPrompt(null)).isFalse();
    }
}
