package com.ksh.features.practice.ai.controlplane;

import java.util.Set;

public enum PracticeAiPurpose {
    PRACTICE_PDF_AUTHORING(
            "Biên soạn từ PDF",
            "AUTHORING_SOURCE",
            Set.of("STRICT_JSON_SCHEMA")),
    PRACTICE_RL_EXPLANATION(
            "Giải thích Đọc / Nghe",
            "PUBLISHED_QUESTION_EVIDENCE",
            Set.of("STRICT_JSON_SCHEMA", "IMAGE_INPUT")),
    PRACTICE_WRITING_EVALUATION(
            "Chấm bài Viết",
            "LEARNER_WRITING_RESPONSE",
            Set.of("STRICT_JSON_SCHEMA", "IMAGE_INPUT")),
    PRACTICE_SPEAKING_EVALUATION(
            "Chấm bài Nói",
            "LEARNER_SPEAKING_TRANSCRIPT",
            Set.of("STRICT_JSON_SCHEMA", "IMAGE_INPUT", "TRANSCRIPT_TEXT_INPUT")),
    PRACTICE_SPEAKING_STT(
            "Chuyển giọng nói thành văn bản",
            "SPEAKING_AUDIO",
            Set.of("BATCH_TRANSCRIPTION")),
    PRACTICE_SPEAKING_TTS(
            "Tạo giọng đọc đề bài",
            "LECTURER_PROMPT_AUDIO",
            Set.of("SPEECH_SYNTHESIS")),
    PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION(
            "Đánh giá âm thanh bài Nói",
            "LEARNER_SPEAKING_AUDIO",
            Set.of("STRICT_JSON_SCHEMA", "DIRECT_AUDIO_INPUT"));

    private final String displayName;
    private final String dataClass;
    private final Set<String> requiredCapabilities;

    PracticeAiPurpose(
            String displayName,
            String dataClass,
            Set<String> requiredCapabilities) {
        this.displayName = displayName;
        this.dataClass = dataClass;
        this.requiredCapabilities = Set.copyOf(requiredCapabilities);
    }

    public String displayName() {
        return displayName;
    }

    public String dataClass() {
        return dataClass;
    }

    public Set<String> requiredCapabilities() {
        return requiredCapabilities;
    }

    public boolean structuredJson() {
        return requiredCapabilities.contains("STRICT_JSON_SCHEMA");
    }
}
