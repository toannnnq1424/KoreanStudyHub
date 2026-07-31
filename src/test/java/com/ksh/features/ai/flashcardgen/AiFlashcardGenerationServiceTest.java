package com.ksh.features.ai.flashcardgen;

import com.ksh.features.ai.client.AiClient;
import com.ksh.features.ai.log.AiRequestLogger;
import com.ksh.features.ai.questiongen.DocumentTextExtractor;
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
    @Mock DocumentTextExtractor textExtractor;
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

        verify(textExtractor, never()).normalizePastedText(any());
        verify(aiClient, never()).chatJsonObject(any(), any(), anyInt(),
                any(), any());
    }

    @Test
    void returnsParsedRowsWithoutPersistingThem() {
        AiFlashcardGenerationService service = service();
        var request = new AiFlashcardGenDtos.GenerateRequest(10, "tiếng việt");
        var rows = List.of(new AiFlashcardGenDtos.GeneratedCardRow("A", "B"));

        when(textExtractor.normalizePastedText("material")).thenReturn("material");
        when(promptBuilder.systemPrompt()).thenReturn("system");
        when(promptBuilder.userMessage(request, "material")).thenReturn("user");
        when(aiClient.chatJsonObject("system", "user", 1200, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN)).thenReturn("{json}");
        when(responseParser.parse("{json}")).thenReturn(rows);

        var result = service.generate(3L, 7L, null, "material", request);

        assertEquals(1, result.count());
        assertEquals(rows, result.cards());
        InOrder order = inOrder(accessResolver, textExtractor, aiClient, responseParser);
        order.verify(accessResolver).requireOwner(7L, 3L);
        order.verify(textExtractor).normalizePastedText("material");
        order.verify(aiClient).chatJsonObject("system", "user", 1200, 3L,
                AiRequestLogger.SOURCE_FLASHCARD_GEN);
        order.verify(responseParser).parse("{json}");
    }

    private AiFlashcardGenerationService service() {
        return new AiFlashcardGenerationService(
                accessResolver, textExtractor, promptBuilder, responseParser, aiClient);
    }
}
