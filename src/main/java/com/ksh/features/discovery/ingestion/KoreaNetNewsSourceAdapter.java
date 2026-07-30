package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.entity.NewsSourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KoreaNetNewsSourceAdapter implements NewsSourceAdapter {

    private static final Pattern CONTENT_VIEW = Pattern.compile(
            "contentView\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final int MAX_ITEMS_PER_PAGE = 30;

    private final NewsHttpClient httpClient;
    private final int pagesPerRun;

    @Autowired
    public KoreaNetNewsSourceAdapter(
            NewsHttpClient httpClient,
            @Value("${app.news.ingestion.backfill-pages-per-run:4}") int pagesPerRun
    ) {
        this.httpClient = httpClient;
        this.pagesPerRun = Math.max(2, Math.min(pagesPerRun, 12));
    }

    KoreaNetNewsSourceAdapter(NewsHttpClient httpClient) {
        this(httpClient, 4);
    }

    @Override
    public NewsSourceType supportedType() {
        return NewsSourceType.KOREA_NET_HTML;
    }

    @Override
    public List<NewsCandidate> fetch(NewsSource source) {
        List<NewsCandidate> candidates = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        addPage(candidates, seenIds, source, 1);

        int cursor = Math.max(2, source.getCrawlCursor());
        for (int offset = 0; offset < pagesPerRun - 1; offset++) {
            int page = cursor + offset;
            int before = candidates.size();
            addPage(candidates, seenIds, source, page);
            if (candidates.size() == before) {
                source.setCrawlCursor(2);
                return candidates;
            }
            source.setCrawlCursor(page + 1);
        }
        return candidates;
    }

    List<NewsCandidate> parse(String html, NewsSource source) {
        Document document = Jsoup.parse(html, source.getFeedUrl());
        List<NewsCandidate> candidates = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (Element card : document.select(".list-box")) {
            Element link = card.selectFirst("a[href*=contentView]");
            if (link == null) {
                continue;
            }
            Matcher matcher = CONTENT_VIEW.matcher(link.attr("href"));
            if (!matcher.find()) {
                continue;
            }
            String path = matcher.group(1);
            String articleId = matcher.group(2);
            if (!seenIds.add(articleId)) {
                continue;
            }

            Element titleNode = card.selectFirst(".tit");
            String title = titleNode == null ? link.text() : titleNode.text();
            if (title.isBlank()) {
                continue;
            }
            Element image = card.selectFirst("img[src]");
            String imageUrl = image == null ? null : absoluteUrl(source.getFeedUrl(), image.attr("src"));
            Element dateNode = card.selectFirst(".date");

            candidates.add(new NewsCandidate(
                    articleId,
                    NewsTextSupport.plainText(title, 700),
                    null,
                    absoluteUrl(source.getFeedUrl(), path + "/view?articleId=" + articleId),
                    imageUrl,
                    source.getLanguageCode(),
                    source.getDefaultCategory(),
                    parseDate(dateNode == null ? null : dateNode.text()),
                    null
            ));
            if (candidates.size() >= MAX_ITEMS_PER_PAGE) {
                break;
            }
        }
        return candidates;
    }

    private void addPage(
            List<NewsCandidate> candidates,
            Set<String> seenIds,
            NewsSource source,
            int page
    ) {
        String url = UriComponentsBuilder.fromUriString(source.getFeedUrl())
                .replaceQueryParam("pageIndex", page)
                .build()
                .encode()
                .toUriString();
        String html = httpClient.get(url, MediaType.TEXT_HTML);
        for (NewsCandidate candidate : parse(html, source)) {
            String key = candidate.externalId() == null
                    ? candidate.canonicalUrl()
                    : candidate.externalId();
            if (seenIds.add(key)) {
                candidates.add(candidate);
            }
        }
    }

    private static LocalDateTime parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDate.parse(rawDate.trim(), DATE).atTime(LocalTime.NOON);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now();
        }
    }

    private static String absoluteUrl(String baseUrl, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.startsWith("//") ? "https:" + value : value;
        return URI.create(baseUrl).resolve(normalized).toString();
    }
}
