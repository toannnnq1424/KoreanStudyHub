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
        assertFalse(html.contains("Điểm đánh giá câu viết"));
        assertTrue(html.contains("Điểm bài làm"));
        assertTrue(html.contains("r.maxScore || 10"));
        assertFalse(html.contains("rubricValue / 10.0"));
    }

    @Test
    void resultOverviewUsesCanonicalScoreSummary() throws IOException {
        String html = readResource("templates/practice/result.html");

        assertFalse(html.contains("'Overall Score'"));
        assertFalse(html.contains(">Overall Score<"));
        assertTrue(html.contains("result.score().primaryDisplay()"));
        assertTrue(html.contains("result.score().pointsDisplay()"));
        assertFalse(html.contains("matchedScore * 10"));
    }

    @Test
    void writingFragmentUsesPresenterScoreAndRetainedLegacyJsUsesRubricMaxScore() throws IOException {
        String fragment = readResource("templates/practice/result/writing.html");
        String js = readResource("static/js/practice.js");

        assertTrue(fragment.contains("task.score().pointsDisplay()"));
        assertTrue(fragment.contains("criterion.scoreDisplay()"));
        assertFalse(fragment.contains("matchedScore * 10"));
        assertTrue(js.contains("item.maxScore || 10"));
    }

    private static String readResource(String relativePath) throws IOException {
        return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
