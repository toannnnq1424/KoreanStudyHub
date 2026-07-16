package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingResultRenderingContractTest {

    @Test
    void resultDetailContainsFunctionalSpeakingAiTabsAndRichContractFields() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/templates/practice/result-detail.html"));

        assertThat(html)
                .contains("data-tab=\"criterion-content\"")
                .contains("data-tab=\"criterion-vocabulary\"")
                .contains("data-tab=\"criterion-grammar\"")
                .contains("data-tab=\"criterion-fluency\"")
                .contains("data-tab=\"criterion-pronunciation\"")
                .contains("S_CONTENT_TASK_FULFILLMENT")
                .contains("S_VOCABULARY_EXPRESSIONS")
                .contains("S_GRAMMAR_SENTENCE_CONTROL")
                .contains("S_FLUENCY")
                .contains("S_PRONUNCIATION_DELIVERY")
                .contains("criterion_feedback")
                .contains("criterionFeedback")
                .contains("transcript_annotations")
                .contains("transcriptAnnotations")
                .contains("score_available")
                .contains("scoreAvailable");
    }

    @Test
    void speakingOverviewUsesSixSpeakingCriteriaAndProtectedAudioRouteOnly() throws Exception {
        String overview = Files.readString(Path.of("src/main/resources/templates/practice/result.html"));
        String fragment = Files.readString(Path.of("src/main/resources/templates/practice/result/speaking.html"));
        String presenter = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java"));
        String rubric = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/speaking/SpeakingRubricCriterion.java"));

        assertThat(rubric)
                .contains("S_CONTENT_TASK_FULFILLMENT")
                .contains("S_VOCABULARY_EXPRESSIONS")
                .contains("S_GRAMMAR_SENTENCE_CONTROL")
                .contains("S_COHERENCE_ORGANIZATION")
                .contains("S_FLUENCY")
                .contains("S_PRONUNCIATION_DELIVERY");
        assertThat(presenter)
                .contains("SpeakingRubricCriterion.values()")
                .contains("criterion.id()");
        assertThat(overview).contains("practice/result/speaking");
        assertThat(fragment)
                .contains("Sáu tiêu chí tiếng Hàn")
                .contains("result.payload().criteria()")
                .contains("media.playbackPath()");
        assertThat(overview + fragment)
                .doesNotContain("storageKey")
                .doesNotContain("contentHash")
                .doesNotContain("apiKey");
        assertThat(fragment)
                .doesNotContain("bản xứ")
                .doesNotContain("native-like")
                .contains("Phạm vi đánh giá giới hạn")
                .contains("criterion.advisoryOnly()")
                .contains(">Tham khảo<");
    }

    @Test
    void workflowRecordsVietnameseKoreanProductLanguagePolicyAnd8EDStatus() throws Exception {
        String workflow = Files.readString(Path.of("CODEX_PRACTICE_WORKFLOW.md"));

        assertThat(workflow)
                .contains("KSH is a Vietnamese/Korean learning website")
                .contains("must use Vietnamese and/or Korean only")
                .contains("English-learning product")
                .contains("#### Phase 8E-D — Speaking AI Persistence and Result Rendering")
                .contains("IMPLEMENTED_AND_FOCUSED_TESTED")
                .contains("speaking_ai_v1");
    }
}
