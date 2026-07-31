package com.ksh.features.ai.flashcardgen;

import com.ksh.entities.AiSystemPrompt;
import com.ksh.features.admin.settings.repository.AiSystemPromptRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AiFlashcardPromptBuilder {

    public static final String PROMPT_NAME = "AI_FLASHCARD_GENERATOR";
    static final int MIN_COUNT = 1;
    static final int MAX_COUNT = 50;
    static final String LANGUAGE_AUTO = "theo ngôn ngữ của tài liệu";
    private static final int MAX_LANGUAGE_CHARS = 40;

    private static final String FALLBACK_SYSTEM_PROMPT = """
            Bạn là trợ lý soạn thẻ ghi nhớ cho người học.
            Dựa duy nhất trên tài liệu được cung cấp, hãy sinh các thẻ hai mặt.

            QUY TẮC BẮT BUỘC:
            1. Chỉ trả về đúng một JSON object, không markdown và không thêm lời dẫn.
            2. Schema chính xác:
            {"cards":[{"front":"thuật ngữ hoặc câu hỏi ngắn","back":"định nghĩa hoặc câu trả lời súc tích"}]}
            3. Mặt trước ngắn gọn; mặt sau đủ ý nhưng không lan man.
            4. Hai mặt đều là chuỗi văn bản thuần, không rỗng, không HTML.
            5. Không lặp mặt trước.
            6. Không bịa kiến thức ngoài tài liệu.
            7. Sinh đúng số lượng và ngôn ngữ người dùng yêu cầu.""";

    private final AiSystemPromptRepository promptRepository;

    public AiFlashcardPromptBuilder(AiSystemPromptRepository promptRepository) {
        this.promptRepository = promptRepository;
    }

    public String systemPrompt() {
        return promptRepository.findByNameAndEnabledTrue(PROMPT_NAME)
                .map(AiSystemPrompt::getContent)
                .filter(content -> content != null && !content.isBlank())
                .orElse(FALLBACK_SYSTEM_PROMPT);
    }

    public String userMessage(AiFlashcardGenDtos.GenerateRequest request, String material) {
        return "Yêu cầu:\n"
                + "- Số thẻ: " + clampCount(request.count()) + "\n"
                + "- Ngôn ngữ: " + normalizeLanguage(request.language()) + "\n\n"
                + "Tài liệu:\n" + material;
    }

    public static int clampCount(int count) {
        return Math.max(MIN_COUNT, Math.min(MAX_COUNT, count));
    }

    public static String normalizeLanguage(String language) {
        String value = language == null ? "" : language.trim();
        if (value.isEmpty() || value.length() > MAX_LANGUAGE_CHARS) {
            return LANGUAGE_AUTO;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static int maxTokensFor(int count) {
        return 120 * clampCount(count);
    }
}
