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
                "aria-controls=\"libraryDistribution\"",
                "data-library-share-close",
                "library-subject-tree",
                "data-library-editor-dialog",
                "data-inline-edit",
                "library-drag-handle",
                "clazz.alreadyDistributed()",
                "data-library-share",
                "libraryDistribution\" class=\"library-subject-distribution library-subject-distribution--flat",
                "aria-labelledby=\"subjectDistributionTitle\" hidden",
                "library-distribution-close",
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
                "multiple",
                "data-library-cancel-url",
                "quill@2.0.3/dist/quill.js",
                "/js/library-inline.js");
        String inlineScript = readResource("static/js/library-inline.js");
        assertThat(inlineScript).contains(
                "new DataTransfer()",
                "dataTransfer.files",
                "uploadForm(form",
                "data-library-file-list",
                "library-selected-file-remove",
                "closeDistribution()",
                "focusTarget?.focus()",
                "chapterNumbers",
                "editorRequestSequence",
                "new window.AbortController()",
                "requestId !== editorRequestSequence",
                "cancelEditorRequest()",
                "aria-expanded",
                "message.textContent = error.message",
                "form.dataset.libraryEditorReady",
                "const inlineDialogForm = Boolean(dialog && dialog.contains(form))",
                "if (!inlineDialogForm)",
                "window.location.assign(cancelUrl",
                "acceptFiles(form, event.dataTransfer.files)");
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

    @Test
    void shared_class_lesson_view_keeps_completion_student_only_and_authoring_library_only()
            throws IOException {
        String sharedLessonView = readResource("templates/student/class-lessons.html");
        String classTabController = readJava(
                "features/lessons/controller/LessonsTabController.java");
        String libraryController = readJava(
                "features/library/controller/LessonTemplateController.java");

        assertThat(sharedLessonView).contains(
                "class=\"student-lesson-completion-form\"",
                "th:if=\"${!teachingView}\"",
                "method=\"post\"",
                "/progress/toggle");
        assertThat(classTabController)
                .contains("@GetMapping", "return \"student/class-lessons\"")
                .doesNotContain("@PostMapping", "@PutMapping", "@PatchMapping", "@DeleteMapping");
        assertThat(libraryController).contains(
                "@GetMapping(\"/new\")",
                "@PostMapping",
                "@PostMapping(\"/subjects/{subjectId}/distribute\")");
    }

    @Test
    void shared_lesson_workspace_exposes_server_timed_checklist_and_real_neighbour_navigation()
            throws IOException {
        String lessonView = readResource("templates/student/class-lessons.html");
        String navigation = readResource("static/js/student-lesson-nav.js");
        String lessonCss = readResource("static/css/student-lesson-detail.css");

        assertThat(lessonView).contains(
                "data-lesson-workspace",
                "data-tracking-enabled=${!teachingView}",
                "/progress/checkpoint",
                "data-engagement-tab=\"CONTENT\"",
                "data-engagement-tab=\"VIDEO\"",
                "data-engagement-tab=\"ATTACHMENTS\"",
                "data-lesson-checkpoint=\"CONTENT\"",
                "data-lesson-checkpoint=\"VIDEO\"",
                "data-lesson-checkpoint=\"ATTACHMENTS\"",
                "lessonEngagement.content().seconds()",
                "lessonEngagement.video().seconds()",
                "lessonEngagement.attachments().seconds()",
                "student-lesson-quick-download-list",
                "lessonDetail.pdfDownloadUrl()",
                "th:href=\"${att.downloadUrl()}\"",
                "th:text=\"${att.filename()}\"",
                "data-lesson-previous",
                "data-lesson-next",
                "data-lesson-engagement-announcement",
                "aria-live=\"polite\"");

        assertThat(navigation).contains(
                "body.set('active', String(Boolean(active)))",
                "document.visibilityState === 'hidden'",
                "visibilitychange",
                "navigator.sendBeacon",
                "function announceEngagementTransition",
                "function renderProgressActivityState",
                "Tab không áp dụng",
                "Mục này đã đủ",
                "data-lesson-tab-target",
                ".student-lessons-outline-link",
                "aria-current");
        assertThat(navigation).doesNotContain(
                "body.set('seconds'",
                "body.set('elapsed'");

        assertThat(lessonCss).contains(
                "width: min(100%, 780px)");
        String workspaceCss = readResource("static/css/student-lessons.css");
        assertThat(workspaceCss).contains(
                "grid-template-columns: repeat(auto-fit, minmax(220px, 1fr))");
    }

    @Test
    void lesson_engagement_heartbeat_resumes_once_after_back_forward_cache_restore()
            throws IOException {
        String navigation = readResource("static/js/student-lesson-nav.js");

        assertThat(navigation).contains(
                "workspace.dataset.engagementTrackerInitialized = 'true';",
                "function heartbeat()",
                "function stopHeartbeatTimer()",
                "function startHeartbeatTimer()",
                "window.addEventListener('pagehide'",
                "window.addEventListener('pageshow'",
                "if (!event.persisted || !trackingSuspended) return;",
                "activeKey = engagementKey(selectedTab(tabList));",
                "timer = window.setInterval(heartbeat, interval);");
        assertThat(navigation)
                .containsOnlyOnce("window.setInterval(heartbeat, interval)")
                .containsOnlyOnce("window.addEventListener('pageshow'")
                .doesNotContain(
                        "body.set('seconds'",
                        "body.set('elapsed'");
    }

    @Test
    void library_form_exposes_optional_video_summary_with_the_declared_boundary()
            throws IOException {
        String lessonForm = readResource("templates/library/lesson-form.html");

        assertThat(lessonForm).contains(
                "th:field=\"*{videoSummary}\"",
                "maxlength=\"1000\"",
                "Tóm tắt nội dung video",
                "không bắt buộc");
    }

    @Test
    void library_lesson_rows_show_stable_uploader_name_and_id() throws IOException {
        String index = readResource("templates/library/index.html");

        assertThat(index).contains(
                "item.uploaderDisplayName()",
                "item.uploaderUserId()",
                "Tải lên bởi");
    }

    private static String readJava(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/ksh").resolve(relative));
    }

    private static String readResource(String relative) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relative));
    }
}
