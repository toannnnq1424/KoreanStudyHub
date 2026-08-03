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
    void subject_review_uses_labeled_native_filters_and_has_no_category_ui() throws IOException {
        String list = Files.readString(LIST_TEMPLATE);
        String review = Files.readString(SUBJECT_REVIEW_TEMPLATE);

        assertThat(list)
                .contains("for=\"qbLecturerQuery\"", "id=\"qbLecturerQuery\"")
                .contains("for=\"qbLecturerStatus\"", "id=\"qbLecturerStatus\"")
                .doesNotContain("question-bank.js");
        assertThat(review)
                .contains("for=\"qbLeaderQuery\"", "id=\"qbLeaderQuery\"")
                .contains("for=\"qbContributor\"", "id=\"qbContributor\"")
                .contains("<select class=\"qb-input\" id=\"qbContributor\"")
                .doesNotContain("category", "danh mục", "question-bank.js");
    }

    @Test
    void authoring_and_import_require_only_an_assigned_subject() throws IOException {
        String list = Files.readString(LIST_TEMPLATE);
        String form = Files.readString(FORM_TEMPLATE);

        assertThat(list)
                .contains("th:if=\"${!emptyDepartment}\"")
                .contains("Bạn chưa được gán mã môn")
                .contains("<th>Mã môn</th>")
                .doesNotContain("emptyCategories", "Danh mục ngân hàng câu hỏi");
        assertThat(form)
                .contains("th:if=\"${!emptyDepartment}\"")
                .contains("Bạn chưa được gán mã môn")
                .doesNotContain("emptyCategories", "Danh mục ngân hàng câu hỏi");
    }
}
