package com.ksh.features.questionbank.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionBankLayoutFrontendContractTest {

    private static final Path LIST_TEMPLATE =
            Path.of("src/main/resources/templates/questionbank/list.html");
    private static final Path DETAIL_TEMPLATE =
            Path.of("src/main/resources/templates/questionbank/detail.html");
    private static final Path FORM_TEMPLATE =
            Path.of("src/main/resources/templates/questionbank/form.html");
    private static final Path FORM_SCRIPT =
            Path.of("src/main/resources/static/js/question-bank-form.js");
    private static final Path STYLES =
            Path.of("src/main/resources/static/css/question-bank.css");

    @Test
    void detail_page_is_bounded_and_the_correct_answer_badge_stays_intrinsic() throws IOException {
        String template = Files.readString(DETAIL_TEMPLATE);
        String styles = Files.readString(STYLES);

        assertThat(template)
                .contains("class=\"qb-page detail-page qb-detail-page\"")
                .contains("class=\"detail-card qb-detail-card\"")
                .contains("th:classappend=\"${option.correct()} ? ' is-correct'\"")
                .contains(">Đáp án đúng</span>");
        assertThat(ruleBody(styles, ".qb-detail-page"))
                .contains("max-width: 960px");
        assertThat(ruleBody(styles, ".qb-detail-card .qb-option-body > .status-pill"))
                .contains("justify-self: start")
                .contains("width: max-content")
                .contains("max-width: 100%");
    }

    @Test
    void import_preview_pads_copy_but_keeps_the_scroll_table_full_bleed() throws IOException {
        String template = Files.readString(LIST_TEMPLATE);
        String styles = Files.readString(STYLES);

        assertThat(template)
                .contains("class=\"admin-list-panel qb-import-panel\"")
                .contains("class=\"qb-import-head\"")
                .contains("class=\"qb-import-summary\"")
                .contains("class=\"qb-import-empty\"")
                .contains("class=\"qb-import-table-wrap admin-list-table-scroll\"");
        assertThat(ruleBody(styles, ".qb-import-panel .qb-import-head"))
                .contains("padding: 1rem 1.125rem 0");
        assertThat(ruleBody(styles, ".qb-import-panel .qb-import-summary"))
                .contains("padding-inline: 1.125rem");
        assertThat(ruleBody(styles, ".qb-import-panel .qb-import-empty"))
                .contains("padding: 0 1.125rem 1rem");
        assertThat(ruleBody(styles, ".qb-import-table-wrap"))
                .contains("overflow-x: auto")
                .doesNotContain("padding");
    }

    @Test
    void subject_catalog_is_row_based_and_question_index_is_high_density() throws IOException {
        String template = Files.readString(LIST_TEMPLATE);
        String styles = Files.readString(STYLES);

        assertThat(template)
                .contains("role=\"table\"")
                .contains("class=\"qb-catalog-row\"")
                .contains("class=\"qb-question-table\"")
                .contains("class=\"qb-page-size\"")
                .contains("name=\"size\"")
                .contains("itemPage.number + 1")
                .contains("data-qb-open-detail")
                .contains("data-qb-detail-drawer")
                .doesNotContain("class=\"qb-subject-rail\"")
                .doesNotContain("class=\"qb-question-card\"");
        assertThat(styles)
                .contains(".qb-catalog-row")
                .contains(".qb-question-table")
                .contains("border-collapse: collapse")
                .contains("table-layout: fixed")
                .contains(".qb-drawer-panel")
                .contains("position: absolute")
                .contains("right: 0");
        assertThat(styles)
                .contains(".qb-generator [data-scope-field][hidden]")
                .contains(".qb-generator .qb-generator-classes .ksh-checklist-options")
                .contains("grid-template-columns: 1fr")
                .contains(".qb-drawer-panel.is-editor")
                .contains("margin-top: 18px");
    }

    @Test
    void single_question_form_is_flat_progressive_and_uses_the_full_page_background() throws IOException {
        String template = Files.readString(FORM_TEMPLATE);
        String script = Files.readString(FORM_SCRIPT);
        String styles = Files.readString(STYLES);

        assertThat(template)
                .contains("<body class=\"qb-body\">")
                .contains("class=\"qb-form qb-form-surface\"")
                .contains("class=\"qb-optional-explanation\"")
                .contains("data-qb-option-row")
                .contains("data-qb-option-editor")
                .contains("data-qb-option-add")
                .doesNotContain("<section class=\"lf-panel\"");
        assertThat(script)
                .contains("initOptionEditor")
                .contains("initProgressiveOptions");
        assertThat(ruleBody(styles, ".qb-body"))
                .contains("min-height: 100vh")
                .contains("background:");
    }

    private static String ruleBody(String styles, String selector) {
        int selectorStart = styles.indexOf(selector);
        assertThat(selectorStart)
                .as("CSS selector %s must exist", selector)
                .isGreaterThanOrEqualTo(0);
        int bodyStart = styles.indexOf('{', selectorStart);
        int bodyEnd = styles.indexOf('}', bodyStart);
        assertThat(bodyStart).isGreaterThan(selectorStart);
        assertThat(bodyEnd).isGreaterThan(bodyStart);
        return styles.substring(bodyStart + 1, bodyEnd);
    }
}
