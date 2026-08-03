package com.ksh.features.practice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeLegacyRouteRetirementStaticContractTest {

    @Test
    void legacyLearnerRoutesAndRedirectHandlersStayRetired() throws Exception {
        String routes = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/web/PracticeRoutes.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/controller/PracticeController.java"));

        assertThat(routes)
                .doesNotContain("LEGACY_SET", "LEGACY_MODE", "LEGACY_ROOM",
                        "LEGACY_SUBMIT", "LEGACY_SUBMISSION_RESULT",
                        "LEGACY_SUBMISSION_RE_EVALUATE");
        assertThat(controller)
                .doesNotContain("legacyDetail(", "legacyDetailView(",
                        "legacyMode(", "legacyPlayer(", "legacySubmit(",
                        "legacyResult(", "legacyReEvaluate(");
    }
}
