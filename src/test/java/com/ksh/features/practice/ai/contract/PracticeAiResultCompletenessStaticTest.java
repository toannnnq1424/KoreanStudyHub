package com.ksh.features.practice.ai.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAiResultCompletenessStaticTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void oneVersionedModelCrossesEveryCurrentResultBoundary() throws Exception {
        String model = read("src/main/java/com/ksh/features/practice/ai/contract/"
                + "PracticeAiResultCompleteness.java");
        String writingProducer = read("src/main/java/com/ksh/features/practice/ai/"
                + "writing/WritingEvaluationNormalizer.java");
        String writingReader = read("src/main/java/com/ksh/features/practice/ai/"
                + "writing/WritingFeedbackContractParser.java");
        String writingPresenter = read("src/main/java/com/ksh/features/practice/ai/"
                + "writing/WritingFeedbackViewMapper.java");
        String rlProducer = read("src/main/java/com/ksh/features/practice/ai/"
                + "readinglistening/ReadingListeningExplanationClient.java");
        String rlReader = read("src/main/java/com/ksh/features/practice/ai/"
                + "readinglistening/QuestionExplanationReadService.java");
        String audioProducer = read("src/main/java/com/ksh/features/practice/ai/"
                + "speaking/acoustic/DirectAudioAcousticResponseNormalizer.java");
        String audioStore = read("src/main/java/com/ksh/features/practice/ai/"
                + "speaking/acoustic/DirectAudioDarkObservationService.java");

        assertThat(model).contains(
                "practice-ai-result-completeness-v1",
                "COMPLETE", "PARTIAL_NON_SCORE", "UNAVAILABLE",
                "rejected_item_count");
        assertThat(writingProducer).contains("PracticeAiResultCompleteness");
        assertThat(writingReader).contains("PracticeAiResultCompleteness.require");
        assertThat(writingPresenter)
                .contains("PracticeAiResultCompleteness.require")
                .contains("complete ? decimal", "complete && Boolean.TRUE");
        assertThat(rlProducer).contains("PracticeAiResultCompleteness.complete");
        assertThat(rlReader).contains("PracticeAiResultCompleteness.require");
        assertThat(audioProducer)
                .contains("DIRECT_AUDIO_DIAGNOSTIC_ITEMS_REJECTED")
                .contains("rejectedItemCount");
        assertThat(audioStore)
                .contains("validatedCompleteness")
                .contains("scoreReleaseEligible");
    }

    @Test
    void evidenceNamesExactConsumersAndNoMigration() throws Exception {
        String evidence = read("docs/evidence/"
                + "practice-ai-result-completeness-matrix-2026-08-03.md");
        assertThat(evidence).contains(
                "WritingEvaluationNormalizer",
                "WritingFeedbackContractParser",
                "QuestionExplanationReadService",
                "DirectAudioDarkObservationService.inspect",
                "V1–V102 remain the protected integration baseline",
                "No migration is needed");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }
}
