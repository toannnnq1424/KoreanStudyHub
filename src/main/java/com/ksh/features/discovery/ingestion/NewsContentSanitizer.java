package com.ksh.features.discovery.ingestion;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class NewsContentSanitizer {

    private static final int MAX_HTML_LENGTH = 400_000;
    private static final int MAX_TEXT_LENGTH = 200_000;
    private static final Safelist SOURCE_CONTENT = Safelist.relaxed()
            .addTags(
                    "div", "section", "article", "figure", "figcaption",
                    "h2", "h3", "h4", "table", "thead", "tbody", "tr", "th", "td"
            )
            .addAttributes(":all", "class")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("a", "href", "title")
            .addProtocols("img", "src", "https")
            .addProtocols("a", "href", "https", "mailto")
            .addEnforcedAttribute("a", "target", "_blank")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow");

    public SanitizedContent sanitize(String rawHtml, String baseUrl) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return new SanitizedContent(null, null, null);
        }
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String cleaned = Jsoup.clean(rawHtml, baseUrl, SOURCE_CONTENT, outputSettings);
        Document fragment = Jsoup.parseBodyFragment(cleaned, baseUrl);
        fragment.outputSettings(outputSettings);
        for (Element image : fragment.select("img[src]")) {
            String absolute = image.absUrl("src");
            if (!absolute.isBlank()) {
                image.attr("src", absolute);
            }
            image.attr("loading", "lazy");
            image.attr("decoding", "async");
            image.attr("referrerpolicy", "no-referrer");
        }

        String html = trimTo(fragment.body().html(), MAX_HTML_LENGTH);
        String text = trimTo(fragment.body().text().replaceAll("\\s+", " ").trim(), MAX_TEXT_LENGTH);
        Element firstImage = fragment.selectFirst("img[src]");
        String firstImageUrl = firstImage == null ? null : firstImage.attr("src");
        return new SanitizedContent(
                html == null || html.isBlank() ? null : html,
                text == null || text.isBlank() ? null : text,
                firstImageUrl == null || firstImageUrl.isBlank() ? null : firstImageUrl
        );
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record SanitizedContent(
            String html,
            String text,
            String firstImageUrl
    ) {
    }
}
