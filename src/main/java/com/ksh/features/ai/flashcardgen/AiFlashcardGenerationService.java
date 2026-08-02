package com.ksh.features.ai.flashcardgen;

import com.ksh.features.ai.client.AiClient;
import com.ksh.features.ai.log.AiRequestLogger;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.ksh.common.IConstant.MSG_AI_NO_MATERIAL;

@Service
public class AiFlashcardGenerationService {

    private final DeckAccessResolver accessResolver;
    private final KoreanFlashcardMaterialSelector materialSelector;
    private final AiFlashcardPromptBuilder promptBuilder;
    private final AiFlashcardResponseParser responseParser;
    private final AiClient aiClient;

    public AiFlashcardGenerationService(DeckAccessResolver accessResolver,
                                        KoreanFlashcardMaterialSelector materialSelector,
                                        AiFlashcardPromptBuilder promptBuilder,
                                        AiFlashcardResponseParser responseParser,
                                        AiClient aiClient) {
        this.accessResolver = accessResolver;
        this.materialSelector = materialSelector;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.aiClient = aiClient;
    }

    public AiFlashcardGenDtos.GenerateResult generate(Long userId, Long deckId,
                                                      MultipartFile file, String text,
                                                      AiFlashcardGenDtos.GenerateRequest request) {
        accessResolver.requireOwner(deckId, userId);
        if ((file == null || file.isEmpty()) && (text == null || text.isBlank())) {
            throw new IllegalArgumentException(MSG_AI_NO_MATERIAL);
        }
        String material = materialSelector.select(file, text);
        String reply = aiClient.chatJsonObject(
                promptBuilder.systemPrompt(),
                promptBuilder.userMessage(request, material),
                AiFlashcardPromptBuilder.maxTokensFor(request.count()),
                userId,
                AiRequestLogger.SOURCE_FLASHCARD_GEN);
        List<AiFlashcardGenDtos.GeneratedCardRow> rows;
        try {
            rows = responseParser.parse(reply);
        } catch (IllegalArgumentException malformedReply) {
            // Some OpenAI-compatible free providers accept response_format but still
            // return prose, a top-level array, or invalid JSON. Retry once without the
            // provider-side JSON mode and with a shorter, stricter output contract.
            String retryReply = aiClient.chat(
                    promptBuilder.retrySystemPrompt(),
                    promptBuilder.retryUserMessage(request, material),
                    AiFlashcardPromptBuilder.maxTokensFor(request.count()),
                    userId,
                    AiRequestLogger.SOURCE_FLASHCARD_GEN);
            rows = responseParser.parse(retryReply);
        }
        int requestedCount = AiFlashcardPromptBuilder.clampCount(request.count());
        if (rows.size() > requestedCount) {
            rows = List.copyOf(rows.subList(0, requestedCount));
        }
        return new AiFlashcardGenDtos.GenerateResult(rows, rows.size());
    }
}
