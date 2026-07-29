package com.ksh.features.classes.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StudentClassesFrontendContractTest {

    private static final Path TEMPLATE =
            Path.of("src/main/resources/templates/student/my-classes.html");
    private static final Path STYLES =
            Path.of("src/main/resources/static/css/student-classes.css");
    private static final Path SCRIPT =
            Path.of("src/main/resources/static/js/student-classes.js");

    @Test
    void student_grid_overrides_target_the_same_shell_element() throws IOException {
        String template = Files.readString(TEMPLATE);
        String styles = Files.readString(STYLES);

        assertThat(template).contains("<main class=\"class-page my-classes-shell\">");
        assertThat(styles)
                .contains(".class-page.my-classes-shell {")
                .contains(".class-page.my-classes-shell .list-head,")
                .contains(".class-page.my-classes-shell .student-class-row")
                .doesNotContain(".class-page .my-classes-shell");
    }

    @Test
    void search_and_sort_are_scoped_to_the_active_class_list() throws IOException {
        String template = Files.readString(TEMPLATE);
        String script = Files.readString(SCRIPT);

        assertThat(template)
                .contains("id=\"active-class-list\"")
                .contains("class=\"class-row student-class-row\"")
                .contains("class=\"class-row student-class-row is-pending\"")
                .doesNotContain("id=\"viewToggle\"");
        assertThat(script)
                .contains("document.getElementById('active-class-list')")
                .contains("listContainer.querySelectorAll('.student-class-row:not(.is-pending)')")
                .doesNotContain("getElementById('viewToggle')");
    }
}
