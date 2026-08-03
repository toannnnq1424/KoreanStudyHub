package com.ksh.features.questionbank.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionBankFrontendContractTest {

    private static final Path IMPORT_SCRIPT =
            Path.of("src/main/resources/static/js/question-bank-import.js");
    private static final Path LIST_TEMPLATE =
            Path.of("src/main/resources/templates/questionbank/list.html");
    private static final Path FORM_TEMPLATE =
            Path.of("src/main/resources/templates/questionbank/form.html");
    private static final Path SUBJECT_REVIEW_TEMPLATE =
            Path.of("src/main/resources/templates/questionbank/subject-review.html");

    @Test
    void multipart_preview_sends_the_spring_csrf_header() throws IOException {
        String script = Files.readString(IMPORT_SCRIPT);
        int previewStart = script.indexOf("function uploadPreview(file)");
        int previewEnd = script.indexOf("function renderPreview(data)");
        assertThat(previewStart).isGreaterThanOrEqualTo(0);
        assertThat(previewEnd).isGreaterThan(previewStart);

        String previewFlow = script.substring(previewStart, previewEnd);
        assertThat(previewFlow)
                .contains("headers[csrfHeader.content] = csrfToken.content")
                .contains("headers: headers")
                .contains("credentials: 'same-origin'");
    }

    @Test
    void workspace_uses_clickable_subjects_and_two_explicit_banks() throws IOException {
        String list = Files.readString(LIST_TEMPLATE);
        String review = Files.readString(SUBJECT_REVIEW_TEMPLATE);

        assertThat(list)
                .contains("for=\"qbLecturerQuery\"", "id=\"qbLecturerQuery\"")
                .contains("qb-subject-rail", "Bộ chung đã duyệt", "Chờ leader duyệt")
                .contains("name=\"scope\"", "name=\"lessonTemplateId\"", "name=\"classIds\"")
                .doesNotContain("question-bank.js");
        assertThat(review)
                .contains("for=\"qbLeaderQuery\"", "id=\"qbLeaderQuery\"")
                .contains("for=\"qbContributor\"", "id=\"qbContributor\"")
                .contains("<select class=\"qb-input\" id=\"qbContributor\"")
                .doesNotContain("category", "danh mục", "question-bank.js");
    }

    @Test
    void authoring_and_import_require_an_active_catalog_not_an_account_assignment() throws IOException {
        String list = Files.readString(LIST_TEMPLATE);
        String form = Files.readString(FORM_TEMPLATE);

        assertThat(list)
                .contains("th:if=\"${!emptyDepartment}\"")
                .contains("Chưa có mã môn đang hoạt động", "qb-subject-rail")
                .doesNotContain("Bạn chưa được gán mã môn")
                .doesNotContain("emptyCategories", "Danh mục ngân hàng câu hỏi");
        assertThat(form)
                .contains("th:if=\"${!emptyDepartment}\"")
                .contains("Chưa có mã môn đang hoạt động")
                .doesNotContain("Bạn chưa được gán mã môn")
                .doesNotContain("emptyCategories", "Danh mục ngân hàng câu hỏi");
    }
}
