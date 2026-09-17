package com.ksh.features.practice.result;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class UndetectedCriteriaTemplateTest {
    @Test
    void bothSkillsExposeUndetectedCriteriaWithoutClaimingScores() throws Exception {
        for (String skill : new String[]{"writing", "speaking"}) {
            String template = Files.readString(Path.of("src/main/resources/templates/practice/result-detail-" + skill + ".html"));
            assertThat(template).contains(skill + "-tab-undetected-", skill + "-panel-undetected-",
                    "Các tiêu chí chưa được phát hiện", "chip.count() == 0",
                    "group.strengthChips()", "group.needsImprovementChips()",
                    "không có nghĩa là đã đạt");
        }
    }
}
