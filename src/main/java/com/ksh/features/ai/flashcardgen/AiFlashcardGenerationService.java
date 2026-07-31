package com.ksh.features.ai.flashcardgen;

import com.ksh.features.ai.client.AiClient;
import com.ksh.features.ai.log.AiRequestLogger;
import com.ksh.features.ai.questiongen.DocumentTextExtractor;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.ksh.common.IConstant.MSG_AI_NO_MATERIAL;

@Service
public class AiFlashcardGenerationService {

    private final DeckAccessResolver accessResolver;
    private final DocumentTextExtractor textExtractor;
    private final AiFlashcardPromptBuilder promptBuilder;
    private final AiFlashcardResponseParser responseParser;
    private final AiClient aiClient;

    public AiFlashcardGenerationService(DeckAccessResolver accessResolver,
                                        DocumentTextExtractor textExtractor,
                                        AiFlashcardPromptBuilder promptBuilder,
                                        AiFlashcardResponseParser responseParser,
                                        AiClient aiClient) {
        this.accessResolver = accessResolver;
        this.textExtractor = textExtractor;
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
        String material = file != null && !file.isEmpty()
                ? textExtractor.extract(file)
                : textExtractor.normalizePastedText(text);
        String reply = aiClient.chatJsonObject(
                promptBuilder.systemPrompt(),
                promptBuilder.userMessage(request, material),
                AiFlashcardPromptBuilder.maxTokensFor(request.count()),
                userId,
                AiRequestLogger.SOURCE_FLASHCARD_GEN);
        List<AiFlashcardGenDtos.GeneratedCardRow> rows = responseParser.parse(reply);
        return new AiFlashcardGenDtos.GenerateResult(rows, rows.size());
    }
}
