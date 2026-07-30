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
