package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingResultRenderingContractTest {

    @Test
    void legacyResultDetailFailsClosedForSpeakingScoresWithoutRedesigningPhase13E() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/templates/practice/result-detail.html"));

        assertThat(html)
                .contains("questions.filter(q => q.questionType === 'ESSAY')")
                .contains("currentQ.writingFeedback || {}")
                .contains("profile_available")
                .contains("profileAvailable")
                .contains("holistic_score_available")
                .contains("holisticScoreAvailable")
                .contains("return setSkill === 'SPEAKING';")
                .doesNotContain("currentQ.questionType !== 'ESSAY'")
                .contains("row.availability === 'SCORED'")
                .contains("function speakingScoredRubricRows()")
                .contains("field(aiData, 'profile_available', 'profileAvailable') === true")
                .contains("typeof score === 'number'")
                .contains("typeof maxScore === 'number'")
                .contains("Number.isFinite(score)")
                .contains("Number.isFinite(maxScore)")
                .contains("firstValue(r.name, 'Tiêu chí hồ sơ')")
                .doesNotContain("SPEAKING_TRANSCRIPT_CRITERIA")
                .doesNotContain("SPEAKING_ACOUSTIC_CRITERIA")
                .doesNotContain("SPEAKING_CRITERION_LABELS")
                .contains("result.set().skill() == 'SPEAKING' ? 'Không có điểm Nói tổng hợp' : 'Điểm bài làm'")
                .contains("Không có điểm Nói tổng hợp")
                .contains("Hồ sơ này chỉ dựa trên bản chép lời; không đánh giá độ lưu loát, phát âm hoặc đặc tính âm thanh.")
                .contains("const score = row && row.score")
                .contains("const maxScore = row && row.maxScore")
                .doesNotContain("r.max_score, 100")
                .doesNotContain("rubric.max_score, 100")
                .doesNotContain("AUDIO_DIRECT_FULL_RESERVED")
                .doesNotContain("DIRECT_AUDIO_AND_TRANSCRIPT");
    }

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
                "src/main/resources/static/css/practice-result.css"));

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
                .contains("item.criterion().transcriptGrounded()")
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
                .doesNotContain("radar")
                .contains("bộ đánh giá chưa nhận âm thanh trực tiếp")
                .contains("criterion.coverageLabel()");
        assertThat(css)
                .contains(".pr-speaking-profile-state")
                .contains(".pr-speaking-provenance")
                .contains(".pr-speaking-criterion.is-not-scorable")
                .contains(".pr-speaking-criterion-no-score")
                .doesNotContain(".pr-band-chip")
                .doesNotContain(".pr-scale")
                .doesNotContain("radar");
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
