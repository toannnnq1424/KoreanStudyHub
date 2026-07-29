package com.ksh.features.discovery.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.entity.NewsSourceLayout;
import com.ksh.features.discovery.entity.NewsSourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class StudyInKoreaNewsSourceAdapter implements NewsSourceAdapter {

    private static final int MAX_ITEMS = 30;
    private static final String DETAIL_URL =
            "https://www.studyinkorea.go.kr/eng/plan/gksNoticeRead.do";

    private final NewsHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int pagesPerRun;

    @Autowired
    public StudyInKoreaNewsSourceAdapter(
            NewsHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${app.news.ingestion.backfill-pages-per-run:4}") int pagesPerRun
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.pagesPerRun = Math.max(2, Math.min(pagesPerRun, 12));
    }

    StudyInKoreaNewsSourceAdapter(NewsHttpClient httpClient, ObjectMapper objectMapper) {
        this(httpClient, objectMapper, 4);
    }

    @Override
    public NewsSourceType supportedType() {
        return NewsSourceType.STUDY_IN_KOREA_JSON;
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

    List<NewsCandidate> parse(String json, NewsSource source) {
        try {
            JsonNode list = objectMapper.readTree(json).path("list");
            List<NewsCandidate> candidates = new ArrayList<>();
            if (!list.isArray()) {
                return candidates;
            }
            for (JsonNode item : list) {
                String id = text(item, "ntt_id");
                String boardId = text(item, "bbs_id");
                String title = text(item, "ntt_sj");
                if (id == null || title == null || title.isBlank()) {
                    continue;
                }
                String url = UriComponentsBuilder.fromUriString(DETAIL_URL)
                        .queryParam("bbsId", boardId)
                        .queryParam("nttId", id)
                        .build()
                        .toUriString();
                String rawBody = text(item, "ntt_cn");
                NewsSourceContent sourceContent = new NewsSourceContent(
                        rawBody,
                        null,
                        NewsSourceLayout.STUDY_IN_KOREA,
                        text(item, "frst_register_nm"),
                        longValue(item, "rdcnt"),
                        text(item, "atch_file_id"),
                        List.of()
                );
                candidates.add(new NewsCandidate(
                        id,
                        NewsTextSupport.plainText(title, 700),
                        NewsTextSupport.plainText(rawBody, 480),
                        url,
                        null,
                        source.getLanguageCode(),
                        source.getDefaultCategory(),
                        parseDate(text(item, "frst_regist_pnttm")),
                        parseDeadline(text(item, "ntce_endde")),
                        sourceContent
                ));
                if (candidates.size() >= MAX_ITEMS) {
                    break;
                }
            }
            return candidates;
        } catch (Exception exception) {
            throw new IllegalStateException("Không đọc được GKS JSON", exception);
        }
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void addPage(
            List<NewsCandidate> candidates,
            Set<String> seenIds,
            NewsSource source,
            int page
    ) {
        String url = UriComponentsBuilder.fromUriString(source.getFeedUrl())
                .replaceQueryParam("page", page)
                .build()
                .encode()
                .toUriString();
        String json = httpClient.get(url, MediaType.APPLICATION_JSON);
        for (NewsCandidate candidate : parse(json, source)) {
            String key = candidate.externalId() == null
                    ? candidate.canonicalUrl()
                    : candidate.externalId();
            if (seenIds.add(key)) {
                candidates.add(candidate);
            }
        }
    }

    private static Long longValue(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static LocalDateTime parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDate.parse(rawDate.trim()).atTime(LocalTime.NOON);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now();
        }
    }

    private static LocalDateTime parseDeadline(String rawDate) {
        if (rawDate == null || rawDate.isBlank() || "99991231".equals(rawDate.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(
                    rawDate.trim(),
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE
            ).atTime(23, 59, 59);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
