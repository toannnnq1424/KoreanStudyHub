package com.ksh.features.practice.service.audio;

public final class FfprobeProcessResult {
    private final int exitCode;
    private final String stdout;
    private final String stderr;

    public FfprobeProcessResult(int exitCode, String stdout, String stderr) {
        this.exitCode = exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    @Override
    public String toString() {
        return "FfprobeProcessResult{exitCode=" + exitCode + "}";
    }
}
