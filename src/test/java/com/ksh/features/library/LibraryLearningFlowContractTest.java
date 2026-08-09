package com.ksh.features.library;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Static route/UI ownership contract for the non-Practice Library flow. */
class LibraryLearningFlowContractTest {

    @Test
    void library_is_the_only_lesson_authoring_surface() throws IOException {
        String libraryController = readJava(
                "features/library/controller/LessonTemplateController.java");
        String classTabController = readJava(
                "features/lessons/controller/LessonsTabController.java");

        assertThat(libraryController).contains(
                "@GetMapping(\"/new\")",
                "@PostMapping",
                "@PostMapping(\"/subjects/{subjectId}/distribute\")");
        assertThat(classTabController)
                .contains("return \"student/class-lessons\"")
                .doesNotContain("redirect:/my/classes/")
                .doesNotContain("@PostMapping", "@DeleteMapping", "classes/detail-lessons");

        Path classTemplates = Path.of("src/main/resources/templates/classes");
        assertThat(classTemplates.resolve("lesson-form.html")).doesNotExist();
        assertThat(classTemplates.resolve("section-form.html")).doesNotExist();
        assertThat(classTemplates.resolve("detail-lessons.html")).doesNotExist();
    }

    @Test
    void library_ui_exposes_subject_hierarchy_and_no_loose_attach_wizard()
            throws IOException {
        String index = readResource("templates/library/index.html");
        String form = readResource("templates/library/lesson-form.html");

        assertThat(index).contains(
                "mã môn",
                "Chương",
                "Bài học",
                "file",
                "name=\"classIds\"",
                "Phân phối bài giảng",
                "library-subject-tree",
                "data-library-editor-dialog",
                "data-inline-edit",
                "library-drag-handle",
                "clazz.alreadyDistributed()",
                "/js/library-inline.js")
                .doesNotContain("library-distribute-form", "<select multiple");
        assertThat(form).contains(
                "th:field=\"*{chapterNumber}\"",
                "th:field=\"*{chapterTitle}\"",
                "th:field=\"*{title}\"",
                "enctype=\"multipart/form-data\"",
                "th:field=\"*{materialUploads}\"",
                "name=\"materialAssetIds\"",
                "data-library-file-dropzone",
                "Kéo thả file vào đây",
                "multiple");
        String inlineScript = readResource("static/js/library-inline.js");
        assertThat(inlineScript).contains(
                "new DataTransfer()",
                "dataTransfer.files",
                "uploadForm(form",
                "data-library-file-list",
                "library-selected-file-remove",
                "chapterNumbers");
        assertThat(index + form).doesNotContain(
                "libraryAttachWizard",
                "attach-to-class",
                "library-attach-wizard.js",
                "/lecturer/library/upload",
                "library.js",
                "Gắn vào lớp",
                "Thêm vào lớp");
    }

    @Test
    void class_scoped_lesson_mutation_controllers_are_absent() {
        Path controllerDir = Path.of(
                "src/main/java/com/ksh/features/lessons/controller");

        for (String name : new String[]{
                "LessonsController.java",
                "SectionsController.java",
                "LessonsApiController.java",
                "SectionsApiController.java",
                "LessonContentApiController.java",
                "LessonCloneController.java",
                "LessonsLifecycleController.java"}) {
            assertThat(controllerDir.resolve(name)).as(name).doesNotExist();
        }
    }

    private static String readJava(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/ksh").resolve(relative));
    }

    private static String readResource(String relative) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relative));
    }
}
