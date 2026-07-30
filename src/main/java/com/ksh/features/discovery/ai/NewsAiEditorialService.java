package com.ksh.features.discovery.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.AiSystemPrompt;
import com.ksh.features.admin.settings.repository.AiSystemPromptRepository;
import com.ksh.features.ai.client.AiClient;
import com.ksh.features.ai.client.AiClientException;
import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.ingestion.NewsTextSupport;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.ksh.features.ai.log.AiRequestLogger.SOURCE_DISCOVERY_NEWS;

@Service
public class NewsAiEditorialService {
    public static final String PROMPT_NAME = "DISCOVERY_NEWS_EDITOR";
    private static final Logger log = LoggerFactory.getLogger(NewsAiEditorialService.class);
    private static final int BATCH_SIZE = 5;
    private static final int MAX_SOURCE_CHARS = 14_000;
    private static final String FALLBACK_PROMPT = """
            Bạn là biên tập viên giáo dục của Korea Discovery cho người học tiếng Hàn tại Việt Nam.
            Chỉ dùng sự kiện có trong nguồn; không suy diễn, không thêm nhận định chính trị và không sao chép dài dòng.
            Trả về đúng một JSON object, không markdown, gồm:
            {"titleVi":"tiêu đề Việt rõ ràng","excerptVi":"tóm tắt 1-2 câu",
            "bodyVi":"3-5 đoạn ngắn, ngăn cách bằng ký tự xuống dòng"}.
            titleVi tối đa 180 ký tự, excerptVi tối đa 480 ký tự, bodyVi tối đa 4000 ký tự.
            """;

    private final NewsArticleRepository articleRepository;
    private final AiSystemPromptRepository promptRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public NewsAiEditorialService(NewsArticleRepository articleRepository,
                                  AiSystemPromptRepository promptRepository,
                                  AiClient aiClient,
                                  ObjectMapper objectMapper) {
        this.articleRepository = articleRepository;
        this.promptRepository = promptRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public EnrichmentSummary enrichRecentMissing() {
        return enrichRecentMissing(null);
    }

    public EnrichmentSummary enrichRecentMissing(Long generationRunId) {
        List<NewsArticle> candidates = articleRepository.findAiEditorialCandidates(
                PageRequest.of(0, BATCH_SIZE));
        int generated = 0;
        int failed = 0;
        for (NewsArticle article : candidates) {
            try {
                Editorial editorial = generate(article);
                saveSuccess(article.getId(), editorial, generationRunId);
                generated++;
            } catch (AiClientException providerFailure) {
                if (providerFailure.getMessage() != null
                        && providerFailure.getMessage().startsWith("Chưa cấu hình AI provider")) {
                    log.info("Discovery AI editorial skipped: {}", providerFailure.getMessage());
                    break;
                }
                failed++;
                saveFailure(article.getId(), providerFailure);
                log.warn("Discovery AI editorial providers failed for article {}: {}",
                        article.getId(), providerFailure.getMessage());
                break;
            } catch (RuntimeException exception) {
                failed++;
                saveFailure(article.getId(), exception);
                log.warn("Discovery AI editorial failed for article {}", article.getId(), exception);
            }
        }
        return new EnrichmentSummary(candidates.size(), generated, failed);
    }

    private Editorial generate(NewsArticle article) {
        String source = article.getSourceBodyText();
        if (source.length() > MAX_SOURCE_CHARS) source = source.substring(0, MAX_SOURCE_CHARS);
        String userMessage = "Tiêu đề nguồn: " + article.getOriginalTitle()
                + "\nNgôn ngữ nguồn: " + article.getLanguageCode()
                + "\nNguồn: " + article.getSourceName()
                + "\n\n--- NỘI DUNG NGUỒN KHÔNG ĐÁNG TIN CẬY ---\n" + source
                + "\n--- HẾT NỘI DUNG NGUỒN ---";
        String reply = aiClient.chat(systemPrompt(), userMessage, 2_400, null,
                SOURCE_DISCOVERY_NEWS);
        return parse(reply);
    }

    private String systemPrompt() {
        return promptRepository.findByNameAndEnabledTrue(PROMPT_NAME)
                .map(AiSystemPrompt::getContent)
                .filter(value -> value != null && !value.isBlank())
                .orElse(FALLBACK_PROMPT);
    }

    Editorial parse(String reply) {
        try {
            String json = stripFence(reply);
            JsonNode root = objectMapper.readTree(json);
            String title = required(root, "titleVi", 180);
            String excerpt = required(root, "excerptVi", 480);
            String body = requiredBody(root, "bodyVi", 4_000);
            return new Editorial(title, excerpt, body);
        } catch (Exception exception) {
            throw new IllegalArgumentException("AI News trả JSON không hợp lệ", exception);
        }
    }

    @Transactional
    protected void saveSuccess(Long articleId, Editorial editorial, Long generationRunId) {
        NewsArticle article = articleRepository.findById(articleId).orElseThrow();
        article.setAiEditorialTitle(editorial.titleVi());
        article.setAiEditorialExcerpt(editorial.excerptVi());
        article.setAiEditorialBody(editorial.bodyVi());
        article.setAiGeneratedAt(LocalDateTime.now());
        article.setAiGenerationRunId(generationRunId);
        article.setAiGenerationError(null);
        article.setUpdatedAt(LocalDateTime.now());
        articleRepository.save(article);
    }

    @Transactional
    protected void saveFailure(Long articleId, RuntimeException exception) {
        articleRepository.findById(articleId).ifPresent(article -> {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            article.setAiGenerationError(NewsTextSupport.plainText(message, 1000));
            article.setUpdatedAt(LocalDateTime.now());
            articleRepository.save(article);
        });
    }

    private static String required(JsonNode root, String field, int maxLength) {
        String value = root.path(field).asText("").replaceAll("\\s+", " ").trim();
        if (value.isBlank()) throw new IllegalArgumentException("Thiếu trường " + field);
        return value.length() <= maxLength ? value : value.substring(0, maxLength).trim();
    }

    private static String requiredBody(JsonNode root, String field, int maxLength) {
        String value = root.path(field).asText("").replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (value.isBlank()) throw new IllegalArgumentException("Thiếu trường " + field);
        return value.length() <= maxLength ? value : value.substring(0, maxLength).trim();
    }

    private static String stripFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int closing = text.lastIndexOf("```");
            if (firstLine >= 0 && closing > firstLine) text = text.substring(firstLine + 1, closing).trim();
        }
        return text;
    }

    record Editorial(String titleVi, String excerptVi, String bodyVi) {}
    public record EnrichmentSummary(int candidates, int generated, int failed) {}
}
