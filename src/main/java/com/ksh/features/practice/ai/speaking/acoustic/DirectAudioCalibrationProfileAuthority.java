package com.ksh.features.practice.ai.speaking.acoustic;

import java.util.Objects;
import java.util.Optional;

/** Immutable calibration evidence authority; it intentionally defines no weights. */
public interface DirectAudioCalibrationProfileAuthority {

    Optional<CalibrationProfile> resolve(String profileId, String version);

    record CalibrationProfile(
            String profileId,
            String version,
            String language,
            String corpusEvidenceId,
            String acousticCalibrationEvidenceId,
            String fairnessEvidenceId,
            String repeatabilityEvidenceId,
            boolean scoreReleaseApproved) {
        public CalibrationProfile {
            profileId = required(profileId);
            version = required(version);
            language = required(language);
            corpusEvidenceId = required(corpusEvidenceId);
            acousticCalibrationEvidenceId = required(acousticCalibrationEvidenceId);
            fairnessEvidenceId = required(fairnessEvidenceId);
            repeatabilityEvidenceId = required(repeatabilityEvidenceId);
        }

        private static String required(String value) {
            String normalized = Objects.requireNonNull(value).trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Calibration field is required");
            }
            return normalized;
        }
    }
}
