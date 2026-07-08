package com.ksh.features.practice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PracticeResultWordingTest {

    private static final Path RESOURCE_ROOT = Path.of("src", "main", "resources");

    @Test
    void practiceJsUsesNeutralWritingRawScoreLabel() throws IOException {
        String js = readResource("static/js/practice.js");

        assertFalse(js.contains("Điểm TOPIK thô"));
        assertTrue(js.contains("Điểm câu viết theo bộ chấm"));
    }

    @Test
    void resultDetailUsesNeutralWritingTaskScoreLabel() throws IOException {
        String html = readResource("templates/practice/result-detail.html");

        assertFalse(html.contains("'Task Score'"));
        assertFalse(html.contains(">Task Score<"));
        assertTrue(html.contains("Điểm đánh giá câu viết"));
    }

    @Test
    void resultOverviewUsesFinalAttemptScoreLabel() throws IOException {
        String html = readResource("templates/practice/result.html");

        assertFalse(html.contains("'Overall Score'"));
        assertFalse(html.contains(">Overall Score<"));
        assertTrue(html.contains("Điểm tổng kết bài làm"));
    }

    private static String readResource(String relativePath) throws IOException {
        return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
