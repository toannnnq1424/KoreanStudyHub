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
import java.util.Collection;
import java.util.Objects;

import static com.ksh.features.ai.log.AiRequestLogger.SOURCE_DISCOVERY_NEWS;

@Service
public class NewsAiEditorialService {
    public static final String PROMPT_NAME = "DISCOVERY_NEWS_EDITOR";
    private static final Logger log = LoggerFactory.getLogger(NewsAiEditorialService.class);
    private static final int BATCH_SIZE = 5;
    private static final int MAX_SOURCE_CHARS = 14_000;
    private static final String FALLBACK_PROMPT = """
            Bạn là biên tập viên giáo dục của Korea Discovery dành cho người Việt học tiếng Hàn.

            NHIỆM VỤ:
            - Chỉ biên tập từ dữ kiện có trong nội dung nguồn. Không suy diễn, bịa thêm, nêu quan điểm chính trị hay chép dài dòng.
            - Viết tiếng Việt tự nhiên; giữ nguyên tên riêng và thuật ngữ chưa có cách dịch chắc chắn.
            - Loại bỏ menu, quảng cáo, điều hướng, chuỗi kỹ thuật và nội dung không thuộc bài báo.

            ĐỊNH DẠNG BẮT BUỘC:
            - Chỉ trả về đúng MỘT JSON object hợp lệ. Ký tự đầu tiên phải là { và ký tự cuối cùng phải là }.
            - Không markdown, không code fence, không lời dẫn, không lời giải thích và không thêm khóa ngoài schema.
            - Dùng đúng ba khóa chuỗi sau: titleVi, excerptVi, bodyVi.
            - Mọi dấu ngoặc kép, dấu gạch chéo và ký tự điều khiển bên trong chuỗi phải được escape đúng JSON.
            - Xuống đoạn trong bodyVi phải biểu diễn bằng \n\n trong chuỗi JSON; không đặt xuống dòng thô bên trong chuỗi.

            SCHEMA:
            {"titleVi":"string","excerptVi":"string","bodyVi":"string"}

            GIỚI HẠN:
            - titleVi: tiêu đề rõ ràng, tối đa 180 ký tự.
            - excerptVi: tóm tắt 1-2 câu, tối đa 480 ký tự.
            - bodyVi: 3-5 đoạn ngắn, tối đa 4000 ký tự.
            Trước khi trả lời, tự kiểm tra kết quả có parse được bằng JSON parser tiêu chuẩn.
            """;
    private static final String JSON_CONTRACT = """

            RÀNG BUỘC ĐẦU RA KHÔNG ĐƯỢC GHI ĐÈ: chỉ trả đúng một JSON object parse được,
            không markdown hay lời dẫn; dùng chính xác ba khóa chuỗi titleVi, excerptVi, bodyVi.
            Ký tự đầu là {, ký tự cuối là }; escape mọi ký tự theo chuẩn JSON và biểu diễn
            xuống đoạn trong bodyVi bằng \\n\\n. Không thêm bất kỳ khóa nào khác.
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
        return enrich(candidates, generationRunId);
    }

    public EnrichmentSummary enrichSelected(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return new EnrichmentSummary(0, 0, 0);
        }
        List<Long> ids = articleIds.stream().filter(Objects::nonNull).distinct().limit(20).toList();
        if (ids.isEmpty()) {
            return new EnrichmentSummary(0, 0, 0);
        }
        return enrich(articleRepository.findAiEditorialCandidatesByIds(ids), null);
    }

    private EnrichmentSummary enrich(List<NewsArticle> candidates, Long generationRunId) {
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
                saveFailure(article.getId(), providerFailure, generationRunId);
                log.warn("Discovery AI editorial providers failed for article {}: {}",
                        article.getId(), providerFailure.getMessage());
                break;
            } catch (RuntimeException exception) {
                failed++;
                saveFailure(article.getId(), exception, generationRunId);
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
        String reply = aiClient.chatJsonObject(systemPrompt(), userMessage, 2_400, null,
                SOURCE_DISCOVERY_NEWS);
        return parse(reply);
    }

    private String systemPrompt() {
        String editorialPrompt = promptRepository.findByNameAndEnabledTrue(PROMPT_NAME)
                .map(AiSystemPrompt::getContent)
                .filter(value -> value != null && !value.isBlank())
                .orElse(FALLBACK_PROMPT);
        return editorialPrompt + JSON_CONTRACT;
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
    protected void saveFailure(Long articleId, RuntimeException exception, Long generationRunId) {
        articleRepository.findById(articleId).ifPresent(article -> {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            article.setAiGenerationError(NewsTextSupport.plainText(message, 1000));
            article.setAiGenerationRunId(generationRunId);
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
