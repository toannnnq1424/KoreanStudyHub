package com.ksh.features.practice.ai.speaking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingPromptRulesTest {
    @Test
    void promptRulesIncludeAllowedRubricPolicyAndEvidenceConstraints() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("KSH practice only")
                .contains("not an official TOPIK Speaking score")
                .contains("Score only the criteria provided in allowed_rubric")
                .contains("max_score from allowed_rubric")
                .contains("Do not use a 10-point band")
                .contains("Do not use 9.0 / 7.5 / 5.0")
                .contains("Do not use few-shot calibration samples")
                .contains("Pronunciation feedback is advisory only")
                .contains("exact phoneme-level")
                .contains("TRANSCRIPT")
                .contains("AUDIO_METADATA")
                .contains("PROMPT")
                .contains("INTERPRETED_INTENT")
                .contains("S_CONTENT_TASK_FULFILLMENT")
                .contains("S_GRAMMAR_SENTENCE_CONTROL")
                .contains("S_VOCABULARY_EXPRESSIONS")
                .contains("S_COHERENCE_ORGANIZATION")
                .contains("S_FLUENCY")
                .contains("S_PRONUNCIATION_DELIVERY")
                .contains("Content / Task Achievement")
                .contains("Vocabulary / Expressions")
                .contains("Grammar / Sentence Control")
                .contains("Register / Honorifics / Ending Consistency")
                .contains("Coherence / Organization")
                .contains("Fluency")
                .contains("Rich feedback contract")
                .contains("action_plan")
                .contains("criterion_feedback")
                .contains("transcript_annotations")
                .contains("upgraded_answer")
                .contains("sample_answer")
                .contains("confidence_notes")
                .contains("max 20")
                .contains("max 15");
    }

    @Test
    void promptRulesUseStableKshSectionsAndLanguagePolicy() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("[POLICY RULES]")
                .contains("[ALLOWED_RUBRIC SCORING RULES]")
                .contains("[EVIDENCE SOURCE RULES]")
                .contains("[OVERALL AND RUBRIC SECTION]")
                .contains("[STRENGTHS AND NEEDS IMPROVEMENT SECTION]")
                .contains("[TRANSCRIPT ANNOTATION SECTION]")
                .contains("[UPGRADED AND SAMPLE ANSWER SECTION]")
                .contains("[OUTPUT JSON SECTION]")
                .contains("[LANGUAGE POLICY]")
                .contains("Use Vietnamese for learner-facing explanations")
                .contains("Use Korean only for evidence")
                .contains("Do NOT use English in learner-facing explanations");
    }

    @Test
    void promptRulesContainKoreanSpecificChecklistsAndGuardrails() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("이/가")
                .contains("은/는")
                .contains("을/를")
                .contains("관심이 많다")
                .contains("영향을 미치다")
                .contains("문제를 해결하다")
                .contains("말투")
                .contains("높임말")
                .contains("반말/존댓말")
                .contains("음, 어, 그")
                .contains("suspected pronunciation issue")
                .contains("possible batchim/linking/vowel issue")
                .contains("Do not create phoneme-level facts");
    }

    @Test
    void promptRulesContainStrictJsonAndSpamGuardrail() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("overall_summary")
                .contains("task_achievement_summary")
                .contains("rubric_scores")
                .contains("strengths")
                .contains("needs_improvement")
                .contains("transcript_annotations")
                .contains("upgraded_answer")
                .contains("sample_answer")
                .contains("confidence_notes")
                .contains("action_plan")
                .contains("[SPAM_DETECTED]")
                .contains("strengths must be empty")
                .contains("Do not treat valid contextual words like TOPIK, AI, K-pop, 2026, Internet, SNS as spam");
    }

    @Test
    void textFallbackRulesMakeNoAudioLimitExplicit() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(true);

        assertThat(prompt)
                .contains("text-only fallback")
                .contains("Pronunciation & Delivery must be capped")
                .contains("do not pretend learner audio was evaluated");
    }
}
