package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingResultRenderingContractTest {


    @Test
    void speakingOverviewConsumesExplicitCapabilityAndCriterionAvailabilityContract() throws Exception {
        String overview = Files.readString(Path.of("src/main/resources/templates/practice/result.html"));
        String fragment = Files.readString(Path.of("src/main/resources/templates/practice/result/speaking.html"));
        String presenter = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/result/SpeakingResultPresenter.java"));
        String rubric = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/speaking/SpeakingRubricCriterion.java"));
        String dto = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/dto/PracticeDtos.java"));
        String css = Files.readString(Path.of(
                "src/main/resources/static/css/practice-result.css"))
                + Files.readString(Path.of(
                "src/main/resources/static/css/practice-result-prep.css"));

        assertThat(rubric)
                .contains("S_CONTENT_TASK_FULFILLMENT")
                .contains("S_VOCABULARY_EXPRESSIONS")
                .contains("S_GRAMMAR_SENTENCE_CONTROL")
                .contains("S_COHERENCE_ORGANIZATION")
                .contains("S_FLUENCY")
                .contains("S_PRONUNCIATION_DELIVERY");
        assertThat(presenter)
                .contains("SpeakingRubricCriterion.values()")
                .contains("criterion.id()")
                .contains("profileAvailable()")
                .contains("holisticScoreAvailable()")
                .contains("NOT_SCORABLE")
                .contains("LEGACY_UNVERIFIED")
                .contains("AUDIO_DIRECT_FULL_RESERVED")
                .contains("trustedOverviewCapability")
                .contains("criterion.transcriptGrounded()")
                .contains("limit(4)");
        assertThat(dto)
                .contains("String evaluatorCapability")
                .contains("String evidenceContractVersion")
                .contains("String contractTrust")
                .contains("boolean holisticScoreAvailable")
                .contains("String profileState")
                .contains("String availability")
                .contains("requiresDirectAudioEvidence")
                .contains("profileStateLabel()")
                .contains("evidenceSourceLabel()")
                .contains("trustLabel()");
        assertThat(overview).contains("practice/result/speaking");
        assertThat(fragment)
                .contains("Hồ sơ ngôn ngữ dựa trên bản chép lời")
                .contains("Bốn tiêu chí ngôn ngữ dùng điểm tối đa riêng")
                .contains("Kết quả Nói tổng hợp")
                .contains("Chưa khả dụng")
                .contains("Không cộng bốn tiêu chí bản chép lời thành điểm Nói tổng hợp")
                .contains("criterion.scored()")
                .contains("criterion.notScorable()")
                .contains("criterion.availabilityLabel()")
                .contains("result.payload().criteria()")
                .contains("result.payload().holisticScoreAvailable()")
                .contains("result.payload().radarPolygonPoints()")
                .contains("result.payload().radarAxes()")
                .contains("axis.percentage()")
                .contains("item.criterionLabel()")
                .contains("media.playbackPath()");
        assertThat(overview + fragment)
                .doesNotContain("storageKey")
                .doesNotContain("contentHash")
                .doesNotContain("apiKey");
        assertThat(fragment)
                .doesNotContain("bản xứ")
                .doesNotContain("native-like")
                .doesNotContain("IELTS")
                .doesNotContain("criterion.band()")
                .doesNotContain("criterion.percentage()")
                .doesNotContain("pr-scale")
                .contains("pr-speaking-radar")
                .contains("Mỗi trục được chuẩn hóa độc lập theo điểm đạt được trên điểm tối đa")
                .contains("bộ đánh giá chưa nhận âm thanh trực tiếp")
                .contains("criterion.coverageLabel()");
        assertThat(css)
                .contains(".pr-speaking-profile-state")
                .contains(".pr-speaking-provenance")
                .contains(".pr-speaking-criterion.is-not-scorable")
                .contains(".pr-speaking-criterion-no-score")
                .contains(".pr-speaking-radar")
                .doesNotContain(".pr-band-chip")
                .doesNotContain(".pr-scale");
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
