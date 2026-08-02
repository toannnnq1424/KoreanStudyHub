package com.ksh.features.ai.flashcardgen;

import com.ksh.features.ai.client.AiClient;
import com.ksh.features.ai.log.AiRequestLogger;
import com.ksh.features.flashcards.support.DeckAccessResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFlashcardGenerationServiceTest {

    @Mock DeckAccessResolver accessResolver;
    @Mock KoreanFlashcardMaterialSelector materialSelector;
    @Mock AiFlashcardPromptBuilder promptBuilder;
    @Mock AiFlashcardResponseParser responseParser;
    @Mock AiClient aiClient;

    @Test
    void checksOwnershipBeforeReadingMaterialOrSpendingTokens() {
        AiFlashcardGenerationService service = service();
        org.mockito.Mockito.doThrow(new AccessDeniedException("forbidden"))
                .when(accessResolver).requireOwner(7L, 3L);

        assertThrows(AccessDeniedException.class, () -> service.generate(
                3L, 7L, null, "private material",
                new AiFlashcardGenDtos.GenerateRequest(10, "tiếng việt")));

        verify(materialSelector, never()).select(any(), any());
        verify(aiClient, never()).chatJsonObject(any(), any(), anyInt(),
                any(), any());
    }

    @Test
    void returnsParsedRowsWithoutPersistingThem() {
        AiFlashcardGenerationService service = service();
        var request = new AiFlashcardGenDtos.GenerateRequest(10, "tiếng việt");
        var rows = List.of(new AiFlashcardGenDtos.GeneratedCardRow("A", "B"));

        when(materialSelector.select(null, "material")).thenReturn("material");
        when(promptBuilder.systemPrompt()).thenReturn("system");
        when(promptBuilder.userMessage(request, "material")).thenReturn("user");
        when(aiClient.chatJsonObject("system", "user", 1200, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN)).thenReturn("{json}");
        when(responseParser.parse("{json}")).thenReturn(rows);

        var result = service.generate(3L, 7L, null, "material", request);

        assertEquals(1, result.count());
        assertEquals(rows, result.cards());
        InOrder order = inOrder(accessResolver, materialSelector, aiClient, responseParser);
        order.verify(accessResolver).requireOwner(7L, 3L);
        order.verify(materialSelector).select(null, "material");
        order.verify(aiClient).chatJsonObject("system", "user", 1200, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN);
        order.verify(responseParser).parse("{json}");
    }

    @Test
    void retriesOnce_without_provider_json_mode_when_first_reply_is_malformed() {
        AiFlashcardGenerationService service = service();
        var request = new AiFlashcardGenDtos.GenerateRequest(5, "tiếng việt");
        var rows = List.of(new AiFlashcardGenDtos.GeneratedCardRow("문화", "văn hóa"));

        when(materialSelector.select(null, "material")).thenReturn("material");
        when(promptBuilder.systemPrompt()).thenReturn("system");
        when(promptBuilder.userMessage(request, "material")).thenReturn("user");
        when(aiClient.chatJsonObject("system", "user", 600, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN)).thenReturn("bad reply");
        when(responseParser.parse("bad reply"))
                .thenThrow(new IllegalArgumentException("invalid"));
        when(promptBuilder.retrySystemPrompt()).thenReturn("retry system");
        when(promptBuilder.retryUserMessage(request, "material")).thenReturn("retry user");
        when(aiClient.chat("retry system", "retry user", 600, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN)).thenReturn("{fixed}");
        when(responseParser.parse("{fixed}")).thenReturn(rows);

        var result = service.generate(3L, 7L, null, "material", request);

        assertEquals(rows, result.cards());
        verify(aiClient).chatJsonObject("system", "user", 600, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN);
        verify(aiClient).chat("retry system", "retry user", 600, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN);
    }

    @Test
    void caps_provider_output_to_the_requested_number_of_cards() {
        AiFlashcardGenerationService service = service();
        var request = new AiFlashcardGenDtos.GenerateRequest(2, "tiếng việt");
        var rows = List.of(
                new AiFlashcardGenDtos.GeneratedCardRow("문화센터", "trung tâm văn hóa"),
                new AiFlashcardGenDtos.GeneratedCardRow("교류 행사", "sự kiện giao lưu"),
                new AiFlashcardGenDtos.GeneratedCardRow("참가 신청", "đăng ký tham gia"));

        when(materialSelector.select(null, "material")).thenReturn("material");
        when(promptBuilder.systemPrompt()).thenReturn("system");
        when(promptBuilder.userMessage(request, "material")).thenReturn("user");
        when(aiClient.chatJsonObject("system", "user", 240, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN)).thenReturn("{json}");
        when(responseParser.parse("{json}")).thenReturn(rows);

        var result = service.generate(3L, 7L, null, "material", request);

        assertEquals(2, result.count());
        assertEquals(rows.subList(0, 2), result.cards());
    }

    private AiFlashcardGenerationService service() {
        return new AiFlashcardGenerationService(
                accessResolver, materialSelector, promptBuilder, responseParser, aiClient);
    }
}
