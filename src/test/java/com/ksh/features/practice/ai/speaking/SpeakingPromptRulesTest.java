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
                .contains("allowed_rubric provides each criterion and its max_score")
                .contains("Do not use a 10-point band")
                .contains("Do not use 9.0 / 7.5 / 5.0")
                .contains("Do not use few-shot calibration samples")
                .contains("exact phoneme-level")
                .contains("Evidence source values must be only: TRANSCRIPT.")
                .contains("AUDIO_METADATA is not an allowed grounding source")
                .contains("TASK_METADATA is not accepted by the current evidence output contract")
                .contains("interpreted_intent=null and intent_confidence=null")
                .contains("S_CONTENT_TASK_FULFILLMENT")
                .contains("S_GRAMMAR_SENTENCE_CONTROL")
                .contains("S_VOCABULARY_EXPRESSIONS")
                .contains("S_COHERENCE_ORGANIZATION")
                .contains("Fluency and Pronunciation / Delivery are NOT_SCORABLE")
                .contains("score_available=false, overall_score=null, and level_label=null")
                .contains("Do not rescale the four language criteria to 100")
                .contains("Content / Task Achievement")
                .contains("Vocabulary / Expressions")
                .contains("Grammar / Sentence Control")
                .contains("Register / Honorifics / Ending Consistency")
                .contains("Coherence / Organization")
                .contains("action_plan")
                .contains("criterion_feedback")
                .contains("transcript_annotations")
                .contains("upgraded_answer")
                .contains("sample_answer")
                .contains("confidence_notes")
                .contains("allowed_rubric provides each criterion and its max_score")
                .contains("do not assume fixed weights")
                .doesNotContain("S_FLUENCY")
                .doesNotContain("S_PRONUNCIATION_DELIVERY")
                .doesNotContain("AUDIO_METADATA, PROMPT")
                .doesNotContain("criteria with max 20 and max 15");
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
                .contains("Use exact transcript text for evidence")
                .contains("Do not translate, normalize, or rewrite evidence")
                .doesNotContain("Use Korean only for evidence")
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
                .contains("Do not infer hesitation, pauses, pacing, speech rate, fillers, self-repair")
                .contains("ASR confidence describes transcription confidence only")
                .contains("AUDIO_METADATA is provenance only")
                .doesNotContain("evaluate fluency and listener burden conservatively");
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
                .contains("Fluency and Pronunciation / Delivery are NOT_SCORABLE")
                .contains("no score, max percentage, level, or band")
                .contains("Do not pretend learner audio was evaluated");
    }

    @Test
    void promptRulesDefineCriterionFeedbackAndActionPlanSchemasExplicitly() {
        String prompt = SpeakingPromptRules.buildSystemPrompt(false);

        assertThat(prompt)
                .contains("criterion_feedback item: criterion_id, display_name, score, max_score, level_label, summary")
                .contains("strengths, needs_improvement, subcriteria")
                .contains("subcriteria item: sub_criterion_id, display_name, level_label, summary")
                .contains("action_plan item: criterion_id, sub_criterion_id, title, instruction, reason, priority")
                .contains("Use the exact snake_case field names from the provided JSON schema")
                .contains("Backend provenance, transcript identity, model/version identity and media identity")
                .doesNotContain("pronunciation_advisory")
                .doesNotContain("fluency_observations");
    }
}
