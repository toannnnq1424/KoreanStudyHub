package com.ksh.features.practice.ai.speaking.acoustic;

import org.springframework.stereotype.Component;

import java.util.Optional;

/** Production remains red until reviewed corpus/calibration evidence is configured. */
@Component
public final class DisabledDirectAudioCalibrationProfileAuthority
        implements DirectAudioCalibrationProfileAuthority {

    @Override
    public Optional<CalibrationProfile> resolve(String profileId, String version) {
        return Optional.empty();
    }
}
