package com.ksh.features.tests.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentTestFlowUiContractTest {

    @Test
    void class_list_preserves_its_scope_when_opening_a_read_only_detail() throws IOException {
        String template = read("src/main/resources/templates/student/class-tests.html");

        assertThat(template)
                .contains("@{|/my/classes/${view.classId()}/tests/${exam.id()}|}")
                .contains("@{|/my/classes/${view.classId()}/tests/${exam.id()}/start|}")
                .doesNotContain("@{|/my/tests/${exam.id()}|}");
    }

    @Test
    void detail_requires_an_explicit_post_to_start_and_explains_one_attempt() throws IOException {
        String template = read("src/main/resources/templates/tests/detail.html");
        String styles = read("src/main/resources/static/css/test-detail.css");

        assertThat(template)
                .contains("classScopeId != null ?")
                .contains("'/my/classes/' + classScopeId + '/tests/' + detail.id() + '/start'")
                .contains("'/my/classes/' + classScopeId + '/tests/' + detail.id() + '/take'")
                .contains("'/my/classes/' + classScopeId + '/tests/' + detail.id() + '/result/' + detail.attemptId()")
                .contains("method=\"post\"")
                .contains("Đồng hồ chỉ bắt đầu")
                .contains("chỉ cho phép một lượt làm");
        assertThat(styles)
                .contains("@media (max-width: 900px)")
                .contains("@media (max-width: 640px)")
                .contains("overflow-wrap: anywhere");
    }

    @Test
    void taking_screen_counts_down_from_the_absolute_server_deadline() throws IOException {
        String template = read("src/main/resources/templates/tests/take.html");
        String script = read("src/main/resources/static/js/test-take.js");

        assertThat(template).contains("data-deadline");
        assertThat(script)
                .contains("deadline - Date.now()")
                .contains("if (submitting) return;")
                .contains("function countUnanswered(form)")
                .contains("Bạn còn ' + unanswered + ' câu chưa trả lời")
                .contains("if (unanswered > 0 && !window.confirm(")
                .contains("Deadline submission is authoritative")
                .contains("Hết giờ — bài của bạn đang được nộp tự động.");
        assertThat(script.indexOf("if (unanswered > 0 && !window.confirm("))
                .isLessThan(script.indexOf("// Recompute from the absolute server deadline"));
        String deadlineFlow = script.substring(
                script.indexOf("// Recompute from the absolute server deadline"));
        assertThat(deadlineFlow)
                .contains("if (!submitting)", "doSubmit();")
                .doesNotContain("window.confirm");
    }

    @Test
    void result_and_review_gate_optional_sidebar_before_fragment_evaluation() throws IOException {
        String result = read("src/main/resources/templates/tests/result.html");
        String review = read("src/main/resources/templates/tests/review.html");

        assertThat(result)
                .contains("<th:block th:if=\"${view != null}\">")
                .doesNotContain("<div th:if=\"${view != null}\"\n       th:replace=");
        assertThat(review)
                .contains("<th:block th:if=\"${!review.lecturerView() and view != null}\">")
                .doesNotContain("<div th:if=\"${!review.lecturerView() and view != null}\"\n       th:replace=");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
