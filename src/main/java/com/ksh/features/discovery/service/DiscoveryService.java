package com.ksh.features.discovery.service;

import com.ksh.features.discovery.dto.DiscoveryDtos.ArticleDetail;
import com.ksh.features.discovery.dto.DiscoveryDtos.AttachmentCard;
import com.ksh.features.discovery.dto.DiscoveryDtos.CategoryOption;
import com.ksh.features.discovery.dto.DiscoveryDtos.DiscoveryPage;
import com.ksh.features.discovery.dto.DiscoveryDtos.StoryCard;
import com.ksh.features.discovery.dto.DiscoveryDtos.VocabularyCard;
import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsArticleAttachment;
import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.entity.NewsCategory;
import com.ksh.features.discovery.entity.NewsVocabulary;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.repository.NewsVocabularyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class DiscoveryService {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final int FEED_PAGE_SIZE = 18;
    private static final int FEED_QUERY_POOL_SIZE = 240;
    private static final Pattern HANGUL =
            Pattern.compile("[\\u1100-\\u11ff\\u3130-\\u318f\\uac00-\\ud7af]");

    private final NewsArticleRepository articleRepository;
    private final NewsVocabularyRepository vocabularyRepository;

    public DiscoveryService(
            NewsArticleRepository articleRepository,
            NewsVocabularyRepository vocabularyRepository
    ) {
        this.articleRepository = articleRepository;
        this.vocabularyRepository = vocabularyRepository;
    }

    public DiscoveryPage page(String categorySlug, String rawQuery) {
        return page(categorySlug, rawQuery, "vi", 1);
    }

    public DiscoveryPage page(String categorySlug, String rawQuery, String rawLanguage) {
        return page(categorySlug, rawQuery, rawLanguage, 1);
    }

    public DiscoveryPage page(
            String categorySlug,
            String rawQuery,
            String rawLanguage,
            int requestedPage
    ) {
        Optional<NewsCategory> selected = NewsCategory.fromSlug(categorySlug);
        String query = normalizeQuery(rawQuery);
        String selectedLanguage = normalizeLanguage(rawLanguage);
        boolean vietnamSelected = "vietnam".equalsIgnoreCase(categorySlug);
        int zeroBasedPage = Math.max(0, requestedPage - 1);
        Page<NewsArticle> articlePool = vietnamSelected
                ? articleRepository.findVietnamFeed(
                        query,
                        PageRequest.of(0, FEED_QUERY_POOL_SIZE)
                )
                : articleRepository.findFeed(
                        selected.orElse(null),
                        query,
                        PageRequest.of(0, FEED_QUERY_POOL_SIZE)
                );
        List<NewsArticle> orderedArticles = prioritizeLanguage(
                articlePool.getContent(),
                selectedLanguage
        );
        int totalPages = Math.max(
                1,
                (int) Math.ceil(articlePool.getTotalElements() / (double) FEED_PAGE_SIZE)
        );
        if (zeroBasedPage >= totalPages) {
            zeroBasedPage = totalPages - 1;
        }
        int from = zeroBasedPage * FEED_PAGE_SIZE;
        int to = Math.min(from + FEED_PAGE_SIZE, orderedArticles.size());
        List<NewsArticle> articles = from >= orderedArticles.size()
                ? List.of()
                : orderedArticles.subList(from, to);
        List<StoryCard> cards = articles.stream().map(this::toCard).toList();

        StoryCard hero = cards.isEmpty() ? null : cards.get(0);
        List<StoryCard> digest = slice(cards, 1, 5);
        List<StoryCard> featured = slice(cards, 5, 11);
        List<StoryCard> latest = slice(cards, 11, cards.size());
        if (latest.isEmpty() && cards.size() > 1) {
            latest = slice(cards, 1, cards.size());
        }

        StoryCard scholarship = topScholarship(cards);
        if (scholarship == null) {
            List<NewsArticle> opportunities = articleRepository.findFeed(
                    NewsCategory.SCHOLARSHIP,
                    null,
                    PageRequest.of(0, 1)
            ).getContent();
            scholarship = opportunities.isEmpty() ? null : toCard(opportunities.get(0));
        }

        List<VocabularyCard> quickVocabulary = hero == null
                ? List.of()
                : vocabularyRepository.findByArticleIdOrderByDisplayOrderAsc(hero.id())
                        .stream()
                        .limit(3)
                        .map(this::toVocabulary)
                        .toList();

        String selectedSlug = selected.map(NewsCategory::getSlug).orElse("all");
        List<CategoryOption> categories = new ArrayList<>();
        categories.add(new CategoryOption(
                "all",
                "Dành cho bạn",
                selected.isEmpty() && !vietnamSelected
        ));
        categories.add(new CategoryOption("vietnam", "Góc Việt–Hàn", vietnamSelected));
        Arrays.stream(NewsCategory.values())
                .map(category -> new CategoryOption(
                        category.getSlug(),
                        category.getLabel(),
                        selected.map(value -> value == category).orElse(false)
                ))
                .forEach(categories::add);

        if (vietnamSelected) {
            List<StoryCard> vietnamCards = cards;
            hero = vietnamCards.isEmpty() ? null : vietnamCards.get(0);
            digest = slice(vietnamCards, 1, 5);
            featured = slice(vietnamCards, 5, 11);
            latest = vietnamCards.size() > 11
                    ? slice(vietnamCards, 11, vietnamCards.size())
                    : slice(vietnamCards, 1, vietnamCards.size());
            selectedSlug = "vietnam";
            quickVocabulary = hero == null
                    ? List.of()
                    : vocabularyRepository.findByArticleIdOrderByDisplayOrderAsc(hero.id())
                            .stream()
                            .limit(3)
                            .map(this::toVocabulary)
                            .toList();
        }

        return new DiscoveryPage(
                selectedLanguage,
                selectedSlug,
                query == null ? "" : query,
                categories,
                hero,
                digest,
                featured,
                latest,
                scholarship,
                quickVocabulary,
                hero == null,
                zeroBasedPage + 1,
                totalPages,
                articlePool.getTotalElements(),
                zeroBasedPage > 0,
                zeroBasedPage + 1 < totalPages,
                pageNumbers(zeroBasedPage + 1, totalPages)
        );
    }

    public ArticleDetail detail(String slug) {
        NewsArticle article = articleRepository
                .findBySlugAndStatus(slug, NewsArticleStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        List<VocabularyCard> vocabulary = vocabularyRepository
                .findByArticleIdOrderByDisplayOrderAsc(article.getId())
                .stream()
                .map(this::toVocabulary)
                .toList();
        List<StoryCard> related = articleRepository
                .findByStatusAndCategoryAndIdNotOrderByRankScoreDescPublishedAtDesc(
                        NewsArticleStatus.PUBLISHED,
                        article.getCategory(),
                        article.getId(),
                        PageRequest.of(0, 3)
                )
                .stream()
                .map(this::toCard)
                .toList();
        StoryCard card = toCard(article);
        List<AttachmentCard> attachments = Optional
                .ofNullable(article.getSourceAttachments())
                .orElseGet(List::of)
                .stream()
                .sorted(Comparator.comparingInt(NewsArticleAttachment::displayOrder))
                .map(attachment -> new AttachmentCard(
                        attachment.displayName(),
                        attachment.sourceUrl(),
                        attachment.mediaType(),
                        formatBytes(attachment.sizeBytes())
                ))
                .toList();
        return new ArticleDetail(
                article.getId(),
                article.getSlug(),
                effectiveTitle(article),
                article.getOriginalTitle(),
                containsKorean(article.getOriginalTitle()),
                effectiveExcerpt(article),
                article.getCategory(),
                article.getCategory().getLabel(),
                article.getCategory().getSlug(),
                article.getCategory().getGlyph(),
                article.getSourceName(),
                article.getImageUrl(),
                article.getCanonicalUrl(),
                article.getSourceBodyHtml(),
                article.getAiEditorialBody(),
                article.getSourceLayout() == null
                        ? "curated"
                        : article.getSourceLayout().getCssClass(),
                article.getSourceAuthor(),
                article.getSourceViewCount(),
                languageLabel(article.getLanguageCode()),
                article.getPublishedAt(),
                card.displayDate(),
                article.getVietnamRelevance() > 0,
                containsKorean(article),
                readMinutes(article.getSourceBodyText()),
                attachments,
                vocabulary,
                related
        );
    }

    private StoryCard toCard(NewsArticle article) {
        return new StoryCard(
                article.getId(),
                article.getSlug(),
                effectiveTitle(article),
                article.getOriginalTitle(),
                containsKorean(article.getOriginalTitle()),
                effectiveExcerpt(article),
                article.getCategory(),
                article.getCategory().getLabel(),
                article.getCategory().getSlug(),
                article.getCategory().getGlyph(),
                article.getSourceName(),
                article.getImageUrl(),
                article.getPublishedAt(),
                article.getPublishedAt().format(DISPLAY_DATE),
                article.getVietnamRelevance() > 0,
                readMinutes(effectiveExcerpt(article))
        );
    }

    private VocabularyCard toVocabulary(NewsVocabulary vocabulary) {
        return new VocabularyCard(
                vocabulary.getKoreanWord(),
                vocabulary.getPronunciation(),
                vocabulary.getMeaningVi(),
                vocabulary.getPartOfSpeech(),
                vocabulary.getDictionaryUrl()
        );
    }

    private static String effectiveTitle(NewsArticle article) {
        return hasText(article.getAiEditorialTitle())
                ? article.getAiEditorialTitle() : article.getDisplayTitle();
    }

    private static String effectiveExcerpt(NewsArticle article) {
        return hasText(article.getAiEditorialExcerpt())
                ? article.getAiEditorialExcerpt() : article.getSourceExcerpt();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static List<StoryCard> slice(List<StoryCard> cards, int from, int to) {
        int safeFrom = Math.min(Math.max(0, from), cards.size());
        int safeTo = Math.min(Math.max(safeFrom, to), cards.size());
        return List.copyOf(cards.subList(safeFrom, safeTo));
    }

    private static List<Integer> pageNumbers(int currentPage, int totalPages) {
        if (totalPages <= 0) {
            return List.of();
        }
        int start = Math.max(1, currentPage - 2);
        int end = Math.min(totalPages, start + 4);
        start = Math.max(1, end - 4);
        List<Integer> pages = new ArrayList<>();
        for (int page = start; page <= end; page++) {
            pages.add(page);
        }
        return List.copyOf(pages);
    }

    private static StoryCard topScholarship(List<StoryCard> cards) {
        return cards.stream()
                .filter(card -> card.category() == NewsCategory.SCHOLARSHIP)
                .findFirst()
                .orElse(null);
    }

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        String value = rawQuery.trim().replaceAll("\\s+", " ");
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private static int readMinutes(String excerpt) {
        if (excerpt == null || excerpt.isBlank()) {
            return 2;
        }
        int words = excerpt.trim().split("\\s+").length;
        return Math.max(2, Math.min(5, (int) Math.ceil(words / 90.0)));
    }

    private static List<NewsArticle> prioritizeLanguage(
            List<NewsArticle> articles,
            String selectedLanguage
    ) {
        Comparator<NewsArticle> preference = Comparator
                .comparingInt((NewsArticle article) -> languagePriority(article, selectedLanguage))
                .thenComparing(NewsArticle::getRankScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(NewsArticle::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(NewsArticle::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        return articles.stream().sorted(preference).toList();
    }

    private static int languagePriority(NewsArticle article, String language) {
        if ("ko".equals(language)) {
            if (containsKorean(article)) {
                return 0;
            }
            if (!"vi".equalsIgnoreCase(article.getLanguageCode())) {
                return 1;
            }
            if (article.getVietnamRelevance() > 0) {
                return 2;
            }
            return 3;
        }
        if ("vi".equals(language)) {
            if ("vi".equalsIgnoreCase(article.getLanguageCode())) {
                return 0;
            }
            if (article.getVietnamRelevance() > 0) {
                return 1;
            }
            if (containsKorean(article)) {
                return 2;
            }
            return 3;
        }
        return 0;
    }

    private static boolean containsKorean(NewsArticle article) {
        return containsKorean(article.getDisplayTitle())
                || containsKorean(article.getSourceExcerpt())
                || containsKorean(article.getSourceBodyText());
    }

    private static boolean containsKorean(String value) {
        return value != null && HANGUL.matcher(value).find();
    }

    private static String normalizeLanguage(String rawLanguage) {
        return "ko".equalsIgnoreCase(rawLanguage) ? "ko" : "vi";
    }

    private static String languageLabel(String code) {
        if (code == null) {
            return "Không xác định";
        }
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "vi" -> "Tiếng Việt";
            case "ko" -> "Tiếng Hàn";
            case "en" -> "Tiếng Anh";
            default -> code.toUpperCase(Locale.ROOT);
        };
    }

    private static String formatBytes(Long bytes) {
        if (bytes == null || bytes < 0) {
            return null;
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
