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
    void authoring_and_import_are_not_offered_without_active_categories() throws IOException {
        String list = Files.readString(LIST_TEMPLATE);
        String form = Files.readString(FORM_TEMPLATE);

        assertThat(list)
                .contains("th:if=\"${!emptyDepartment and !emptyCategories}\"")
                .contains("Chưa có danh mục ngân hàng câu hỏi nào đang mở trong bộ môn")
                .contains("Vui lòng liên hệ trưởng bộ môn")
                .doesNotContain("Vui lòng liên hệ ADMIN");
        assertThat(form)
                .contains("th:if=\"${!emptyDepartment and !emptyCategories}\"")
                .contains("Chưa có danh mục ngân hàng câu hỏi nào đang mở trong bộ môn")
                .contains("Vui lòng liên hệ trưởng bộ môn")
                .contains("Danh mục ngân hàng câu hỏi")
                .doesNotContain("Vui lòng liên hệ ADMIN");
    }
}
