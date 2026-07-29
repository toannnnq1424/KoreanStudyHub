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
        assertThat(analysis.signals()).allMatch(signal ->
                signal.action() !=
                        SpeakingRuleEngine.SpeakingRuleAction.NEEDS_IMPROVEMENT);
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
        assertThat(analysis.signals()).allMatch(signal ->
                signal.action() !=
                        SpeakingRuleEngine.SpeakingRuleAction.NEEDS_IMPROVEMENT);
    }

    @Test
    void lexicalSubstringsAndPoliteEndingsDoNotCreateMixedRegister() {
        for (String transcript : java.util.List.of(
                "저는 요리를 좋아해요.",
                "저는 한국에서 일하고 싶어요.",
                "그 분야. 저는 한국어가 좋아요.",
                "어제 운동을 했어요.")) {
            SpeakingRuleEngine.SpeakingRuleAnalysis analysis =
                    ruleEngine.analyze(
                            transcript, new BigDecimal("0.90"), false);
            assertThat(codes(analysis))
                    .as(transcript)
                    .doesNotContain("MIXED_REGISTER_ENDINGS");
        }
    }

    @Test
    void decomposedHangulIsNormalizedAndEveryDeterministicSignalIsAdvisory() {
        String decomposed = java.text.Normalizer.normalize(
                "저는 학교에 갔어요. 친구를 만났어.",
                java.text.Normalizer.Form.NFD);

        SpeakingRuleEngine.SpeakingRuleAnalysis analysis =
                ruleEngine.analyze(
                        decomposed, new BigDecimal("0.90"), false);

        assertThat(codes(analysis))
                .contains("MIXED_REGISTER_ENDINGS")
                .doesNotContain("NO_KOREAN_TRANSCRIPT");
        assertThat(analysis.signals()).allMatch(signal ->
                signal.action() == SpeakingRuleEngine.SpeakingRuleAction.SUGGESTION
                        || signal.action()
                        == SpeakingRuleEngine.SpeakingRuleAction.INFO);
        assertThat(analysis.signals()).allMatch(signal ->
                !signal.message().contains("Mixed")
                        && !signal.message().contains("Transcript"));
    }

    @Test
    void embeddedDiscourseMarkerSubstringDoesNotCountAsABoundedMarker() {
        String embeddedOnly = (
                "이 응답은 마지막으로서라는 단어만 반복하지만 실제 연결 표현은 "
                        + "따로 사용하지 않고 같은 설명을 길게 이어 가고 있어요. ")
                .repeat(3);
        String boundedMarker = (
                "이 응답은 같은 설명을 길게 이어 가고 있어요. "
                        + "하지만 다음 문장에서는 앞의 내용과 다른 근거를 제시해요. ")
                .repeat(3);

        assertThat(codes(ruleEngine.analyze(
                embeddedOnly, new BigDecimal("0.90"), false)))
                .contains("NO_DISCOURSE_MARKERS");
        assertThat(codes(ruleEngine.analyze(
                boundedMarker, new BigDecimal("0.90"), false)))
                .doesNotContain("NO_DISCOURSE_MARKERS");
    }

    private Set<String> codes(SpeakingRuleEngine.SpeakingRuleAnalysis analysis) {
        return analysis.signals().stream()
                .map(SpeakingRuleEngine.SpeakingRuleSignal::code)
                .collect(Collectors.toSet());
    }
}
