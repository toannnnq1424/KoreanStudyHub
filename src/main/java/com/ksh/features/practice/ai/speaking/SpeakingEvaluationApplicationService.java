package com.ksh.features.practice.ai.speaking;

import com.ksh.features.practice.ai.media.AiImageEvidence;
import com.ksh.features.practice.ai.media.AiQuestionImageResolver;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionClient;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionMediaResolver;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionRequest;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionResult;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpeakingEvaluationApplicationService {
    private final SpeakingTranscriptionMediaResolver mediaResolver;
    private final SpeakingTranscriptionClient transcriptionClient;
    private final SpeakingEvaluationOrchestrator orchestrator;
    private final SpeakingEvaluationReusePolicy reusePolicy;
    private final SpeakingTranscriptionProperties transcriptionProperties;
    private final SpeakingEvaluatorProperties evaluatorProperties;
    private final AiQuestionImageResolver imageResolver;
    private final boolean textFallbackEnabled;

    public SpeakingEvaluationApplicationService(
            SpeakingTranscriptionMediaResolver mediaResolver,
            SpeakingTranscriptionClient transcriptionClient,
            SpeakingEvaluationOrchestrator orchestrator,
            SpeakingEvaluationReusePolicy reusePolicy,
            SpeakingTranscriptionProperties transcriptionProperties,
            SpeakingEvaluatorProperties evaluatorProperties,
            boolean textFallbackEnabled
    ) {
        this(mediaResolver, transcriptionClient, orchestrator, reusePolicy,
                transcriptionProperties, evaluatorProperties, null, textFallbackEnabled);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SpeakingEvaluationApplicationService(
            SpeakingTranscriptionMediaResolver mediaResolver,
            SpeakingTranscriptionClient transcriptionClient,
            SpeakingEvaluationOrchestrator orchestrator,
            SpeakingEvaluationReusePolicy reusePolicy,
            SpeakingTranscriptionProperties transcriptionProperties,
            SpeakingEvaluatorProperties evaluatorProperties,
            AiQuestionImageResolver imageResolver,
            @Value("${app.practice.speaking-evaluator.text-fallback-enabled:false}") boolean textFallbackEnabled
    ) {
        this.mediaResolver = mediaResolver;
        this.transcriptionClient = transcriptionClient;
        this.orchestrator = orchestrator;
        this.reusePolicy = reusePolicy;
        this.transcriptionProperties = transcriptionProperties;
        this.evaluatorProperties = evaluatorProperties;
        this.imageResolver = imageResolver;
        this.textFallbackEnabled = textFallbackEnabled;
    }

    public boolean enabled() {
        return transcriptionProperties.enabled() && evaluatorProperties.enabled();
    }

    public Evaluation evaluateQuestion(EvaluationInput input) {
        if (!enabled()) {
            return Evaluation.skipped("SPEAKING_AI_DISABLED");
        }
        AiImageEvidence imageEvidence = imageResolver == null
                ? null
                : imageResolver.resolve(input.questionImageReference(), input.userId()).orElse(null);
        SpeakingTranscriptionMediaResolver.Resolution resolution =
                mediaResolver.resolveForOwner(input.userId(), input.attemptId(), input.questionId());
        if (resolution.request().isPresent()) {
            return evaluateAudio(input, resolution.request().orElseThrow(), imageEvidence);
        }
        if (textFallbackEnabled && SpeakingEvaluationIdentity.normalizeText(input.textFallbackAnswer()) != null) {
            return evaluateTextFallback(input, imageEvidence);
        }
        SpeakingEvaluationResult failure = resolution.failure()
                .map(transcription -> orchestrator.evaluate(
                        orchestratorInput(input, null, transcription, imageEvidence, false)))
                .orElse(null);
        return Evaluation.evaluated(failure == null
                ? input.storedResult()
                : failure);
    }

    private Evaluation evaluateAudio(EvaluationInput input, SpeakingTranscriptionRequest request,
                                     AiImageEvidence imageEvidence) {
        SpeakingEvaluationIdentity identity = SpeakingEvaluationIdentity.audio(
                input.attemptId(),
                input.questionId(),
                request.mediaId(),
                request.mediaVersion(),
                transcriptionProperties.model(),
                evaluatorProperties.model(),
                evaluatorProperties.promptVersion(),
                evaluatorProperties.rubricVersion(),
                evaluatorProperties.schemaVersion());
        SpeakingEvaluationReusePolicy.Decision decision =
                reusePolicy.decide(input.storedResult(), identity, true);
        if (decision.reuse()) {
            return Evaluation.reused(input.storedResult(), decision.reason());
        }

        SpeakingTranscriptionResult transcription = transcriptionClient.transcribe(request);
        SpeakingEvaluationResult evaluated = orchestrator.evaluate(
                orchestratorInput(input, request, transcription, imageEvidence, false));
        evaluated = withIdentity(evaluated, identity, transcription);
        if (!currentAudioIdentityMatches(input, identity)) {
            return Evaluation.evaluated(staleAudioIdentityFailure(identity));
        }
        evaluated = reusePolicy.preserveSuccessOnTransientFailure(input.storedResult(), evaluated, identity);
        return Evaluation.evaluated(evaluated);
    }

    private Evaluation evaluateTextFallback(EvaluationInput input, AiImageEvidence imageEvidence) {
        SpeakingEvaluationIdentity identity = SpeakingEvaluationIdentity.textFallback(
                input.attemptId(),
                input.questionId(),
                input.textFallbackAnswer(),
                evaluatorProperties.model(),
                evaluatorProperties.promptVersion(),
                evaluatorProperties.rubricVersion(),
                evaluatorProperties.schemaVersion());
        SpeakingEvaluationReusePolicy.Decision decision =
                reusePolicy.decide(input.storedResult(), identity, true);
        if (decision.reuse()) {
            return Evaluation.reused(input.storedResult(), decision.reason());
        }
        SpeakingTranscriptionResult transcription = mediaResolver.textFallback(input.textFallbackAnswer());
        SpeakingEvaluationResult evaluated = orchestrator.evaluate(
                orchestratorInput(input, null, transcription, imageEvidence, true));
        evaluated = reusePolicy.preserveSuccessOnTransientFailure(input.storedResult(), evaluated, identity);
        return Evaluation.evaluated(evaluated);
    }

    private SpeakingEvaluationOrchestrator.Input orchestratorInput(
            EvaluationInput input,
            SpeakingTranscriptionRequest request,
            SpeakingTranscriptionResult transcription,
            AiImageEvidence imageEvidence,
            boolean textFallback
    ) {
        return new SpeakingEvaluationOrchestrator.Input(
                input.attemptId(),
                input.questionId(),
                input.questionText(),
                input.targetLevel(),
                input.expectedAnswerGuidance(),
                request == null ? null : request.mediaId(),
                request == null ? null : request.mediaVersion(),
                request == null ? null : request.mimeType(),
                request == null ? null : request.byteSize(),
                request == null ? transcription.durationMs() : request.durationMs(),
                imageEvidence,
                transcription,
                textFallback ? input.textFallbackAnswer() : null);
    }

    private SpeakingEvaluationResult withIdentity(
            SpeakingEvaluationResult result,
            SpeakingEvaluationIdentity identity,
            SpeakingTranscriptionResult transcription
    ) {
        if (result == null || identity == null) {
            return result;
        }
        return new SpeakingEvaluationResult(
                result.evaluationStatus(),
                result.scoreAvailable(),
                result.source(),
                identity.evaluatorModel() == null ? result.model() : identity.evaluatorModel(),
                identity.transcriptionModel() == null ? result.transcriptionModel() : identity.transcriptionModel(),
                identity.promptVersion() == null ? result.promptVersion() : identity.promptVersion(),
                identity.rubricVersion() == null ? result.rubricVersion() : identity.rubricVersion(),
                identity.schemaVersion() == null ? result.schemaVersion() : identity.schemaVersion(),
                result.evaluatorCapability(),
                result.evidenceMode(),
                result.evidenceContractVersion(),
                result.contractTrust(),
                identity.audioMediaId() == null ? result.audioMediaId() : identity.audioMediaId(),
                identity.mediaVersion() == null ? result.mediaVersion() : identity.mediaVersion(),
                result.transcript(),
                result.normalizedTranscript(),
                result.actuallyHeardTranscript(),
                result.interpretedIntent(),
                result.intentConfidence(),
                result.transcriptConfidence() == null && transcription != null
                        ? transcription.transcriptConfidence() : result.transcriptConfidence(),
                result.listenerBurden(),
                result.overallScore(),
                result.levelLabel(),
                result.overallSummary(),
                result.taskAchievementSummary(),
                result.majorStrengths(),
                result.majorNeedsImprovement(),
                result.actionPlan(),
                result.criterionFeedback(),
                result.transcriptAnnotations(),
                result.strengths(),
                result.needsImprovement(),
                result.confidenceNotes(),
                result.rubricScores(),
                result.findings(),
                result.evidence(),
                result.recommendations(),
                result.upgradedAnswer(),
                result.sampleAnswer(),
                result.pronunciationAdvisory(),
                result.fluencyObservations(),
                result.errorCategory(),
                result.retryable());
    }

    private boolean currentAudioIdentityMatches(EvaluationInput input, SpeakingEvaluationIdentity identity) {
        if (identity == null || identity.audioMediaId() == null) {
            return true;
        }
        return mediaResolver.resolveForOwner(input.userId(), input.attemptId(), input.questionId())
                .request()
                .map(current -> identity.audioMediaId().equals(current.mediaId())
                        && identity.mediaVersion().equals(current.mediaVersion()))
                .orElse(false);
    }

    private SpeakingEvaluationResult staleAudioIdentityFailure(SpeakingEvaluationIdentity identity) {
        return new SpeakingEvaluationResult(
                SpeakingEvaluationStatus.AUDIO_UNAVAILABLE,
                false,
                SpeakingEvaluationSource.PROVIDER,
                identity.evaluatorModel(),
                identity.transcriptionModel(),
                identity.promptVersion(),
                identity.rubricVersion(),
                identity.schemaVersion(),
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                SpeakingContractTrust.CURRENT_VERIFIED,
                identity.audioMediaId(),
                identity.mediaVersion(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                "STALE_AUDIO_IDENTITY",
                true);
    }

    public record EvaluationInput(
            Long userId,
            Long attemptId,
            Long questionId,
            String questionText,
            String targetLevel,
            String expectedAnswerGuidance,
            String questionImageReference,
            String textFallbackAnswer,
            SpeakingEvaluationResult storedResult
    ) {
        public EvaluationInput(
                Long userId,
                Long attemptId,
                Long questionId,
                String questionText,
                String targetLevel,
                String expectedAnswerGuidance,
                String textFallbackAnswer,
                SpeakingEvaluationResult storedResult
        ) {
            this(userId, attemptId, questionId, questionText, targetLevel,
                    expectedAnswerGuidance, null, textFallbackAnswer, storedResult);
        }

        @Override
        public String toString() {
            return "EvaluationInput{"
                    + "userIdPresent=" + (userId != null)
                    + ", attemptId=" + attemptId
                    + ", questionId=" + questionId
                    + ", questionTextPresent=" + (questionText != null && !questionText.isBlank())
                    + ", targetLevelPresent=" + (targetLevel != null && !targetLevel.isBlank())
                    + ", expectedAnswerGuidancePresent=" + (expectedAnswerGuidance != null && !expectedAnswerGuidance.isBlank())
                    + ", questionImageReferencePresent=" + (questionImageReference != null && !questionImageReference.isBlank())
                    + ", textFallbackAnswerPresent=" + (textFallbackAnswer != null && !textFallbackAnswer.isBlank())
                    + ", storedStatus=" + (storedResult == null ? null : storedResult.evaluationStatus())
                    + '}';
        }
    }

    public record Evaluation(SpeakingEvaluationResult result, boolean reused, boolean skipped, String reason) {
        static Evaluation skipped(String reason) {
            return new Evaluation(null, false, true, reason);
        }

        static Evaluation reused(SpeakingEvaluationResult result, String reason) {
            return new Evaluation(result, true, false, reason);
        }

        static Evaluation evaluated(SpeakingEvaluationResult result) {
            return new Evaluation(result, false, false, "EVALUATED");
        }
    }
}
