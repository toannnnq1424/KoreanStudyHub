package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAuthoringCandidateNormalizationValidationTest {

    private ObjectMapper objectMapper;
    private PracticeAuthoringCandidateNormalizer normalizer;
    private PracticeAuthoringCandidateValidator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        QuestionTypeResolver typeResolver = new QuestionTypeResolver();
        AssessmentContractCodec codec = new AssessmentContractCodec(
                objectMapper, typeResolver);
        PracticeAuthoringCandidateJson json =
                new PracticeAuthoringCandidateJson(objectMapper);
        normalizer = new PracticeAuthoringCandidateNormalizer(
                objectMapper, codec, typeResolver, json);
        validator = new PracticeAuthoringCandidateValidator(
                codec, typeResolver, json);
    }

    @Test
    void normalizesNfcLineEndingsAndAcceptsFrozenQuickReadingContract() {
        ArrayNode groups = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, true);
        groups.get(0).withObject("/stimulus")
                .put("instruction", "  Cafe\u0301\r\nDòng hai  ");

        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(
                        PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID,
                        SourceKind.QUICK_EXCEL, groups);
        PracticeAuthoringCandidateValidator.ValidationResult validation =
                validator.validate(
                        SourceKind.QUICK_EXCEL,
                        new TargetRoute(5001L, 1, "READING", "R1"),
                        normalized.groups(), normalized.issues());

        assertThat(normalized.groups().get(0).path("stimulus")
                .path("instruction").asText()).isEqualTo("Café\nDòng hai");
        assertThat(validation.issues()).isEmpty();
    }

    @Test
    void emitsFieldAddressableIssueForUnknownSchemaField() {
        ArrayNode groups = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, true);
        groups.get(0).withObject("/questions/0")
                .put("score", 100);

        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(
                        PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID,
                        SourceKind.QUICK_EXCEL, groups);

        assertThat(normalized.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code())
                            .isEqualTo("CANDIDATE_SCHEMA_FIELD_UNKNOWN");
                    assertThat(issue.path())
                            .isEqualTo("/groups/0/questions/0/score");
                    assertThat(issue.blocking()).isTrue();
                });
    }

    @Test
    void stripsAndReportsUnknownNestedTypedFieldAtExactPointer() {
        ArrayNode groups = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, true);
        groups.get(0).withObject("/questions/0/questionContent/options/0")
                .put("score", 100);

        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(
                        PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID,
                        SourceKind.QUICK_EXCEL, groups);

        assertThat(normalized.groups().get(0)
                .path("questions").get(0)
                .path("questionContent").path("options").get(0)
                .has("score")).isFalse();
        assertThat(normalized.issues()).anySatisfy(issue -> {
            assertThat(issue.code())
                    .isEqualTo("CANDIDATE_SCHEMA_FIELD_UNKNOWN");
            assertThat(issue.path()).isEqualTo(
                    "/groups/0/questions/0/questionContent/options/0/score");
            assertThat(issue.blocking()).isTrue();
        });
    }

    @Test
    void quickMatchingFailsClosedToAdvancedWithoutLossyConversion() {
        ArrayNode groups = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, true);
        groups.get(0).withObject("/questions/0")
                .put("questionType", "MATCHING");
        groups.get(0).withObject("/questions/0/answerSpec")
                .put("questionType", "MATCHING")
                .put("scoringPolicyCode", "NORMALIZED_EXACT");

        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(
                        PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID,
                        SourceKind.QUICK_EXCEL, groups);
        PracticeAuthoringCandidateValidator.ValidationResult validation =
                validator.validate(
                        SourceKind.QUICK_EXCEL,
                        new TargetRoute(5001L, 1, "READING", "R1"),
                        normalized.groups(), normalized.issues());

        assertThat(validation.issues())
                .extracting(issue -> issue.code())
                .contains("QUESTION_TYPE_NOT_SUPPORTED_BY_QUICK");
    }

    @Test
    void unapprovedImportedStimulusBlocksReadyState() {
        ArrayNode groups = PracticeAuthoringCandidateTestFixtures
                .readingGroups(objectMapper, false);
        PracticeAuthoringCandidateNormalizer.NormalizationResult normalized =
                normalizer.normalize(
                        PracticeAuthoringCandidateTestFixtures.CANDIDATE_ID,
                        SourceKind.QUICK_EXCEL, groups);
        PracticeAuthoringCandidateValidator.ValidationResult validation =
                validator.validate(
                        SourceKind.QUICK_EXCEL,
                        new TargetRoute(5001L, 1, "READING", "R1"),
                        normalized.groups(), List.of());

        assertThat(validation.issues())
                .anyMatch(issue -> "STIMULUS_REVIEW_REQUIRED"
                        .equals(issue.code()) && issue.blocking());
    }
}
