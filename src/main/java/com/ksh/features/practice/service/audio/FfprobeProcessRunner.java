package com.ksh.features.practice.service.audio;

import java.nio.file.Path;

public interface FfprobeProcessRunner {
    FfprobeProcessResult run(Path privateMediaPath);
}
