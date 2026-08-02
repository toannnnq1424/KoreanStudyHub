package com.ksh.features.admin.settings.dto;

import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class PracticeAiSettingsDtos {

    public static final String MASKED = "********";

    private PracticeAiSettingsDtos() {
    }

    public record ProfileForm(
            Long id,
            Long revision,
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String profileCode,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Pattern(regexp = "OPENAI_COMPATIBLE") String providerFamily,
            @NotBlank @Size(max = 500)
            @Pattern(regexp = "^https?://.+") String baseUrl,
            @Size(max = 4096) String credentialSecret,
            boolean enabled
    ) {
        public static ProfileForm empty() {
            return new ProfileForm(null, null, "", "", "OPENAI_COMPATIBLE", "", "", false);
        }
    }

    public record ProfileRow(
            Long id,
            String profileCode,
            String displayName,
            String providerFamily,
            String baseUrl,
            boolean enabled,
            long revision,
            LocalDateTime updatedAt
    ) {
    }

    public record BindingForm(
            @NotNull PracticeAiPurpose purpose,
            @NotNull Long providerProfileId,
            @NotBlank @Size(max = 150) String model,
            boolean pdfImageInput,
            @Min(100) @Max(30000) int connectTimeoutMs,
            @Min(1000) @Max(120000) int readTimeoutMs,
            @Min(0) @Max(3) int maxRetries,
            @Min(1) @Max(26214400) int maxRequestBytes,
            @Min(16384) @Max(8388608) int maxResponseBytes,
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String retentionCode,
            boolean enabled,
            Long revision
    ) {
        public static BindingForm empty(PracticeAiPurpose purpose) {
            int requestBytes = purpose == PracticeAiPurpose.PRACTICE_SPEAKING_STT
                    ? 26_214_400
                    : 8_388_608;
            return new BindingForm(
                    purpose,
                    null,
                    "",
                    false,
                    5_000,
                    60_000,
                    2,
                    requestBytes,
                    2_097_152,
                    defaultRetention(purpose),
                    false,
                    null);
        }

        private static String defaultRetention(PracticeAiPurpose purpose) {
            return switch (purpose) {
                case PRACTICE_PDF_AUTHORING -> "PRACTICE_AUTHORING_V1";
                case PRACTICE_RL_EXPLANATION -> "PUBLISHED_EXPLANATION_V1";
                case PRACTICE_WRITING_EVALUATION -> "WRITING_EVALUATION_V1";
                case PRACTICE_SPEAKING_EVALUATION -> "SPEAKING_TRANSCRIPT_EVAL_V1";
                case PRACTICE_SPEAKING_STT -> "SPEAKING_AUDIO_STT_V1";
                case PRACTICE_SPEAKING_TTS -> "LECTURER_PROMPT_TTS_V1";
            };
        }
    }

    public record BindingRow(
            PracticeAiPurpose purpose,
            String purposeLabel,
            String requiredCapabilities,
            Long providerProfileId,
            String providerProfileCode,
            String model,
            boolean enabled,
            long revision,
            String retentionCode,
            LocalDateTime updatedAt,
            List<CapabilityRunRow> recentRuns
    ) {
        public boolean configured() {
            return providerProfileId != null;
        }
    }

    public record CapabilityRunRow(
            Long id,
            Long bindingRevision,
            String status,
            Long durationMs,
            String errorCode,
            LocalDateTime startedAt
    ) {
    }

    public record CapabilityTestResult(
            boolean ok,
            String status,
            String errorCode,
            Long bindingRevision,
            long durationMs
    ) {
    }
}
