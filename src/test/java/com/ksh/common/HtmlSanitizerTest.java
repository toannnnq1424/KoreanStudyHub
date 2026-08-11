package com.ksh.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HtmlSanitizer}. The policy is documented in
 * the {@code add-lesson-crud} design D2 — verify the four canonical
 * payloads from the spec (script tag, event handler, data-URI image,
 * {@code javascript:} URL).
 */
class HtmlSanitizerTest {

    @Test
    void blank_input_returns_empty_string() {
        assertThat(HtmlSanitizer.sanitize(null)).isEmpty();
        assertThat(HtmlSanitizer.sanitize("")).isEmpty();
        assertThat(HtmlSanitizer.sanitize("   ")).isEmpty();
    }

    @Test
    void script_tag_is_stripped() {
        String cleaned = HtmlSanitizer.sanitize("<p>Safe</p><script>alert(1)</script>");
        assertThat(cleaned).contains("<p>Safe</p>");
        assertThat(cleaned).doesNotContainIgnoringCase("<script");
        assertThat(cleaned).doesNotContain("alert(1)");
    }

    @Test
    void event_handler_attribute_is_stripped() {
        String cleaned = HtmlSanitizer.sanitize("<p onclick=\"evil()\">Click</p>");
        assertThat(cleaned).contains("<p>Click</p>");
        assertThat(cleaned).doesNotContainIgnoringCase("onclick");
    }

    @Test
    void javascript_url_is_dropped() {
        String cleaned = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>");
        assertThat(cleaned).doesNotContainIgnoringCase("javascript:");
    }

    @Test
    void data_uri_image_is_preserved() {
        String html = "<p>See <img src=\"data:image/png;base64,iVBORw0KGgoAAAA\" alt=\"x\"></p>";
        String cleaned = HtmlSanitizer.sanitize(html);
        assertThat(cleaned).contains("data:image/png;base64,iVBORw0KGgoAAAA");
        assertThat(cleaned).contains("alt=\"x\"");
    }

    @Test
    void relative_upload_image_src_is_preserved() {
        String html = "<p><img src=\"/uploads/exams/abc.png\" alt=\"q\"></p>";
        String cleaned = HtmlSanitizer.sanitize(html);
        assertThat(cleaned).contains("src=\"/uploads/exams/abc.png\"");
        assertThat(cleaned).contains("alt=\"q\"");
    }

    @Test
    void http_link_with_target_is_preserved() {
        String cleaned = HtmlSanitizer.sanitize(
                "<a href=\"https://example.com\" target=\"_blank\">site</a>");
        assertThat(cleaned).contains("href=\"https://example.com\"");
        assertThat(cleaned).contains("target=\"_blank\"");
    }

    @Test
    void sanitize_is_idempotent_across_block_paragraphs() {
        // Round-trip test: sanitizing the same HTML twice must yield the
        // exact same string. Guards against the Jsoup prettyPrint drift bug
        // where each call inserted \n between <p> tags, causing Quill to
        // later re-parse them as empty paragraphs and accumulate blank lines.
        String input = "<p>First paragraph</p><p>Second paragraph</p>";
        String once = HtmlSanitizer.sanitize(input);
        String twice = HtmlSanitizer.sanitize(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void sanitize_does_not_insert_newlines_between_blocks() {
        // Block elements must sit directly next to each other — no \n,
        // no indentation. Whitespace between blocks gets re-parsed by Quill
        // as empty paragraphs on the next form load.
        String cleaned = HtmlSanitizer.sanitize(
                "<p>A</p><p>B</p><h2>C</h2><ul><li>D</li></ul>");
        assertThat(cleaned).doesNotContain("\n");
        assertThat(cleaned).doesNotContain("  ");
    }

    @Test
    void quill_bullet_list_becomes_real_unordered_list() {
        // Quill 2.x emits bullets as <ol><li data-list="bullet">; the marker
        // attribute is stripped by the safelist, so without normalisation the
        // surviving <ol> would render bullets as a numbered list.
        String cleaned = HtmlSanitizer.sanitize(
                "<ol><li data-list=\"bullet\">Đọc trước bài 5</li>"
                        + "<li data-list=\"bullet\">Nộp bài tập trước thứ Sáu</li></ol>");
        assertThat(cleaned).isEqualTo(
                "<ul><li>Đọc trước bài 5</li>"
                        + "<li>Nộp bài tập trước thứ Sáu</li></ul>");
    }

    @Test
    void quill_ordered_list_stays_ordered() {
        String cleaned = HtmlSanitizer.sanitize(
                "<ol><li data-list=\"ordered\">First</li>"
                        + "<li data-list=\"ordered\">Second</li></ol>");
        assertThat(cleaned).isEqualTo("<ol><li>First</li><li>Second</li></ol>");
    }

    @Test
    void mixed_list_splits_into_one_element_per_run() {
        // Quill merges adjacent lists of different kinds into ONE <ol> and
        // marks the kind per item. Keeping it as a single <ol> would render
        // the bullet run as numbers, so it must split into <ul> then <ol>.
        String cleaned = HtmlSanitizer.sanitize(
                "<ol><li data-list=\"bullet\">A</li>"
                        + "<li data-list=\"ordered\">B</li></ol>");
        assertThat(cleaned).isEqualTo("<ul><li>A</li></ul><ol><li>B</li></ol>");
    }

    @Test
    void real_quill_mixed_payload_splits_preserving_order() {
        // Verbatim Quill 2.0.3 serialization of a bullet list followed by a
        // numbered list, captured from the live editor — including the
        // injected ql-ui span. Regression guard for the reported defect.
        String cleaned = HtmlSanitizer.sanitize(
                "<p>Kiem tra bullet sau khi sua:</p><ol>"
                        + "<li data-list=\"bullet\"><span class=\"ql-ui\" contenteditable=\"false\"></span>Gach dau dong mot</li>"
                        + "<li data-list=\"bullet\"><span class=\"ql-ui\" contenteditable=\"false\"></span>Gach dau dong hai</li>"
                        + "<li data-list=\"ordered\"><span class=\"ql-ui\" contenteditable=\"false\"></span>Danh so mot</li>"
                        + "<li data-list=\"ordered\"><span class=\"ql-ui\" contenteditable=\"false\"></span>Danh so hai</li>"
                        + "</ol>");
        assertThat(cleaned).isEqualTo(
                "<p>Kiem tra bullet sau khi sua:</p>"
                        + "<ul><li>Gach dau dong mot</li><li>Gach dau dong hai</li></ul>"
                        + "<ol><li>Danh so mot</li><li>Danh so hai</li></ol>");
    }

    @Test
    void ordered_run_before_bullet_run_also_splits() {
        // The reverse order must split too — the run boundary is what matters,
        // not which kind happens to come first.
        String cleaned = HtmlSanitizer.sanitize(
                "<ol><li data-list=\"ordered\">One</li>"
                        + "<li data-list=\"bullet\">Dot</li></ol>");
        assertThat(cleaned).isEqualTo("<ol><li>One</li></ol><ul><li>Dot</li></ul>");
    }

    @Test
    void alternating_runs_each_get_their_own_list() {
        String cleaned = HtmlSanitizer.sanitize(
                "<ol><li data-list=\"bullet\">A</li>"
                        + "<li data-list=\"ordered\">B</li>"
                        + "<li data-list=\"bullet\">C</li></ol>");
        assertThat(cleaned).isEqualTo(
                "<ul><li>A</li></ul><ol><li>B</li></ol><ul><li>C</li></ul>");
    }

    @Test
    void indented_bullet_items_keep_bullet_kind() {
        // Quill encodes nesting as class="ql-indent-N" on flat siblings. The
        // class is stripped by the safelist so the list flattens, but the
        // items must still be bullets rather than silently becoming numbers.
        String cleaned = HtmlSanitizer.sanitize(
                "<ol><li data-list=\"bullet\">A</li>"
                        + "<li data-list=\"bullet\" class=\"ql-indent-1\">Nested B</li></ol>");
        assertThat(cleaned).isEqualTo("<ul><li>A</li><li>Nested B</li></ul>");
    }

    @Test
    void plain_unordered_list_is_untouched() {
        // Pre-existing lesson content authored as real <ul> must not regress.
        String cleaned = HtmlSanitizer.sanitize("<ul><li>A</li><li>B</li></ul>");
        assertThat(cleaned).isEqualTo("<ul><li>A</li><li>B</li></ul>");
    }

    @Test
    void list_normalisation_is_idempotent() {
        // Re-saving an announcement must not drift the stored markup.
        String input = "<ol><li data-list=\"bullet\">A</li></ol>";
        String once = HtmlSanitizer.sanitize(input);
        String twice = HtmlSanitizer.sanitize(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void sanitize_preserves_paragraph_structure_without_growth() {
        // Simulate three round-trips (lecturer saves the same lesson 3
        // times). Output length must stay constant; structure must not grow.
        String input = "<p>Đoạn 1</p><p>Đoạn 2</p><p>Đoạn 3</p>";
        String r1 = HtmlSanitizer.sanitize(input);
        String r2 = HtmlSanitizer.sanitize(r1);
        String r3 = HtmlSanitizer.sanitize(r2);
        assertThat(r2).isEqualTo(r1);
        assertThat(r3).isEqualTo(r1);
    }
}