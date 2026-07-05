package com.ksh.features.practice.service.audio;

import java.nio.file.Path;

public interface SpeakingAudioInspector {
    SpeakingAudioInspection inspect(Path privateMediaPath);
}
