package com.ksh.features.gradebook;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GradebookUiContractTest {
    @Test
    void gradebookDerivesScoresAndSupportsTypedSelectableColumns() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/ksh/features/gradebook/service/ClassGradebookService.java"));
        String template = Files.readString(Path.of(
                "src/main/resources/templates/classes/detail-scores.html"));
        String script = Files.readString(Path.of(
                "src/main/resources/static/js/gradebook.js"));
        String styles = Files.readString(Path.of(
                "src/main/resources/static/css/gradebook.css"));
        String view = Files.readString(Path.of(
                "src/main/java/com/ksh/features/gradebook/dto/GradebookView.java"));

        assertThat(service)
                .contains("findAllByClassIdAndStatusOrderByJoinedAtDesc(classId, \"ACTIVE\")")
                .contains("findCompletedByTestIds")
                .contains("findAllBySubmissionIdIn")
                .doesNotContain("save(");
        assertThat(template)
                .contains("data-kind-filter")
                .contains("data-custom-average")
                .contains("data-column-key")
                .contains("displayScore()")
                .contains("displayMaxScore()")
                .contains("Trung bình /10");
        assertThat(script)
                .contains("normalized")
                .contains("is-selected")
                .contains("if (!custom.checked) custom.checked = true")
                .contains("data-column-key").doesNotContain("fetch(");
        assertThat(styles)
                .contains("table-layout:fixed")
                .contains(".gradebook-table-wrap{width:100%;max-width:100%;overflow:hidden}")
                .contains(".gradebook-card{width:100%;min-width:0")
                .contains("border-radius:0")
                .doesNotContain("min-width:900px")
                .doesNotContain("overflow:auto");
        assertThat(view).contains("toPlainString()");
    }
}
