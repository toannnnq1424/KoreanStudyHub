package com.ksh.features.practice.manage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeEditorWritingTaskAuthoringTest {

    @Test
    void essayTaskSelectorHasBlankAndGeneralOptions() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/practice/manage/editor.html"));

        assertTrue(template.contains("id=\"q-essay-task\""));
        assertTrue(template.contains("<option value=\"\">"));
        assertTrue(template.contains("<option value=\"GENERAL\">"));
    }

    @Test
    void editorDoesNotSilentlyDefaultMissingTaskToQ53() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/practice/manage/editor.html"));

        assertFalse(template.contains("essayTaskType || 'Q53'"));
    }

    @Test
    void editorPreservesMissingTaskDuringNormalizeAndMaterializesBlankOnlyForAuthoring() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/practice/manage/editor.html"));

        assertTrue(template.contains("function applyWritingTaskState"));
        assertTrue(template.contains("question.essayTaskType = ''"));
        assertTrue(template.contains("question.essayTaskType === null && !shouldMaterializeBlank"));
        assertFalse(template.contains("q.essayTaskType = q.essayTaskType || ''"));
    }
}
