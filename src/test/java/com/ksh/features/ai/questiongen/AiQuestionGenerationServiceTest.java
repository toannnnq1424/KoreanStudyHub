package com.ksh.features.ai.questiongen;

import com.ksh.features.ai.client.AiClient;
import com.ksh.features.ai.log.AiRequestLogger;
import com.ksh.features.ai.questiongen.AiQuestionDraftSessionStore.LoadedSession;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.ConfirmRequest;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftOption;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftQuestion;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.GenerateRequest;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.Preview;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.features.tests.service.ExamQuestionBankWriter;
import com.ksh.features.tests.service.TestActivityWriter;
import com.ksh.features.tests.support.TestAccessResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiQuestionGenerationServiceTest {

    private final TestAccessResolver accessResolver = mock(TestAccessResolver.class);
    private final ExamQuestionBankWriter writer = mock(ExamQuestionBankWriter.class);
    private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    private final AiQuestionPromptBuilder promptBuilder = mock(AiQuestionPromptBuilder.class);
    private final AiQuestionResponseParser parser = mock(AiQuestionResponseParser.class);
    private final AiQuestionDraftSessionStore sessionStore =
            mock(AiQuestionDraftSessionStore.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final TestRepository testRepository = mock(TestRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final TestActivityWriter activityWriter = mock(TestActivityWriter.class);
    private final com.ksh.features.tests.entity.Test exam =
            mock(com.ksh.features.tests.entity.Test.class);
    private AiQuestionGenerationService service;

    @BeforeEach
    void setUp() {
        service = new AiQuestionGenerationService(
                accessResolver, writer, extractor, promptBuilder, parser,
                sessionStore, aiClient, testRepository, questionRepository, activityWriter);
        when(exam.getId()).thenReturn(9L);
        when(exam.getTitle()).thenReturn("TOPIK I");
    }

    @Test
    void generate_uses_general_ksh_ai_transport_and_persists_only_a_preview() {
        GenerateRequest request = new GenerateRequest(3, Question.TYPE_MCQ, "medium");
        List<DraftQuestion> drafts = drafts();
        Preview preview = new Preview(
                "3bde5f97-6573-44d8-94c7-019128de5e0b", drafts);
        when(accessResolver.requireManageable(9L, 42L)).thenReturn(exam);
        when(writer.hasStudentActivity(9L)).thenReturn(false);
        when(extractor.normalizePastedText("material")).thenReturn("material");
        when(promptBuilder.systemPrompt()).thenReturn("system");
        when(promptBuilder.userMessage(request, "material")).thenReturn("user");
        when(aiClient.chat("system", "user", 1_200, 42L,
                AiRequestLogger.SOURCE_QUESTION_GEN)).thenReturn("json");
        when(parser.parse("json", 3, Question.TYPE_MCQ)).thenReturn(drafts);
        when(sessionStore.save(42L, 9L, drafts)).thenReturn(preview);

        assertThat(service.generate(42L, 9L, null, "material", request))
                .isEqualTo(preview);

        verify(questionRepository, never()).save(any());
        verify(testRepository, never()).save(any());
    }

    @Test
    void generate_rejects_invalid_bounds_before_contacting_ai() {
        when(accessResolver.requireManageable(9L, 42L)).thenReturn(exam);
        when(writer.hasStudentActivity(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.generate(
                42L, 9L, null, "material",
                new GenerateRequest(21, Question.TYPE_MCQ, "medium")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 đến 20");

        verifyNoInteractions(aiClient);
    }

    @Test
    void generate_retries_once_when_provider_reply_breaks_the_question_schema() {
        GenerateRequest request = new GenerateRequest(3, Question.TYPE_MCQ, "medium");
        List<DraftQuestion> drafts = drafts();
        Preview preview = new Preview(
                "3bde5f97-6573-44d8-94c7-019128de5e0b", drafts);
        when(accessResolver.requireManageable(9L, 42L)).thenReturn(exam);
        when(writer.hasStudentActivity(9L)).thenReturn(false);
        when(extractor.normalizePastedText("material")).thenReturn("material");
        when(promptBuilder.systemPrompt()).thenReturn("system");
        when(promptBuilder.userMessage(request, "material")).thenReturn("user");
        when(aiClient.chat("system", "user", 1_200, 42L,
                AiRequestLogger.SOURCE_QUESTION_GEN)).thenReturn("bad");
        when(parser.parse("bad", 3, Question.TYPE_MCQ))
                .thenThrow(new IllegalArgumentException("invalid"));
        when(promptBuilder.retrySystemPrompt()).thenReturn("retry-system");
        when(promptBuilder.retryUserMessage(request, "material")).thenReturn("retry-user");
        when(aiClient.chat("retry-system", "retry-user", 1_200, 42L,
                AiRequestLogger.SOURCE_QUESTION_GEN)).thenReturn("fixed");
        when(parser.parse("fixed", 3, Question.TYPE_MCQ)).thenReturn(drafts);
        when(sessionStore.save(42L, 9L, drafts)).thenReturn(preview);

        assertThat(service.generate(42L, 9L, null, "material", request))
                .isEqualTo(preview);

        verify(aiClient).chat("retry-system", "retry-user", 1_200, 42L,
                AiRequestLogger.SOURCE_QUESTION_GEN);
    }

    @Test
    void confirm_locks_exam_then_session_and_consumes_after_append() {
        List<DraftQuestion> drafts = drafts();
        AiQuestionDraftSessionEntity entity = mock(AiQuestionDraftSessionEntity.class);
        LoadedSession loaded = new LoadedSession(entity, drafts);
        ConfirmRequest request = new ConfirmRequest(
                "3bde5f97-6573-44d8-94c7-019128de5e0b", List.of(0));
        when(accessResolver.requireManageableForUpdate(9L, 42L)).thenReturn(exam);
        when(sessionStore.requireForUpdate(request.sessionId(), 42L, 9L))
                .thenReturn(loaded);
        when(writer.hasStudentActivity(9L)).thenReturn(false);
        when(writer.appendQuestions(eq(9L), any())).thenReturn(1);
        when(questionRepository.findByTestIdOrderBySortOrderAscIdAsc(9L))
                .thenReturn(List.of(mock(Question.class)));

        var result = service.confirm(42L, 9L, request);

        assertThat(result.insertedCount()).isEqualTo(1);
        var order = inOrder(accessResolver, sessionStore, writer, testRepository);
        order.verify(accessResolver).requireManageableForUpdate(9L, 42L);
        order.verify(sessionStore).requireForUpdate(request.sessionId(), 42L, 9L);
        order.verify(writer).hasStudentActivity(9L);
        order.verify(writer).appendQuestions(eq(9L), any());
        order.verify(testRepository).save(exam);
        order.verify(sessionStore).consume(loaded);
    }

    @Test
    void confirm_does_not_consume_when_selection_is_empty() {
        LoadedSession loaded = new LoadedSession(
                mock(AiQuestionDraftSessionEntity.class), drafts());
        ConfirmRequest request = new ConfirmRequest(
                "3bde5f97-6573-44d8-94c7-019128de5e0b", List.of(99));
        when(accessResolver.requireManageableForUpdate(9L, 42L)).thenReturn(exam);
        when(sessionStore.requireForUpdate(request.sessionId(), 42L, 9L))
                .thenReturn(loaded);

        assertThatThrownBy(() -> service.confirm(42L, 9L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ");

        verify(writer, never()).appendQuestions(any(), any());
        verify(sessionStore, never()).consume(any());
    }

    @Test
    void confirm_rejects_started_exam_before_any_shape_change() {
        LoadedSession loaded = new LoadedSession(
                mock(AiQuestionDraftSessionEntity.class), drafts());
        ConfirmRequest request = new ConfirmRequest(
                "3bde5f97-6573-44d8-94c7-019128de5e0b", List.of(0));
        when(accessResolver.requireManageableForUpdate(9L, 42L)).thenReturn(exam);
        when(sessionStore.requireForUpdate(request.sessionId(), 42L, 9L))
                .thenReturn(loaded);
        when(writer.hasStudentActivity(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.confirm(42L, 9L, request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(writer, never()).appendQuestions(any(), any());
        verify(sessionStore, never()).consume(any());
    }

    private static List<DraftQuestion> drafts() {
        return List.of(new DraftQuestion(Question.TYPE_MCQ, "Xin chào", null, List.of(
                new DraftOption("A", true),
                new DraftOption("B", false))));
    }
}
