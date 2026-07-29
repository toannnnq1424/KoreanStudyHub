package com.ksh.features.classes.imports;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database-free source contract for the import-students modal upload lifecycle.
 *
 * <p>The project has no JavaScript unit-test runner, so this pins the request
 * identity and dialog lifecycle seams that prevent an older preview response
 * from replacing the session selected by a newer modal interaction.
 */
class ImportExcelUploadRaceContractTest {

    private static final Path SCRIPT =
            Path.of("src/main/resources/static/js/import-excel.js");

    @Test
    void close_reopen_and_new_upload_invalidate_the_previous_request() throws IOException {
        String source = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        String invalidation = between(source,
                "function invalidateUpload()", "function openModal()");
        String open = between(source, "function openModal()", "function closeModal()");
        String close = between(source, "function closeModal()", "function resetForm()");
        String lifecycle = between(source,
                "function bindModalLifecycle()", "function bindFileInputAndDropZone()");
        String upload = between(source, "function doUpload(file)", "function renderPreview");

        assertThat(source)
                .contains("uploadGeneration: 0")
                .contains("uploadController: null");
        assertThat(invalidation)
                .contains("state.uploadGeneration += 1")
                .contains("state.uploadController.abort()")
                .contains("state.uploadController = null");
        assertThat(open).contains("invalidateUpload();");
        assertThat(close).contains("invalidateUpload();");
        assertThat(lifecycle)
                .contains("addEventListener('click', invalidateUpload, true)")
                .contains("addEventListener('cancel', invalidateUpload)")
                .contains("addEventListener('close', invalidateUpload)");
        assertThat(upload)
                .contains("invalidateUpload();")
                .contains("var requestGeneration = state.uploadGeneration")
                .contains("new window.AbortController()")
                .contains("requestInit.signal = controller.signal");
    }

    @Test
    void stale_upload_callbacks_cannot_replace_the_current_session_or_button_state()
            throws IOException {
        String source = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        String requestWrapper = between(source,
                "function postAndHandle(", "function doUpload(file)");
        String upload = between(source, "function doUpload(file)", "function renderPreview");
        String confirm = between(source, "function doConfirm()", "function renderSummary");

        assertThat(requestWrapper)
                .contains("if (isCurrent && !isCurrent()) return;")
                .contains("if ((isCurrent && !isCurrent()) || (err && err.name === 'AbortError')) return;");
        assertThat(requestWrapper.indexOf("if (isCurrent && !isCurrent()) return;"))
                .as("stale responses are discarded before errors, button resets, or onOk")
                .isLessThan(requestWrapper.indexOf("if (out.status !== 200)"));
        assertThat(upload)
                .contains("requestGeneration === state.uploadGeneration")
                .contains("state.uploadController === controller")
                .contains("}, isCurrentUpload).then(function ()")
                .contains("state.sessionId = body.sessionId")
                .contains("showStep('step2')");

        assertThat(confirm)
                .contains("if (!state.sessionId) return;")
                .contains("encodeURIComponent(state.sessionId) + '/confirm'")
                .contains("postAndHandle(url")
                .doesNotContain("invalidateUpload()");
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertThat(start).as("start marker %s", startMarker).isGreaterThanOrEqualTo(0);
        assertThat(end).as("end marker %s", endMarker).isGreaterThan(start);
        return source.substring(start, end);
    }
}
