package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.assessment.QuestionContent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Resolves evaluator task context from immutable question-version state only.
 * It deliberately has no dependency on mutable prompt sources, tasks or
 * artifacts.
 */
@Service
public class SpeakingPromptEvaluationContextService {
    public static final String LEGACY_CONTRACT_IDENTITY =
            "question-content-v1-immutable-prompt";

    private final SpeakingPromptVersionContextRepository contextRepository;

    public SpeakingPromptEvaluationContextService(
            SpeakingPromptVersionContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    public EvaluatorContext resolve(
            Long questionVersionId,
            String questionContentSchemaVersion,
            String immutableQuestionPrompt) {
        if (!QuestionContent.SCHEMA_VERSION_V2.equals(
                questionContentSchemaVersion)) {
            return legacy(questionVersionId, immutableQuestionPrompt);
        }
        if (questionVersionId == null) {
            throw new IllegalStateException(
                    "Không thể đánh giá Speaking v2: bài làm thiếu phiên bản câu hỏi bất biến.");
        }
        SpeakingPromptVersionContext context = contextRepository
                .findById(questionVersionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Không thể đánh giá Speaking v2: thiếu ngữ cảnh đề bất biến."));
        if (!Objects.equals(questionVersionId, context.getQuestionVersionId())) {
            throw new IllegalStateException(
                    "Không thể đánh giá Speaking v2: ngữ cảnh đề bị liên kết sai phiên bản.");
        }
        context.verifyIntegrity();
        return new EvaluatorContext(
                questionVersionId,
                context.getPromptContextText(),
                context.getPromptContextFingerprint(),
                SpeakingPromptContextIdentity.CONTRACT_IDENTITY);
    }

    public static EvaluatorContext legacy(
            Long questionVersionId,
            String immutableQuestionPrompt) {
        String exactPrompt =
                immutableQuestionPrompt == null ? "" : immutableQuestionPrompt;
        String canonical = LEGACY_CONTRACT_IDENTITY + "\n"
                + (questionVersionId == null ? "" : questionVersionId) + "\n"
                + SpeakingPromptAiContract.exactBytesSha256(
                        exactPrompt.getBytes(StandardCharsets.UTF_8));
        String fingerprint = SpeakingPromptAiContract.exactBytesSha256(
                canonical.getBytes(StandardCharsets.UTF_8));
        return new EvaluatorContext(
                questionVersionId,
                exactPrompt,
                fingerprint,
                LEGACY_CONTRACT_IDENTITY);
    }

    public record EvaluatorContext(
            Long questionVersionId,
            String promptContext,
            String promptContextFingerprint,
            String promptContextContractIdentity) {
        public EvaluatorContext {
            if (promptContext == null
                    || promptContextFingerprint == null
                    || promptContextContractIdentity == null) {
                throw new IllegalArgumentException(
                        "Immutable Speaking evaluator context is incomplete.");
            }
        }
    }
}
