package com.ksh.features.practice.ai.writing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WritingRubricCriterionTest {

    @Test
    void legacyIdsRemainReadableAndMapToCanonicalMeaning() {
        assertSame(WritingRubricCriterion.W_LENGTH_REQUIREMENT_MET,
                WritingRubricCriterion.W_SENTENCE_VARIETY.canonical());
        assertSame(WritingRubricCriterion.W_FORMAL_REGISTER_CONSISTENCY,
                WritingRubricCriterion.W_REGISTER_HONORIFIC_ACCURACY.canonical());
        assertSame(WritingRubricCriterion.W_FORMAL_VOCABULARY_USAGE,
                WritingRubricCriterion.W_APPROPRIATE_VOCABULARY_USAGE.canonical());
        assertEquals(WritingRubricCriterion.FindingCategory.LENGTH,
                WritingRubricCriterion.W_SENTENCE_VARIETY.category());
        assertTrue(WritingRubricCriterion.W_SENTENCE_VARIETY.legacyOnly());
        assertTrue(WritingRubricCriterion.W_WON_GO_JI.legacyOnly());
    }

    @Test
    void q51AndQ52ShareClozeProfileButRemainDistinctTasks() {
        List<String> q51 = ids("Q51");
        List<String> q52 = ids("Q52");
        assertEquals(q51, q52);
        assertTrue(q51.contains("W_CLOZE_CONTEXT_FIT"));
        assertTrue(q51.contains("W_CONNECTIVE_ENDING_ACCURACY"));
        assertFalse(q51.contains("W_LOGICAL_ORGANIZATION"));
        assertFalse(q51.contains("W_LENGTH_REQUIREMENT_MET"));
        assertFalse(q51.contains("W_WON_GO_JI"));
    }

    @Test
    void essayProfilesExcludeTaskSpecificCriteriaFromOtherTasks() {
        List<String> q53 = ids("Q53");
        List<String> q54 = ids("Q54");
        List<String> general = ids("GENERAL");

        assertTrue(q53.contains("W_Q53_DATA_FLOW_ISSUES"));
        assertFalse(q53.contains("W_CLEAR_THESIS_OR_MAIN_IDEA"));
        assertFalse(q53.contains("W_FABRICATED_OR_INACCURATE_DATA"));
        assertTrue(q54.contains("W_CLEAR_THESIS_OR_MAIN_IDEA"));
        assertFalse(q54.contains("W_Q53_DATA_FLOW_ISSUES"));
        assertFalse(general.contains("W_Q53_DATA_FLOW_ISSUES"));
        assertFalse(general.contains("W_LENGTH_REQUIREMENT_MET"));
    }

    @Test
    void canonicalCriteriaDeclareSupportedEvidenceScopes() {
        assertTrue(WritingRubricCriterion.W_GRAMMAR_ERRORS
                .supports(WritingRubricCriterion.EvidenceScope.TEXT_SPAN));
        assertTrue(WritingRubricCriterion.W_LOGICAL_ORGANIZATION
                .supports(WritingRubricCriterion.EvidenceScope.WHOLE_ANSWER));
        assertTrue(WritingRubricCriterion.W_FABRICATED_OR_INACCURATE_DATA
                .supports(WritingRubricCriterion.EvidenceScope.TASK_METADATA));
        assertFalse(WritingRubricCriterion.W_FABRICATED_OR_INACCURATE_DATA.activeForProvider());
    }

    private static List<String> ids(String taskType) {
        return WritingRubricCriterion.activeForTask(taskType).stream()
                .map(WritingRubricCriterion::id)
                .toList();
    }
}
