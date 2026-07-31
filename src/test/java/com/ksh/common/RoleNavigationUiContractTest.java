package com.ksh.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RoleNavigationUiContractTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path CSS = Path.of("src/main/resources/static/css");

    @Test
    void accessDeniedPageOffersTheCurrentRoleItsOwnWorkspace() throws Exception {
        String error = Files.readString(TEMPLATES.resolve("error.html"));

        assertThat(error).contains(
                "hasRole('STUDENT')", "@{/my/classes}",
                "hasRole('LECTURER')", "@{/lecturer/classes}",
                "hasRole('LEADER')", "@{/leader}",
                "hasRole('ADMIN')", "@{/admin/dashboard}");
        assertThat(error).doesNotContain(">Forbidden<");
        assertThat(Files.readString(CSS.resolve("error-page.css")))
                .contains(".error-card", "@media (max-width: 560px)");
    }

    @Test
    void desktopAndCompactNavigationKeepPracticeAreasRoleScoped() throws Exception {
        String header = Files.readString(
                TEMPLATES.resolve("fragments/app-header.html"));
        String sidebar = Files.readString(
                TEMPLATES.resolve("fragments/practice-sidebar.html"));

        assertThat(header).contains(
                "hasAnyRole('STUDENT','LECTURER')",
                "hasRole('STUDENT')\" th:href=\"@{/practice/progress}",
                "hasRole('LECTURER')\" th:href=\"@{/practice/manage}");
        assertThat(sidebar).contains(
                "hasRole('STUDENT')",
                "@{/practice/preferences}",
                "hasRole('LECTURER')",
                "@{/practice/manage/revisions}");
    }
}
