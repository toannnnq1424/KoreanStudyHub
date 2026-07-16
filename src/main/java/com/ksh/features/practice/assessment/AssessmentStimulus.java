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

    public boolean hasUsableEvidence() {
        return switch (type) {
            case READING_PASSAGE -> passageText != null && !passageText.isBlank();
            case LISTENING_AUDIO -> approved && transcriptText != null && !transcriptText.isBlank();
        };
    }

    public String evidenceText() {
        if (!hasUsableEvidence()) {
            return "";
        }
        return type == StimulusType.READING_PASSAGE ? passageText : transcriptText;
    }

    public enum StimulusType {
        READING_PASSAGE,
        LISTENING_AUDIO
    }
}
