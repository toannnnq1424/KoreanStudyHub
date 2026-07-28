package com.ksh.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level contract for the progressive detail-tab enhancement.
 *
 * <p>The project has no JavaScript unit-test runner, so this pins the integration
 * seams that are otherwise easy to break during template refactors. Browser
 * behaviour remains a separate end-to-end validation concern.
 */
class DetailTabsContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void sharedOrchestratorUsesTheKshNamespaceAndKeepsHardNavigationFallback()
            throws Exception {
        String source = read("static/js/detail-tabs.js");

        assertThat(source)
                .contains("window.KshDetailTabs")
                .contains("ksh:detail-tab-loaded")
                .contains("credentials: 'same-origin'")
                .contains("navigationSequence")
                .contains("window.AbortController")
                .contains("requestId !== navigationSequence")
                .contains("window.location.href = url")
                .doesNotContain("UlpDetailTabs", "ulp:detail-tab-loaded");
    }

    @Test
    void everyAdoptedTemplateExposesOnePanelAndLoadsTheSharedScript() throws Exception {
        List<String> templates = List.of(
                "templates/admin/users-form.html",
                "templates/admin/departments-form.html",
                "templates/admin/settings-ai-form.html",
                "templates/classes/detail-settings.html");

        for (String template : templates) {
            String source = read(template);
            assertThat(source)
                    .as(template)
                    .containsOnlyOnce("id=\"tabPanel\"")
                    .contains("th:src=\"@{/js/detail-tabs.js}\"");
        }
    }

    @Test
    void testTabsOwnTheirLifecycleAndInviteHandlersAreDelegated() throws Exception {
        assertThat(read("static/js/test-detail-tabs.js"))
                .contains("data-ajax-tabs', 'owned'")
                .contains("detail-tab-loading")
                .contains("aria-busy")
                .contains("navigationSequence")
                .contains("window.AbortController")
                .contains("requestId !== navigationSequence");

        assertThat(read("static/js/invite-code.js"))
                .contains("document.addEventListener('click'")
                .contains("document.addEventListener('submit'")
                .contains("invite-regen-form");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(RESOURCES.resolve(relative), StandardCharsets.UTF_8);
    }
}
