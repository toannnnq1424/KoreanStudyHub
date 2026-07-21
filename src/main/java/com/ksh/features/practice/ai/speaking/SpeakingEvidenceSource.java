package com.ksh.features.practice.ai.speaking;

public enum SpeakingEvidenceSource {
    TRANSCRIPT,
    AUDIO_METADATA,
    PROMPT,
    INTERPRETED_INTENT;

    public boolean transcriptLanguageGrounding() {
        return this == TRANSCRIPT;
    }
}
