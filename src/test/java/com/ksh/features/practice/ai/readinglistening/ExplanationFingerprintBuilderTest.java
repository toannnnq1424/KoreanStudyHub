package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExplanationFingerprintBuilderTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Test
    void fingerprintIsCanonicalAndIndependentFromDatabaseOrLearnerIdentity() {
        ExplanationFingerprintBuilder builder = builder("model-a", "prompt-a", "schema-a");

        ExplanationFingerprint first = builder.build(input(
                "Choose the answer", "Read the passage", "opt_1",
                "Published passage", "Teacher note", "NUMERIC", DIGEST_A, "vi"));
        ExplanationFingerprint repeated = builder.build(input(
                "Choose the answer", "Read the passage", "opt_1",
                "Published passage", "Teacher note", "NUMERIC", DIGEST_A, "vi"));

        assertThat(repeated.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(first.inputContractJson())
                .doesNotContain("questionId", "questionVersionId", "learnerAnswer", "selectedOptionIds")
                .contains("opt_1", "Published passage", DIGEST_A);
        assertThat(Arrays.stream(ExplanationArtifactInput.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("questionId", "questionVersionId", "learnerAnswer");
    }

    @Test
    void everyPublishedInputOrGenerationContractChangeInvalidatesTheFingerprint() {
        ExplanationArtifactInput baseline = input(
                "Choose the answer", "Read the passage", "opt_1",
                "Published passage", "Teacher note", "NUMERIC", DIGEST_A, "vi");
        ExplanationFingerprintBuilder builder = builder("model-a", "prompt-a", "schema-a");

        List<String> fingerprints = List.of(
                builder.build(baseline).fingerprint(),
                builder.build(input("Changed prompt", "Read the passage", "opt_1",
                        "Published passage", "Teacher note", "NUMERIC", DIGEST_A, "vi")).fingerprint(),
                builder.build(input("Choose the answer", "Changed instruction", "opt_1",
                        "Published passage", "Teacher note", "NUMERIC", DIGEST_A, "vi")).fingerprint(),
                builder.build(input("Choose the answer", "Read the passage", "opt_2",
                        "Published passage", "Teacher note", "NUMERIC", DIGEST_A, "vi")).fingerprint(),
                builder.build(input("Choose the answer", "Read the passage", "opt_1",
                        "Changed passage", "Teacher note", "NUMERIC", DIGEST_A, "vi")).fingerprint(),
                builder.build(input("Choose the answer", "Read the passage", "opt_1",
                        "Published passage", "Changed teacher note", "NUMERIC", DIGEST_A, "vi")).fingerprint(),
                builder.build(input("Choose the answer", "Read the passage", "opt_1",
                        "Published passage", "Teacher note", "ALPHA", DIGEST_A, "vi")).fingerprint(),
                builder.build(input("Choose the answer", "Read the passage", "opt_1",
                        "Published passage", "Teacher note", "NUMERIC", DIGEST_B, "vi")).fingerprint(),
                builder.build(input("Choose the answer", "Read the passage", "opt_1",
                        "Published passage", "Teacher note", "NUMERIC", DIGEST_A, "en")).fingerprint(),
                builder("model-b", "prompt-a", "schema-a").build(baseline).fingerprint(),
                builder("model-a", "prompt-b", "schema-a").build(baseline).fingerprint(),
                builder("model-a", "prompt-a", "schema-b").build(baseline).fingerprint());

        assertThat(fingerprints).doesNotHaveDuplicates();
    }

    @Test
    void nonSemanticProvenanceAndMediaMetadataDoNotInvalidateSharedContent() {
        ExplanationFingerprintBuilder builder = builder("model-a", "prompt-a", "schema-a");
        ExplanationArtifactInput baseline = input(
                "Choose the answer", "Read the passage", "opt_1",
                "Published passage", "Teacher note", "NUMERIC", DIGEST_A, "vi");
        ExplanationArtifactInput republished = new ExplanationArtifactInput(
                baseline.schemaVersion(),
                baseline.skill(),
                baseline.questionType(),
                baseline.prompt(),
                baseline.instruction(),
                baseline.questionContent(),
                baseline.answerSpec(),
                AssessmentStimulus.readingPassage("Published passage", "PDF_IMPORT"),
                baseline.teacherExplanation(),
                baseline.optionLabelMode(),
                baseline.explanationLanguage(),
                List.of(
                        new ExplanationArtifactInput.MediaDescriptor(
                                "group.image", "IMAGE", DIGEST_B, "image/jpeg", 200L),
                        new ExplanationArtifactInput.MediaDescriptor(
                                "question.image", "IMAGE", DIGEST_A,
                                "application/octet-stream", 999L)),
                baseline.readinessIssue());
        ExplanationArtifactInput originalOrder = new ExplanationArtifactInput(
                baseline.schemaVersion(),
                baseline.skill(),
                baseline.questionType(),
                baseline.prompt(),
                baseline.instruction(),
                baseline.questionContent(),
                baseline.answerSpec(),
                baseline.stimulus(),
                baseline.teacherExplanation(),
                baseline.optionLabelMode(),
                baseline.explanationLanguage(),
                List.of(
                        baseline.media().get(0),
                        new ExplanationArtifactInput.MediaDescriptor(
                                "group.image", "IMAGE", DIGEST_B, "image/webp", 300L)),
                baseline.readinessIssue());

        assertThat(builder.build(republished).fingerprint())
                .isEqualTo(builder.build(originalOrder).fingerprint());
    }

    private static ExplanationArtifactInput input(
            String prompt,
            String instruction,
            String correctOptionId,
            String passage,
            String teacherExplanation,
            String optionLabelMode,
            String mediaDigest,
            String language) {
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("opt_1", "First"),
                        new QuestionContent.Option("opt_2", "Second")),
                List.of());
        AnswerSpec answerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of(correctOptionId),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        return new ExplanationArtifactInput(
                ExplanationArtifactInput.SCHEMA_VERSION,
                AssessmentSkill.READING,
                CanonicalQuestionType.SINGLE_CHOICE,
                prompt,
                instruction,
                content,
                answerSpec,
                AssessmentStimulus.readingPassage(passage, "PUBLISHED_SNAPSHOT"),
                teacherExplanation,
                optionLabelMode,
                language,
                List.of(new ExplanationArtifactInput.MediaDescriptor(
                        "question.image", "IMAGE", mediaDigest, "image/png", 100L)),
                null);
    }

    private static ExplanationFingerprintBuilder builder(
            String model,
            String promptVersion,
            String responseSchemaVersion) {
        ReadingListeningExplanationClient client = mock(ReadingListeningExplanationClient.class);
        when(client.model()).thenReturn(model);
        when(client.promptVersion()).thenReturn(promptVersion);
        when(client.schemaVersion()).thenReturn(responseSchemaVersion);
        return new ExplanationFingerprintBuilder(new ObjectMapper(), client);
    }
}
