package com.ksh.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static contract scan for {@code class-announcement-form.js}, following the
 * precedent of {@link DeferredResubmitGuardTest} and
 * {@code FrontendMutationGuardContractTest}.
 *
 * <p><b>Why a scan and not a JS test.</b> This repository has no JavaScript test
 * infrastructure — no {@code package.json}, no jest/vitest/karma, no
 * {@code .test.js} anywhere. Introducing one is a separate decision, not
 * something to smuggle in with a test-coverage fix. So the behavioural
 * scenarios that need a DOM and a clipboard are covered here only where a scan
 * can genuinely prove the contract, and the rest are recorded in {@code tasks.md}
 * as explicitly out of automated scope rather than papered over.
 *
 * <p><b>What a scan can prove</b> are structural invariants of the source: which
 * API the script obtains paste handling through, how many submit listeners it
 * registers on the compose form, whether it re-drains flash payload, and that
 * the degraded fallback path still exists. Each of these has a documented way to
 * go wrong that leaves the file syntactically valid.
 *
 * <p><b>What it cannot prove</b> is runtime behaviour: that a pasted image
 * actually uploads, that the toolbar button opens a picker, that the submit
 * button's {@code disabled} property really flips, or that the fallback stays
 * editable when Quill is absent. Those need a browser. They are listed as
 * out-of-scope in the change's tasks rather than approximated here — a scan
 * asserting "the file contains the word disabled" would pass under a broken
 * implementation and is worse than no test.
 */
class AnnouncementFormScriptContractTest {

    private static final Path SCRIPT =
            Path.of("src/main/resources/static/js/class-announcement-form.js");

    /**
     * {@code window.LfQuill} exports only {@code isEmptyHtml}, {@code waitForQuill},
     * {@code createQuill}, and {@code rewriteDataImages}. {@code bindImagePaste}
     * and {@code uploadImage} are closure-private, so calling them yields
     * {@code undefined} at runtime — paste handling must be obtained by going
     * through {@code createQuill}, which registers it internally.
     *
     * <p>This is exactly the mistake the tasks file warns about, and it fails
     * silently: the editor still loads, and pasted images quietly persist as
     * {@code data:} URIs.
     */
    @Test
    void paste_handling_comes_from_createQuill_not_from_unreachable_exports()
            throws IOException {
        String code = stripComments(read());

        assertThat(code)
                .as("paste handling must be obtained through createQuill")
                .contains("LfQuill.createQuill(");
        assertThat(code)
                .as("bindImagePaste is closure-private and resolves to undefined")
                .doesNotContain("bindImagePaste");
        assertThat(code)
                .as("uploadImage is closure-private and resolves to undefined")
                .doesNotContain("LfQuill.uploadImage");
        // A bare `new window.Quill(...)` bypasses createQuill entirely and takes
        // the paste/image handlers with it.
        assertThat(code)
                .as("the board must not construct Quill directly, bypassing createQuill")
                .doesNotContain("new window.Quill(");
    }

    /**
     * The compose form must carry at most ONE submit listener — the
     * single-orchestrator rule in {@code .claude/rules/deferred-upload-on-save.md}.
     * A second independent listener is the documented double-submit hazard, and
     * it is easy to add by copying an upload gate from another form.
     */
    @Test
    void the_compose_form_registers_at_most_one_submit_listener() throws IOException {
        String code = stripComments(read());

        Matcher m = Pattern.compile("addEventListener\\(\\s*['\"]submit['\"]")
                .matcher(code);
        int listeners = 0;
        while (m.find()) {
            listeners++;
        }

        assertThat(listeners)
                .as("a second submit listener double-submits the compose form")
                .isLessThanOrEqualTo(1);
    }

    /**
     * Re-submitting from inside a submit handler must use {@code requestSubmit()},
     * not {@code submit()}. Native {@code submit()} skips every submit listener,
     * so a sibling handler that copies editor HTML into the hidden field never
     * runs and the post silently loses its content.
     */
    @Test
    void the_resubmit_uses_requestSubmit_so_other_listeners_still_run() throws IOException {
        String code = stripComments(read());

        assertThat(code).contains("requestSubmit()");
        assertThat(code)
                .as("form.submit() bypasses submit listeners and drops content")
                .doesNotContain("form.submit()");
    }

    /**
     * {@code notifications.js} is the sole owner of draining {@code #flash-data}.
     * A page script reading it fires a duplicate toast — the exact bug recorded
     * in {@code .claude/rules/flash-toast-drain.md}, which cost nine files.
     *
     * <p>{@code FlashDrainSingleOwnerTest} guards this repository-wide; asserting
     * it here too keeps the failure local and named when this specific file
     * regresses.
     */
    @Test
    void the_board_script_never_drains_flash_payload() throws IOException {
        // Comments are stripped first: the file's header deliberately DISCUSSES
        // #flash-data to explain that it never reads it, so a raw substring scan
        // would fail on the very documentation of the correct behaviour.
        String code = stripComments(read());

        assertThat(code).doesNotContain("flash-data");
        assertThat(code).doesNotContain("data-flash-");
        assertThat(code).doesNotContain("dataset.flash");
        assertThat(code).doesNotContain("flashSuccess");
    }

    /**
     * The degraded path must survive. When Quill fails to load the host element
     * becomes a plain {@code contentEditable} wired to the same hidden input, so
     * the form stays usable rather than silently submitting nothing.
     *
     * <p>A scan can confirm the branch still exists and still writes the hidden
     * input; it cannot confirm the field is genuinely editable in a browser.
     */
    @Test
    void the_contentEditable_fallback_still_exists_and_feeds_the_hidden_input()
            throws IOException {
        String code = stripComments(read());

        assertThat(code).contains("contentEditable");
        assertThat(code)
                .as("the fallback must write the submitted hidden field")
                .contains("hidden.value = host.innerHTML");
    }

    /** The class-scoped upload URL must be read from the DOM, never hardcoded. */
    @Test
    void the_upload_url_is_read_from_the_dom_because_it_is_class_scoped()
            throws IOException {
        String code = stripComments(read());

        assertThat(code).contains("getAttribute('data-image-url')");
        assertThat(code)
                .as("a hardcoded upload path would post to the wrong class")
                .doesNotContain("'/lecturer/classes/");
    }

    private static String read() throws IOException {
        return Files.readString(SCRIPT, StandardCharsets.UTF_8);
    }

    /**
     * Executable source with comments removed.
     *
     * <p>Necessary because this file documents its own constraints in prose: the
     * header explains why {@code bindImagePaste} is unreachable and why
     * {@code #flash-data} is never read. Scanning raw text would flag the
     * explanation of correct behaviour as a violation of it, so every
     * "must not contain" assertion runs against code only. The "must contain"
     * assertions use code too, so a requirement cannot be satisfied by a comment
     * that merely mentions the API.
     */
    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)^\\s*//.*$", "");
    }
}