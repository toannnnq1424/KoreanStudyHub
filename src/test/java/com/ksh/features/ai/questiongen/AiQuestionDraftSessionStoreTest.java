package com.ksh.features.ai.questiongen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftOption;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftQuestion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiQuestionDraftSessionStoreTest {

    private final AiQuestionDraftSessionRepository repository =
            mock(AiQuestionDraftSessionRepository.class);
    private final AiQuestionDraftSessionStore store =
            new AiQuestionDraftSessionStore(repository, new ObjectMapper());

    @Test
    void save_persists_a_durable_preview_and_returns_the_same_questions() {
        List<DraftQuestion> questions = questions();

        var preview = store.save(7L, 11L, questions);

        assertThat(preview.sessionId()).isNotBlank();
        assertThat(preview.questions()).isEqualTo(questions);
        ArgumentCaptor<AiQuestionDraftSessionEntity> row =
                ArgumentCaptor.forClass(AiQuestionDraftSessionEntity.class);
        verify(repository).saveAndFlush(row.capture());
        assertThat(row.getValue().getId()).isEqualTo(preview.sessionId());
        assertThat(row.getValue().getQuestionsJson()).contains("Xin chào");
        verify(repository, never()).deleteExpiredBatch(any(), anyInt());
    }

    @Test
    void malformed_id_is_rejected_without_querying_the_database() {
        assertThatThrownBy(() -> store.requireForUpdate("not-a-uuid", 7L, 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hết hạn");
        verify(repository, never()).findOwnedForUpdate(any(), any(), any());
    }

    @Test
    void owned_pending_row_round_trips_and_becomes_single_use() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        AiQuestionDraftSessionEntity row = new AiQuestionDraftSessionEntity(
                "3bde5f97-6573-44d8-94c7-019128de5e0b",
                7L, 11L,
                """
                [{"type":"MCQ","content":"Xin chào","explanation":null,
                "options":[{"content":"A","correct":true},{"content":"B","correct":false}]}]
                """,
                now.minusMinutes(1), now.plusMinutes(5));
        when(repository.findOwnedForUpdate(row.getId(), 7L, 11L))
                .thenReturn(Optional.of(row));

        var loaded = store.requireForUpdate(row.getId(), 7L, 11L);
        store.consume(loaded);

        assertThat(loaded.questions()).singleElement()
                .extracting(DraftQuestion::content).isEqualTo("Xin chào");
        verify(repository).save(row);
        assertThatThrownBy(() -> store.requireForUpdate(row.getId(), 7L, 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hết hạn");
    }

    @Test
    void expired_or_foreign_rows_use_the_same_non_disclosing_error() {
        String id = "4e9972f7-3885-4eb4-bdc3-c4ef24cd3ba9";
        when(repository.findOwnedForUpdate(id, 7L, 11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.requireForUpdate(id, 7L, 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AiQuestionDraftSessionStore.MSG_SESSION_EXPIRED);
    }

    private static List<DraftQuestion> questions() {
        return List.of(new DraftQuestion("MCQ", "Xin chào", null, List.of(
                new DraftOption("A", true),
                new DraftOption("B", false))));
    }
}
