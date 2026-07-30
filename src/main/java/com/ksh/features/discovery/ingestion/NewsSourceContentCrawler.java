package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.entity.NewsSourceLayout;
import com.ksh.features.discovery.entity.NewsSourceType;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NewsSourceContentCrawler {

    private static final Pattern STUDY_ATTACHMENT = Pattern.compile(
            "fn_egov_downFile\\(['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\)"
    );
    private static final Pattern SIZE_BYTES = Pattern.compile("\\[(\\d+)\\s*byte]", Pattern.CASE_INSENSITIVE);

    private final NewsHttpClient httpClient;
    private final NewsContentSanitizer sanitizer;
    private final NewsArticleRepository articleRepository;

    public NewsSourceContentCrawler(
            NewsHttpClient httpClient,
            NewsContentSanitizer sanitizer,
            NewsArticleRepository articleRepository
    ) {
        this.httpClient = httpClient;
        this.sanitizer = sanitizer;
        this.articleRepository = articleRepository;
    }

    public NewsCandidate enrichIfNeeded(NewsSource source, NewsCandidate candidate) {
        String canonicalUrl = NewsUrlSupport.canonicalize(candidate.canonicalUrl());
        String urlHash = NewsUrlSupport.sha256(canonicalUrl);
        Optional<NewsArticle> existing = articleRepository.findByCanonicalUrlHash(urlHash);

        boolean bodyMissing = existing
                .map(article -> article.getSourceBodyHtml() == null || article.getSourceBodyHtml().isBlank())
                .orElse(true);
        boolean imageMissing = existing
                .map(article -> article.getImageUrl() == null || article.getImageUrl().isBlank())
                .orElse(true);
        boolean attachmentsMissing = existing
                .map(article -> candidate.sourceContent() != null
                        && candidate.sourceContent().attachmentGroupId() != null
                        && (article.getSourceAttachments() == null
                        || article.getSourceAttachments().isEmpty()))
                .orElse(true);

        if (!bodyMissing && !imageMissing && !attachmentsMissing) {
            return candidate.withSourceContent(null, candidate.imageUrl());
        }

        return switch (source.getSourceType()) {
            case RSS -> enrichHtmlSource(
                    candidate,
                    canonicalUrl,
                    parseKbs(httpClient.get(canonicalUrl, MediaType.TEXT_HTML), canonicalUrl, sanitizer)
            );
            case KOREA_NET_HTML -> enrichHtmlSource(
                    candidate,
                    canonicalUrl,
                    parseKoreaNet(httpClient.get(canonicalUrl, MediaType.TEXT_HTML), canonicalUrl, sanitizer)
            );
            case STUDY_IN_KOREA_JSON -> enrichStudyInKorea(
                    candidate,
                    canonicalUrl,
                    attachmentsMissing
            );
        };
    }

    private NewsCandidate enrichStudyInKorea(
            NewsCandidate candidate,
            String canonicalUrl,
            boolean attachmentsMissing
    ) {
        NewsSourceContent raw = candidate.sourceContent();
        if (raw == null) {
            return candidate;
        }
        NewsContentSanitizer.SanitizedContent clean = sanitizer.sanitize(raw.html(), canonicalUrl);
        List<NewsAttachmentCandidate> attachments = raw.attachments();
        if (attachmentsMissing
                && raw.attachmentGroupId() != null
                && !raw.attachmentGroupId().isBlank()) {
            String detailHtml = httpClient.get(canonicalUrl, MediaType.TEXT_HTML);
            attachments = parseStudyAttachments(detailHtml);
        }
        NewsSourceContent content = new NewsSourceContent(
                clean.html(),
                clean.text(),
                NewsSourceLayout.STUDY_IN_KOREA,
                raw.author(),
                raw.viewCount(),
                raw.attachmentGroupId(),
                attachments
        );
        return candidate.withSourceContent(
                content,
                firstNonBlank(candidate.imageUrl(), clean.firstImageUrl())
        );
    }

    private static NewsCandidate enrichHtmlSource(
            NewsCandidate candidate,
            String canonicalUrl,
            ParsedSourceContent parsed
    ) {
        if (parsed.content() == null) {
            throw new IllegalStateException("Không tìm thấy ruột bài tại " + canonicalUrl);
        }
        return candidate.withSourceContent(
                parsed.content(),
                firstNonBlank(parsed.imageUrl(), candidate.imageUrl())
        );
    }

    static ParsedSourceContent parseKbs(
            String html,
            String baseUrl,
            NewsContentSanitizer sanitizer
    ) {
        Document document = Jsoup.parse(html, baseUrl);
        Element body = document.selectFirst(".body_txt");
        if (body == null) {
            return new ParsedSourceContent(null, ogImage(document));
        }
        body.select("script, style, .func_menu").remove();
        NewsContentSanitizer.SanitizedContent clean = sanitizer.sanitize(body.html(), baseUrl);
        NewsSourceContent content = new NewsSourceContent(
                clean.html(),
                clean.text(),
                NewsSourceLayout.KBS_WORLD,
                null,
                null,
                null,
                List.of()
        );
        return new ParsedSourceContent(content, firstNonBlank(ogImage(document), clean.firstImageUrl()));
    }

    static ParsedSourceContent parseKoreaNet(
            String html,
            String baseUrl,
            NewsContentSanitizer sanitizer
    ) {
        Document document = Jsoup.parse(html, baseUrl);
        Element body = document.selectFirst("#content_text_ALLBOX");
        if (body == null) {
            return new ParsedSourceContent(null, ogImage(document));
        }
        body.select("script, style").remove();
        NewsContentSanitizer.SanitizedContent clean = sanitizer.sanitize(body.html(), baseUrl);
        NewsSourceContent content = new NewsSourceContent(
                clean.html(),
                clean.text(),
                NewsSourceLayout.KOREA_NET,
                null,
                null,
                null,
                List.of()
        );
        return new ParsedSourceContent(content, firstNonBlank(ogImage(document), clean.firstImageUrl()));
    }

    static List<NewsAttachmentCandidate> parseStudyAttachments(String html) {
        Document document = Jsoup.parse(html, "https://www.studyinkorea.go.kr");
        List<NewsAttachmentCandidate> attachments = new ArrayList<>();
        int order = 0;
        for (Element link : document.select("a[href*=fn_egov_downFile]")) {
            Matcher call = STUDY_ATTACHMENT.matcher(link.attr("href"));
            if (!call.find()) {
                continue;
            }
            String label = link.text().trim();
            Matcher size = SIZE_BYTES.matcher(label);
            Long sizeBytes = size.find() ? Long.parseLong(size.group(1)) : null;
            String displayName = label.replaceFirst("\\s*\\[\\d+\\s*byte]\\s*$", "").trim();
            String url = UriComponentsBuilder
                    .fromUriString("https://www.studyinkorea.go.kr/cmm/fms/FileDown.do")
                    .queryParam("atchFileId", call.group(1))
                    .queryParam("fileSn", call.group(2))
                    .build()
                    .toUriString();
            attachments.add(new NewsAttachmentCandidate(
                    displayName,
                    url,
                    mediaType(displayName),
                    sizeBytes,
                    order++
            ));
        }
        return List.copyOf(attachments);
    }

    private static String ogImage(Document document) {
        Element image = document.selectFirst("meta[property=og:image][content]");
        return image == null ? null : image.attr("content").trim();
    }

    private static String mediaType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "application/octet-stream";
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    record ParsedSourceContent(
            NewsSourceContent content,
            String imageUrl
    ) {
    }
}
