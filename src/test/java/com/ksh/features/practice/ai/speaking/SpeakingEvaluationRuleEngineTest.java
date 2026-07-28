package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingEvaluationRuleEngineTest {
    private final SpeakingRuleEngine ruleEngine = new SpeakingRuleEngine();

    @Test
    void transcriptFillersDoNotCreateAcousticSignals() {
        SpeakingRuleEngine.SpeakingRuleAnalysis analysis = ruleEngine.analyze(
                "음 저는 어 그 한국어를 뭐 그러니까 공부하고 있어요. 음 재미있어요.",
                new BigDecimal("0.90"),
                false);

        assertThat(codes(analysis)).doesNotContain("REPEATED_FILLERS");
        assertThat(analysis.signals()).noneMatch(signal ->
                signal.message().toLowerCase().contains("fluency")
                        || signal.message().toLowerCase().contains("listener burden"));
    }

    @Test
    void detectsMixedRegisterEndingStyle() {
        SpeakingRuleEngine.SpeakingRuleAnalysis analysis = ruleEngine.analyze(
                "저는 학교에 갔어요. 그리고 친구를 만났어. 정말 좋아요.",
                new BigDecimal("0.90"),
                false);

        assertThat(codes(analysis)).contains("MIXED_REGISTER_ENDINGS");
        assertThat(analysis.signals()).anyMatch(signal ->
                signal.category() == SpeakingRuleEngine.SpeakingRuleCategory.REGISTER);
    }

    @Test
    void politeSpokenEndingsAreNotWritingStyleErrors() {
        SpeakingRuleEngine.SpeakingRuleAnalysis analysis = ruleEngine.analyze(
                "저는 한국어를 공부하고 있어요. 매일 연습해요. 재미있어요.",
                new BigDecimal("0.90"),
                false);

        assertThat(codes(analysis))
                .doesNotContain("MIXED_REGISTER_ENDINGS")
                .doesNotContain("POLITE_SPOKEN_ENDING_ERROR");
    }

    @Test
    void normalTranscriptEmitsNoAcousticCategoryOrDiagnosis() {
        SpeakingRuleEngine.SpeakingRuleAnalysis analysis = ruleEngine.analyze(
                "저는 한국어를 공부하고 있어요.",
                new BigDecimal("0.90"),
                false);

        assertThat(codes(analysis)).doesNotContain("NO_PHONEME_CERTAINTY", "REPEATED_FILLERS");
        assertThat(analysis.signals()).noneMatch(signal ->
                signal.message().toLowerCase().contains("pronunciation")
                        || signal.message().toLowerCase().contains("phoneme"));
    }

    @Test
    void textFallbackAndLowConfidenceProduceSafeSignals() {
        SpeakingRuleEngine.SpeakingRuleAnalysis analysis = ruleEngine.analyze(
                "저는 학생이에요.",
                new BigDecimal("0.20"),
                true);

        assertThat(codes(analysis))
                .contains("TEXT_FALLBACK_TRANSCRIPT_ONLY")
                .contains("LOW_TRANSCRIPT_CONFIDENCE")
                .doesNotContain("NO_PHONEME_CERTAINTY", "REPEATED_FILLERS");
        assertThat(analysis.signals()).allMatch(signal ->
                signal.category() == SpeakingRuleEngine.SpeakingRuleCategory.CONTENT
                        || signal.category() == SpeakingRuleEngine.SpeakingRuleCategory.REGISTER
                        || signal.category() == SpeakingRuleEngine.SpeakingRuleCategory.COHERENCE);
    }

    private Set<String> codes(SpeakingRuleEngine.SpeakingRuleAnalysis analysis) {
        return analysis.signals().stream()
                .map(SpeakingRuleEngine.SpeakingRuleSignal::code)
                .collect(Collectors.toSet());
    }
}
