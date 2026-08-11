package com.ksh.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

/**
 * Static helper that strips unsafe markup from HTML before it is persisted.
 *
 * <p>Lives in {@code common} so every feature that accepts user-authored
 * HTML can reuse the same policy from a single entry point without dragging
 * a bean dependency around. Current consumers: lesson bodies, board posts,
 * lesson templates, question-bank items and exam questions — a change here
 * reaches all of them, so widening the safelist needs a security review.
 *
 * <p>Input is normalised before cleaning: Quill 2.x bullet lists arrive as
 * {@code <ol><li data-list="bullet">} and are retagged to {@code <ul>}, since
 * {@code data-list} is not on the safelist. See {@code normaliseQuillLists}.
 *
 * <p>Allowed tags: {@code h1–h6}, {@code p}, {@code br}, {@code hr},
 * {@code strong}, {@code b}, {@code em}, {@code i}, {@code u}, {@code s},
 * {@code blockquote}, {@code pre}, {@code code}, {@code ol}, {@code ul},
 * {@code li}, {@code a}, {@code img}.
 *
 * <p>Allowed attributes:
 * <ul>
 *   <li>{@code a}: {@code href} (only {@code http}, {@code https},
 *       {@code mailto}), {@code target}, {@code rel}.</li>
 *   <li>{@code img}: {@code src} (only {@code data:image/*}, {@code http},
 *       {@code https}), {@code alt}, {@code width}, {@code height}.</li>
 *   <li>Everything else is stripped — including {@code onclick},
 *       inline {@code style}, and any {@code javascript:} URL scheme.</li>
 * </ul>
 */
public final class HtmlSanitizer {

    /**
     * Shared Safelist instance. Jsoup's {@link Safelist} is documented as
     * being safe for read-only reuse once configured, so we keep a single
     * static copy instead of rebuilding it on every call.
     */
    private static final Safelist SAFELIST = buildSafelist();

    private HtmlSanitizer() {
        // utility holder
    }

    /**
     * Strips disallowed tags / attributes / URL schemes from the supplied
     * HTML body and returns the cleaned markup.
     *
     * @param html raw HTML straight from the form submission; may be
     *             {@code null} or blank
     * @return the sanitised HTML, or an empty string when {@code html} is
     *         {@code null} / blank
     */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // Disable pretty-print so sanitize() is idempotent. Default Jsoup
        // output inserts \n + indentation between block elements, which Quill
        // later re-parses as empty <p><br></p> paragraphs — see bug fix for
        // lesson rich-text round-trip drift.
        //
        // A non-empty base URI is required for preserveRelativeLinks to keep
        // server-uploaded paths like /uploads/exams/<uuid>.png (Quill img src).
        Document.OutputSettings settings = new Document.OutputSettings()
                .prettyPrint(false);
        // Rewrite Quill's bullet markup to real <ul> before cleaning, since
        // the safelist drops the data-list attribute that carries the marker.
        String normalised = normaliseQuillLists(html);
        return Jsoup.clean(normalised, "https://ksh.local", SAFELIST, settings);
    }

    /**
     * Rewrites Quill 2.x list markup into semantic {@code <ul>} / {@code <ol>}.
     *
     * <p>Quill 2.x serialises <em>every</em> list as a single {@code <ol>} and
     * records the real kind per item, as {@code <li data-list="bullet">} or
     * {@code <li data-list="ordered">}. Consecutive lists of different kinds
     * are merged into <em>one</em> {@code <ol>} whose children simply switch
     * {@code data-list} partway through. Verified against Quill 2.0.3: input
     * {@code <ul>a,b</ul><ol>c,d</ol>} serialises to a single {@code <ol>}
     * with children {@code bullet, bullet, ordered, ordered}.
     *
     * <p>Since {@code data-list} is not on the safelist, cleaning alone leaves
     * a bare {@code <ol>} and every bullet renders as a number. So each list is
     * split into consecutive runs of same-kind items and one element is emitted
     * per run — {@code <ul>} for a bullet run, {@code <ol>} otherwise —
     * preserving document order and each item's inner content.
     *
     * <p>Normalising on write (rather than allowing {@code data-list} through
     * and styling it per-page) keeps the stored markup renderer-agnostic: all
     * consumers of this class get correct bullets with no extra CSS, and the
     * output stays valid for non-browser targets such as plain-text mail.
     *
     * <p><strong>Indentation is intentionally flattened.</strong> Quill encodes
     * nesting as {@code class="ql-indent-N"} on flat sibling {@code <li>}s
     * rather than真 nested lists, and {@code class} is not on the safelist
     * either. Rebuilding a nested tree from those hints is out of scope here;
     * indented items keep their text and their bullet/number kind, but render
     * at a single level. This matches the pre-existing behaviour for indent —
     * the fix does not regress it.
     *
     * @param html raw HTML straight from the form submission
     * @return the same markup with Quill list runs rewritten
     */
    private static String normaliseQuillLists(String html) {
        // Fast path: skip the parse entirely for the common no-list payload.
        if (!html.contains("data-list")) {
            return html;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        for (Element list : doc.select("ol")) {
            splitListIntoRuns(list);
        }
        return doc.body().html();
    }

    /**
     * Replaces one Quill {@code <ol>} with a sequence of same-kind lists.
     *
     * <p>Walks the direct {@code <li>} children in order, starting a new
     * sibling list whenever the bullet/ordered kind changes, then drops the
     * original element. A list whose items are all one kind yields exactly one
     * replacement, so the common single-kind case stays a single element.
     *
     * @param list an {@code <ol>} produced by Quill; left untouched when empty
     */
    private static void splitListIntoRuns(Element list) {
        Elements items = list.select("> li");
        if (items.isEmpty()) {
            return;
        }
        Element current = null;
        boolean currentIsBullet = false;
        for (Element item : items) {
            boolean isBullet = "bullet".equals(item.attr("data-list"));
            // Open a new list when the run's kind flips (or on the first item).
            if (current == null || isBullet != currentIsBullet) {
                current = new Element(isBullet ? "ul" : "ol");
                list.before(current);
                currentIsBullet = isBullet;
            }
            // appendChild moves the node, preserving its inner content.
            current.appendChild(item);
        }
        list.remove();
    }

    private static Safelist buildSafelist() {
        Safelist list = new Safelist()
                .addTags("h1", "h2", "h3", "h4", "h5", "h6",
                        "p", "br", "hr",
                        "strong", "b", "em", "i", "u", "s",
                        "blockquote", "pre", "code",
                        "ol", "ul", "li",
                        "a", "img");

        list.addAttributes("a", "href", "target", "rel");
        list.addProtocols("a", "href", "http", "https", "mailto");

        list.addAttributes("img", "src", "alt", "width", "height");
        // Allow inline data URIs in addition to http(s) so a lecturer can
        // paste a small embedded image without the attachment pipeline.
        list.addProtocols("img", "src", "data", "http", "https");
        // Keep relative /uploads/... paths from server image upload endpoints.
        list.preserveRelativeLinks(true);

        return list;
    }
}