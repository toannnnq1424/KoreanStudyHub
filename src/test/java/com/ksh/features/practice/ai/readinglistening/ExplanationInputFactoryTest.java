package com.ksh.features.practice.ai.readinglistening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeSetVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExplanationInputFactoryTest {

    @Test
    void generatedIdsAreExcludedWhileEquivalentContentAndMediaReuseTheFingerprint() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
        PracticeSetVersionRepository setVersionRepository = mock(PracticeSetVersionRepository.class);
        ReadingListeningExplanationClient client = mock(ReadingListeningExplanationClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ExplanationInputFactory factory = new ExplanationInputFactory(
                codec,
                new QuestionTypeResolver(),
                assetRepository,
                setVersionRepository,
                objectMapper,
                new ExplanationFingerprintBuilder(objectMapper, client));

        QuestionContent firstContent = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("draft_option_a", "Đáp án đúng"),
                        new QuestionContent.Option("draft_option_b", "Đáp án sai")),
                List.of());
        QuestionContent republishedContent = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("version_option_x", "Đáp án đúng"),
                        new QuestionContent.Option("version_option_y", "Đáp án sai")),
                List.of());
        AnswerSpec firstAnswerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("draft_option_a"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        AnswerSpec republishedAnswerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.SINGLE_CHOICE,
                List.of("version_option_x"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        when(codec.readQuestionContent(any(), eq(CanonicalQuestionType.SINGLE_CHOICE)))
                .thenReturn(firstContent, republishedContent);
        when(codec.readAnswerSpec(any(), any()))
                .thenReturn(firstAnswerSpec, republishedAnswerSpec);
        when(client.model()).thenReturn("model-v1");
        when(client.promptVersion()).thenReturn("prompt-v1");
        when(client.schemaVersion()).thenReturn("schema-v1");

        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getTitle()).thenReturn("Bộ đề đọc");
        when(setVersionRepository.findByPublishedVersionId(77L)).thenReturn(Optional.of(setVersion));
        LecturerAsset firstAsset = asset("a".repeat(64));
        LecturerAsset republishedAsset = asset("a".repeat(64));
        when(assetRepository.findById(7L)).thenReturn(Optional.of(firstAsset));
        when(assetRepository.findById(8L)).thenReturn(Optional.of(republishedAsset));

        PracticeSectionVersion section = mock(PracticeSectionVersion.class);
        when(section.getSkill()).thenReturn("READING");
        when(section.getInstructions()).thenReturn("Đọc đoạn văn rồi trả lời.");

        ExplanationInputFactory.PreparedExplanation first = factory.prepare(
                question(101L, 1001L, 7L), null, section);
        ExplanationInputFactory.PreparedExplanation repeated = factory.prepare(
                question(202L, 2002L, 8L), null, section);

        assertThat(repeated.fingerprint().fingerprint())
                .isEqualTo(first.fingerprint().fingerprint());
        assertThat(first.fingerprint().inputContractJson())
                .contains("a".repeat(64))
                .contains("option_1", "option_2")
                .doesNotContain(
                        "/practice/materials/7/content",
                        "questionId",
                        "questionVersionId",
                        "draft_option_a",
                        "draft_option_b");
        assertThat(repeated.fingerprint().inputContractJson())
                .doesNotContain(
                        "/practice/materials/8/content",
                        "version_option_x",
                        "version_option_y");
        assertThat(first.input().prompt()).isEqualTo("Chọn đáp án đúng.");
        assertThat(first.input().teacherExplanation()).isEqualTo("Gợi ý của giáo viên.");
        assertThat(first.runtimeMedia())
                .containsExactly(new ExplanationInputFactory.RuntimeMedia(
                        "question.image", "IMAGE", "/practice/materials/7/content"));
    }

    @Test
    void generatedBlankIdsAreExcludedFromEquivalentRepublishedContent() {
        AssessmentContractCodec codec = mock(AssessmentContractCodec.class);
        LecturerAssetRepository assetRepository = mock(LecturerAssetRepository.class);
        PracticeSetVersionRepository setVersionRepository = mock(PracticeSetVersionRepository.class);
        ReadingListeningExplanationClient client = mock(ReadingListeningExplanationClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ExplanationInputFactory factory = new ExplanationInputFactory(
                codec,
                new QuestionTypeResolver(),
                assetRepository,
                setVersionRepository,
                objectMapper,
                new ExplanationFingerprintBuilder(objectMapper, client));

        QuestionContent firstContent = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(),
                List.of(new QuestionContent.Blank("draft_blank_a", "서울은 ___입니다.")));
        QuestionContent republishedContent = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(),
                List.of(new QuestionContent.Blank("version_blank_x", "서울은 ___입니다.")));
        AnswerSpec firstAnswerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                List.of(new AnswerSpec.BlankAnswer("draft_blank_a", List.of("도시"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        AnswerSpec republishedAnswerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                CanonicalQuestionType.FILL_BLANK,
                List.of(),
                null,
                List.of(new AnswerSpec.BlankAnswer("version_blank_x", List.of("도시"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        when(codec.readQuestionContent(any(), eq(CanonicalQuestionType.FILL_BLANK)))
                .thenReturn(firstContent, republishedContent);
        when(codec.readAnswerSpec(any(), any()))
                .thenReturn(firstAnswerSpec, republishedAnswerSpec);
        when(client.model()).thenReturn("model-v1");
        when(client.promptVersion()).thenReturn("prompt-v1");
        when(client.schemaVersion()).thenReturn("schema-v1");

        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getTitle()).thenReturn("Bộ đề nghe");
        when(setVersionRepository.findByPublishedVersionId(77L)).thenReturn(Optional.of(setVersion));
        LecturerAsset firstAsset = asset("b".repeat(64));
        LecturerAsset republishedAsset = asset("b".repeat(64));
        when(assetRepository.findById(7L)).thenReturn(Optional.of(firstAsset));
        when(assetRepository.findById(8L)).thenReturn(Optional.of(republishedAsset));

        PracticeSectionVersion section = mock(PracticeSectionVersion.class);
        when(section.getSkill()).thenReturn("LISTENING");
        when(section.getInstructions()).thenReturn("Nghe rồi điền từ.");

        ExplanationInputFactory.PreparedExplanation first = factory.prepare(
                question(101L, 1001L, 7L, "FILL_BLANK"), null, section);
        ExplanationInputFactory.PreparedExplanation repeated = factory.prepare(
                question(202L, 2002L, 8L, "FILL_BLANK"), null, section);

        assertThat(repeated.fingerprint().fingerprint())
                .isEqualTo(first.fingerprint().fingerprint());
        assertThat(first.fingerprint().inputContractJson())
                .contains("blank_1")
                .doesNotContain("draft_blank_a");
        assertThat(repeated.fingerprint().inputContractJson())
                .doesNotContain("version_blank_x");
    }

    private static PracticeQuestionVersion question(
            Long questionId,
            Long questionVersionId,
            Long assetId) {
        return question(questionId, questionVersionId, assetId, "MCQ");
    }

    private static PracticeQuestionVersion question(
            Long questionId,
            Long questionVersionId,
            Long assetId,
            String questionType) {
        PracticeQuestionVersion question = mock(PracticeQuestionVersion.class);
        String reference = "/practice/materials/" + assetId + "/content";
        when(question.getQuestionId()).thenReturn(questionId);
        when(question.getId()).thenReturn(questionVersionId);
        when(question.getQuestionNo()).thenReturn(1);
        when(question.getPublishedVersionId()).thenReturn(77L);
        when(question.getQuestionType()).thenReturn(questionType);
        when(question.getQuestionContentJson()).thenReturn("{}");
        when(question.getAnswerSpecJson()).thenReturn("{}");
        when(question.getPrompt()).thenReturn("Chọn đáp án đúng. ![minh họa](" + reference + ")");
        when(question.getExplanation()).thenReturn("Gợi ý của giáo viên. " + reference);
        return question;
    }

    private static LecturerAsset asset(String digest) {
        LecturerAsset asset = mock(LecturerAsset.class);
        when(asset.isContentVerified()).thenReturn(true);
        when(asset.getStatus()).thenReturn("ACTIVE");
        when(asset.getSha256()).thenReturn(digest);
        when(asset.getMimeType()).thenReturn("image/png");
        when(asset.getFileSize()).thenReturn(128L);
        return asset;
    }
}
