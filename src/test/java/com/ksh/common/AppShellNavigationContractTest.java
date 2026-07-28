package com.ksh.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppShellNavigationContractTest {

    private static final Path QUESTION_BANK_TEMPLATES =
            Path.of("src/main/resources/templates/questionbank");

    @Test
    void lecturerQuestionBankPagesSelectTheirOwnPrimaryNavigationItem() throws Exception {
        for (String templateName : List.of("list.html", "detail.html", "form.html")) {
            String template = Files.readString(QUESTION_BANK_TEMPLATES.resolve(templateName));

            assertTrue(
                    template.contains("appHeader('question-bank')"),
                    templateName + " must select the question-bank navigation item");
            assertFalse(
                    template.contains("appHeader('classes')"),
                    templateName + " must not select the classes navigation item");
        }
    }

    @Test
    void canonicalLeaderRoleKeepsItsDedicatedBadgeStyle() throws Exception {
        String css = Files.readString(
                Path.of("src/main/resources/static/css/detail-page.css"));
        String users = Files.readString(
                Path.of("src/main/resources/templates/admin/users.html"));
        String userForm = Files.readString(
                Path.of("src/main/resources/templates/admin/users-form.html"));

        assertTrue(css.contains(".role-badge.role-leader"));
        assertFalse(css.contains(".role-badge.role-head"));
        assertTrue(users.contains("#strings.toLowerCase(row.role)"));
        assertTrue(userForm.contains("#strings.toLowerCase(targetUser.role.name())"));
    }
}
