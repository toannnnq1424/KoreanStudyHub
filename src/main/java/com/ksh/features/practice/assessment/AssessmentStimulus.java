package com.ksh.features.practice.assessment;

public record AssessmentStimulus(
        String schemaVersion,
        StimulusType type,
        String passageText,
        String transcriptText,
        String mediaReference,
        String provenance,
        boolean approved
) {
    public static final String SCHEMA_VERSION = "assessment-stimulus-v1";

    public AssessmentStimulus {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported assessment stimulus schema: " + schemaVersion);
        }
        if (type == null) {
            throw new IllegalArgumentException("Assessment stimulus type is required");
        }
    }

    public static AssessmentStimulus readingPassage(String passageText, String provenance) {
        return new AssessmentStimulus(
                SCHEMA_VERSION,
                StimulusType.READING_PASSAGE,
                passageText,
                null,
                null,
                provenance,
                true
        );
    }

    public static AssessmentStimulus listeningAudio(String mediaReference,
                                                    String transcriptText,
                                                    String provenance,
                                                    boolean approved) {
        return new AssessmentStimulus(
                SCHEMA_VERSION,
                StimulusType.LISTENING_AUDIO,
                null,
                transcriptText,
                mediaReference,
                provenance,
                approved
        );
    }

    /**
     * Immutable standalone questions use their own prompt as the only
     * authoritative linguistic source. This is distinct from a missing
     * passage/transcript: the prompt is versioned with the question and may be
     * referenced by exact UTF-16 offsets.
     */
    public static AssessmentStimulus standalonePrompt(
            String prompt,
            String provenance) {
        return new AssessmentStimulus(
                SCHEMA_VERSION,
                StimulusType.STANDALONE_PROMPT,
                prompt,
                null,
                null,
                provenance,
                true
        );
    }

    public boolean hasUsableEvidence() {
        return switch (type) {
            case READING_PASSAGE -> passageText != null && !passageText.isBlank();
            case LISTENING_AUDIO -> approved && transcriptText != null && !transcriptText.isBlank();
            case STANDALONE_PROMPT -> approved
                    && passageText != null
                    && !passageText.isBlank();
        };
    }

    public String evidenceText() {
        if (!hasUsableEvidence()) {
            return "";
        }
        return type == StimulusType.LISTENING_AUDIO
                ? transcriptText
                : passageText;
    }

    public enum StimulusType {
        READING_PASSAGE,
        LISTENING_AUDIO,
        STANDALONE_PROMPT
    }
}
