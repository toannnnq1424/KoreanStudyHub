package com.ksh.features.practice.assessment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTypeResolverTest {

    private final QuestionTypeResolver resolver = new QuestionTypeResolver();

    @Test
    void legacyAliasesResolveToCanonicalTypes() {
        assertEquals(CanonicalQuestionType.SINGLE_CHOICE, resolver.resolve("MCQ"));
        assertEquals(CanonicalQuestionType.SINGLE_CHOICE, resolver.resolve("MCQ_SINGLE"));
        assertEquals(CanonicalQuestionType.MULTIPLE_CHOICE, resolver.resolve("MCQ_MULTIPLE"));
        assertEquals(CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN, resolver.resolve("TFNG"));
        assertEquals(CanonicalQuestionType.MATCHING, resolver.resolve("matching_information"));
        assertEquals(CanonicalQuestionType.FILL_BLANK, resolver.resolve(" GAP_FILL "));
    }

    @Test
    void canonicalQuestionTypesRoundTrip() {
        for (CanonicalQuestionType type : CanonicalQuestionType.values()) {
            assertEquals(type, resolver.resolve(type.name()));
            assertEquals(type.name(), resolver.canonicalCode(type.name()));
        }
    }

    @Test
    void unknownAndDeferredTypesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("ORDERING"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("TEXT_COMPLETION"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("SHORT_TEXT"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(""));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null));
    }

    @Test
    void objectiveClassificationIsExplicit() {
        assertTrue(CanonicalQuestionType.SINGLE_CHOICE.isObjective());
        assertTrue(CanonicalQuestionType.MULTIPLE_CHOICE.isObjective());
        assertTrue(CanonicalQuestionType.MATCHING.isObjective());
        assertFalse(CanonicalQuestionType.ESSAY.isObjective());
        assertFalse(CanonicalQuestionType.SPEAKING.isObjective());
    }
}
