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
        String actions = Files.readString(Path.of("src/main/resources/static/js/practice/manage-editor-actions.js"));
        String tree = Files.readString(Path.of("src/main/resources/static/js/practice/manage-editor-tree.js"));

        assertFalse(template.contains("essayTaskType || 'Q53'"));
        assertFalse(actions.contains("essayTaskType || 'Q53'"));
        assertFalse(tree.contains("essayTaskType || 'Q53'"));
    }

    @Test
    void editorPreservesMissingTaskDuringNormalizeAndMaterializesBlankOnlyForAuthoring() throws Exception {
        String state = Files.readString(Path.of("src/main/resources/static/js/practice/manage-editor-state.js"));
        String template = Files.readString(Path.of("src/main/resources/templates/practice/manage/editor.html"));

        assertTrue(state.contains("function applyWritingTaskState"));
        assertTrue(state.contains("question.essayTaskType = ''"));
        assertTrue(state.contains("question.essayTaskType === null && !shouldMaterializeBlank"));
        assertFalse(state.contains("q.essayTaskType = q.essayTaskType || ''"));
        assertTrue(template.contains("function applyWritingTaskState"));
        assertTrue(template.contains("question.essayTaskType = ''"));
        assertTrue(template.contains("question.essayTaskType === null && !shouldMaterializeBlank"));
    }
}
