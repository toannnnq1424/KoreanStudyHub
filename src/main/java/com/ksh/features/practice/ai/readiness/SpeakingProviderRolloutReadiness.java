package com.ksh.features.practice.ai.readiness;

import com.ksh.features.practice.ai.speaking.SpeakingEvaluatorCapability;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluatorProperties;
import com.ksh.features.practice.ai.speaking.transcription.SpeakingTranscriptionProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpeakingProviderRolloutReadiness {

    public static final String ALLOWED_TRANSCRIPTION_PROVIDER = "openai";
    public static final String ALLOWED_EVALUATOR_PROVIDER = "openai-compatible";

    private final SpeakingTranscriptionProperties transcriptionProperties;
    private final SpeakingEvaluatorProperties evaluatorProperties;

    public SpeakingProviderRolloutReadiness(
            SpeakingTranscriptionProperties transcriptionProperties,
            SpeakingEvaluatorProperties evaluatorProperties
    ) {
        this.transcriptionProperties = transcriptionProperties;
        this.evaluatorProperties = evaluatorProperties;
    }

    public AiReadinessReport assessLiveSpeakingProviderReadiness() {
        List<AiReadinessIssue> issues = new ArrayList<>();

        boolean transcriptionEnabled = transcriptionProperties.enabled();
        boolean evaluatorEnabled = evaluatorProperties.enabled();
        if (!transcriptionEnabled || !evaluatorEnabled) {
            issues.add(AiReadinessIssue.blocker(
                    "SPEAKING_AI_GATES_DISABLED",
                    "Live Speaking AI chỉ được bật khi cả transcription gate và evaluator gate đều enabled.",
                    "8F-A"));
        }
        if (transcriptionEnabled != evaluatorEnabled) {
            issues.add(AiReadinessIssue.blocker(
                    "SPEAKING_AI_GATE_MISMATCH",
                    "Không được bật lệch một gate; submit live cần cả transcription và evaluator.",
                    "8F-A"));
        }
        if (!ALLOWED_TRANSCRIPTION_PROVIDER.equals(transcriptionProperties.provider())) {
            issues.add(AiReadinessIssue.blocker(
                    "UNSUPPORTED_SPEAKING_TRANSCRIPTION_PROVIDER",
                    "8F-A chỉ cho phép OpenAI STT primary cho Speaking transcription.",
                    "8F-A"));
        }
        if (!ALLOWED_EVALUATOR_PROVIDER.equals(evaluatorProperties.provider())) {
            issues.add(AiReadinessIssue.blocker(
                    "UNSUPPORTED_SPEAKING_EVALUATOR_PROVIDER",
                    "8F-A chỉ cho phép OpenAI-compatible evaluator path hiện tại.",
                    "8F-A"));
        }
        if (transcriptionEnabled && transcriptionProperties.apiKey().isBlank()) {
            issues.add(AiReadinessIssue.blocker(
                    "MISSING_TRANSCRIPTION_API_KEY",
                    "Thiếu API key cho Speaking transcription khi gate được bật.",
                    "8F-A"));
        }
        if (evaluatorEnabled && evaluatorProperties.apiKey().isBlank()) {
            issues.add(AiReadinessIssue.blocker(
                    "MISSING_EVALUATOR_API_KEY",
                    "Thiếu API key cho Speaking evaluator khi gate được bật.",
                    "8F-A"));
        }
        SpeakingEvaluatorCapability currentCapability =
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION;
        if (!currentCapability.directLearnerAudioRequired()
                || !currentCapability.acousticCriteriaSupported()
                || !currentCapability.holisticScoreSupported()) {
            issues.add(AiReadinessIssue.blocker(
                    "DIRECT_AUDIO_FULL_EVALUATOR_NOT_READY",
                    "Evaluator hiện tại chỉ nhận transcript; chưa có evaluator trực tiếp nhận audio người học, hiệu chuẩn acoustic và phê duyệt rollout.",
                    "P15-PRE-01"));
        }
        if (transcriptionEnabled && evaluatorEnabled) {
            issues.add(AiReadinessIssue.info(
                    "LIVE_PROVIDER_PATH_CONFIGURED",
                    "Transcript provider path đã được cấu hình nhưng không phải direct-audio full evaluator và không mở rollout.",
                    "8F-E"));
        }

        return new AiReadinessReport("speaking-provider-rollout", issues);
    }
}
