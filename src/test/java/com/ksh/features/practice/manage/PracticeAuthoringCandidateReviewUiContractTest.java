package com.ksh.features.practice.manage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAuthoringCandidateReviewUiContractTest {

    @Test
    void reviewPageIsVietnameseResponsiveAndNotAFullDraftEditor() throws Exception {
        String template = read(
                "src/main/resources/templates/practice/manage/candidate-review.html");
        String css = read(
                "src/main/resources/static/css/practice/candidate-review.css");

        assertThat(template)
                .contains("Rà soát trước khi áp dụng")
                .contains("Xem như học viên")
                .contains("Áp dụng vào bản nháp")
                .contains("id=\"apply-candidate\" type=\"button\" class=\"btn-action btn-pri\" disabled")
                .contains("id=\"mark-ready\" type=\"button\" class=\"btn-action btn-sec\" disabled")
                .contains("Xuất bản vẫn là bước riêng")
                .doesNotContain("autosave");
        assertThat(css).contains("@media(max-width:980px)")
                .contains("@media(max-width:640px)");
    }

    @Test
    void reviewJavascriptUsesVersionDigestExplicitUuidAndFieldPointers()
            throws Exception {
        String javascript = read(
                "src/main/resources/static/js/practice/candidate-review.js");

        assertThat(javascript)
                .contains("candidateVersion: view.version")
                .contains("candidateDigest: view.contentDigest")
                .contains("crypto.randomUUID()")
                .contains("data-candidate-path")
                .contains("entry.supportedQuestionTypes")
                .contains("entry.requiredEvidence")
                .contains("const warningsAccepted = !hasWarnings()")
                .contains("Loại câu khỏi candidate")
                .contains("/learner-preview")
                .contains("/apply")
                .doesNotContain("/autosave")
                .doesNotContain("/publish")
                .doesNotContain("entry.questionTypes");
    }

    @Test
    void editorAndCandidateUseOnePreviewTemplateMapperAndRenderer()
            throws Exception {
        String editor = read(
                "src/main/resources/templates/practice/manage/editor.html");
        String review = read(
                "src/main/resources/templates/practice/manage/candidate-review.html");
        String renderer = read(
                "src/main/resources/static/js/practice/manage-draft-preview.js");

        String fragment =
                "practice/manage/fragments/draft-preview :: modal";
        assertThat(editor).contains(fragment)
                .contains("PracticeDraftPreview.renderModal(delivery)");
        assertThat(review).contains(fragment)
                .contains("manage-draft-preview.js");
        assertThat(renderer).contains("window.PracticeDraftPreview = { mapResponse, renderModal }");
    }

    @Test
    void excelCandidateHandoffNavigatesOnlyToReviewRoute() throws Exception {
        String template = read(
                "src/main/resources/templates/practice/manage/excel-import.html");
        String controller = read(
                "src/main/java/com/ksh/features/practice/manage/controller/PracticeAssessmentExcelController.java");

        assertThat(template)
                .contains("window.location.assign(result.reviewUrl)")
                .contains("/practice/manage/authoring-candidates/");
        assertThat(controller).contains("\"reviewUrl\"")
                .contains("/practice/manage/authoring-candidates/");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
