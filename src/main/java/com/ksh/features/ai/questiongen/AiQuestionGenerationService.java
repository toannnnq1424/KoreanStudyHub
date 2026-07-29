package com.ksh.features.ai.questiongen;

import com.ksh.entities.TestActivity;
import com.ksh.features.ai.client.AiClient;
import com.ksh.features.ai.log.AiRequestLogger;
import com.ksh.features.ai.questiongen.AiQuestionDraftSessionStore.LoadedSession;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.ConfirmRequest;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.ConfirmResult;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.DraftQuestion;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.GenerateRequest;
import com.ksh.features.ai.questiongen.AiQuestionGenDtos.Preview;
import com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.features.tests.service.ExamQuestionBankWriter;
import com.ksh.features.tests.service.TestActivityWriter;
import com.ksh.features.tests.support.TestAccessResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.ksh.common.IConstant.MSG_QB_INSERT_LOCKED;

/** Two-step AI generation: preview first, append selected rows only after confirmation. */
@Service
public class AiQuestionGenerationService {

    private static final String MSG_NO_SELECTION =
            "Vui lòng chọn ít nhất một câu hỏi để chèn vào bài test";
    private static final String MSG_COUNT =
            "Số câu hỏi phải từ 1 đến 20";
    private static final String MSG_TYPE =
            "Loại câu hỏi phải là MCQ hoặc MR";
    private static final String MSG_GENERATION_BUSY =
            "Một yêu cầu sinh câu hỏi khác đang được xử lý, vui lòng chờ hoàn tất";

    private final TestAccessResolver accessResolver;
    private final ExamQuestionBankWriter questionBankWriter;
    private final DocumentTextExtractor textExtractor;
    private final AiQuestionPromptBuilder promptBuilder;
    private final AiQuestionResponseParser responseParser;
    private final AiQuestionDraftSessionStore sessionStore;
    private final AiClient aiClient;
    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final TestActivityWriter activityWriter;
    private final ConcurrentHashMap<Long, Boolean> activeGenerations = new ConcurrentHashMap<>();

    public AiQuestionGenerationService(TestAccessResolver accessResolver,
                                       ExamQuestionBankWriter questionBankWriter,
                                       DocumentTextExtractor textExtractor,
                                       AiQuestionPromptBuilder promptBuilder,
                                       AiQuestionResponseParser responseParser,
                                       AiQuestionDraftSessionStore sessionStore,
                                       AiClient aiClient,
                                       TestRepository testRepository,
                                       QuestionRepository questionRepository,
                                       TestActivityWriter activityWriter) {
        this.accessResolver = accessResolver;
        this.questionBankWriter = questionBankWriter;
        this.textExtractor = textExtractor;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.sessionStore = sessionStore;
        this.aiClient = aiClient;
        this.testRepository = testRepository;
        this.questionRepository = questionRepository;
        this.activityWriter = activityWriter;
    }

    /**
     * Calls AI outside a database transaction. A per-actor guard prevents accidental
     * duplicate requests from repeated clicks on the same application node.
     */
    public Preview generate(Long userId, Long testId, MultipartFile file, String text,
                            GenerateRequest rawRequest) {
        Test test = accessResolver.requireManageable(testId, userId);
        if (questionBankWriter.hasStudentActivity(testId)) {
            throw new IllegalArgumentException(MSG_QB_INSERT_LOCKED);
        }
        GenerateRequest request = validate(rawRequest);
        if (activeGenerations.putIfAbsent(userId, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(MSG_GENERATION_BUSY);
        }
        try {
            String material = file != null && !file.isEmpty()
                    ? textExtractor.extract(file)
                    : textExtractor.normalizePastedText(text);
            String expectedType = AiQuestionPromptBuilder.normalizeType(request.type());
            String reply = aiClient.chat(
                    promptBuilder.systemPrompt(),
                    promptBuilder.userMessage(request, material),
                    AiQuestionPromptBuilder.maxTokensFor(request.count()),
                    userId,
                    AiRequestLogger.SOURCE_QUESTION_GEN);
            List<DraftQuestion> drafts =
                    responseParser.parse(reply, request.count(), expectedType);
            return sessionStore.save(userId, test.getId(), drafts);
        } finally {
            activeGenerations.remove(userId);
        }
    }

    /**
     * Locks the exam and preview in a stable order. The consumed marker shares the same
     * transaction as the append, making double-confirm safe across nodes and on rollback.
     */
    @Transactional
    public ConfirmResult confirm(Long userId, Long testId, ConfirmRequest request) {
        Test test = accessResolver.requireManageableForUpdate(testId, userId);
        if (request == null) {
            throw new IllegalArgumentException(AiQuestionDraftSessionStore.MSG_SESSION_EXPIRED);
        }
        LoadedSession session =
                sessionStore.requireForUpdate(request.sessionId(), userId, testId);
        if (questionBankWriter.hasStudentActivity(testId)) {
            throw new IllegalArgumentException(MSG_QB_INSERT_LOCKED);
        }

        List<QuestionForm> selected =
                AiQuestionDraftSelector.select(session.questions(), request.indexes());
        if (selected.isEmpty()) {
            throw new IllegalArgumentException(MSG_NO_SELECTION);
        }

        int inserted = questionBankWriter.appendQuestions(testId, selected);
        int total = questionRepository.findByTestIdOrderBySortOrderAscIdAsc(testId).size();
        test.setTotalQuestions(total);
        testRepository.save(test);
        activityWriter.write(test.getId(), TestActivity.TYPE_UPDATED,
                "Chèn " + inserted + " câu hỏi do AI sinh vào bài test \""
                        + test.getTitle() + "\"",
                null, userId);
        sessionStore.consume(session);
        return new ConfirmResult(inserted);
    }

    private static GenerateRequest validate(GenerateRequest request) {
        if (request == null || request.count() < AiQuestionPromptBuilder.MIN_COUNT
                || request.count() > AiQuestionPromptBuilder.MAX_COUNT) {
            throw new IllegalArgumentException(MSG_COUNT);
        }
        String type = request.type() == null ? "" : request.type().trim();
        if (!Question.TYPE_MCQ.equalsIgnoreCase(type)
                && !Question.TYPE_MR.equalsIgnoreCase(type)) {
            throw new IllegalArgumentException(MSG_TYPE);
        }
        return new GenerateRequest(request.count(), type.toUpperCase(),
                request.difficulty());
    }
}
