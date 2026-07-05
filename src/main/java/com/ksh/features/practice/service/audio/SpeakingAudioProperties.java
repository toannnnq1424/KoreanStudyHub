package com.ksh.features.practice.service.audio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

@Component
public class SpeakingAudioProperties {
    private static final long MAX_CONFIGURED_AUDIO_BYTES = 100L * 1024L * 1024L;
    private static final Duration MAX_CONFIGURED_DURATION = Duration.ofMinutes(30);
    private static final Duration MAX_CONFIGURED_PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_CONFIGURED_STDOUT_BYTES = 1024 * 1024;
    private static final int MAX_CONFIGURED_STDERR_BYTES = 256 * 1024;

    private final Path privateLocalRoot;
    private final Path publicUploadRoot;
    private final String ffprobePath;
    private final Duration ffprobeTimeout;
    private final int maxProbeStdoutBytes;
    private final int maxProbeStderrBytes;
    private final long maxAudioBytes;
    private final Duration maxDuration;

    public SpeakingAudioProperties(
            @Value("${app.practice.speaking-audio.local-root:private-storage/practice-speaking-audio}") String privateLocalRoot,
            @Value("${app.upload.dir:uploads}") String publicUploadRoot,
            @Value("${app.practice.speaking-audio.ffprobe-path:ffprobe}") String ffprobePath,
            @Value("${app.practice.speaking-audio.ffprobe-timeout:10s}") Duration ffprobeTimeout,
            @Value("${app.practice.speaking-audio.max-probe-stdout-bytes:262144}") int maxProbeStdoutBytes,
            @Value("${app.practice.speaking-audio.max-probe-stderr-bytes:65536}") int maxProbeStderrBytes,
            @Value("${app.practice.speaking-audio.max-audio-bytes:26214400}") long maxAudioBytes,
            @Value("${app.practice.speaking-audio.max-duration:10m}") Duration maxDuration) {
        this.privateLocalRoot = Path.of(requireText(privateLocalRoot, "Private speaking-audio root is required"));
        this.publicUploadRoot = Path.of(requireText(publicUploadRoot, "Public upload root is required"));
        this.ffprobePath = requireText(ffprobePath, "ffprobe path is required");
        this.ffprobeTimeout = requireMax(requirePositive(ffprobeTimeout, "ffprobe timeout"),
                MAX_CONFIGURED_PROBE_TIMEOUT, "ffprobe timeout");
        this.maxProbeStdoutBytes = requireMax(requirePositive(maxProbeStdoutBytes, "ffprobe stdout limit"),
                MAX_CONFIGURED_STDOUT_BYTES, "ffprobe stdout limit");
        this.maxProbeStderrBytes = requireMax(requirePositive(maxProbeStderrBytes, "ffprobe stderr limit"),
                MAX_CONFIGURED_STDERR_BYTES, "ffprobe stderr limit");
        this.maxAudioBytes = requireMax(requirePositive(maxAudioBytes, "speaking-audio byte limit"),
                MAX_CONFIGURED_AUDIO_BYTES, "speaking-audio byte limit");
        this.maxDuration = requireMax(requirePositive(maxDuration, "speaking-audio duration limit"),
                MAX_CONFIGURED_DURATION, "speaking-audio duration limit");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static int requirePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static long requirePositive(long value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private static int requireMax(int value, int max, String label) {
        if (value > max) {
            throw new IllegalArgumentException(label + " exceeds the safety cap");
        }
        return value;
    }

    private static long requireMax(long value, long max, String label) {
        if (value > max) {
            throw new IllegalArgumentException(label + " exceeds the safety cap");
        }
        return value;
    }

    private static Duration requireMax(Duration value, Duration max, String label) {
        if (value.compareTo(max) > 0) {
            throw new IllegalArgumentException(label + " exceeds the safety cap");
        }
        return value;
    }

    public Path getPrivateLocalRoot() {
        return privateLocalRoot;
    }

    public Path getPublicUploadRoot() {
        return publicUploadRoot;
    }

    public String getFfprobePath() {
        return ffprobePath;
    }

    public Duration getFfprobeTimeout() {
        return ffprobeTimeout;
    }

    public int getMaxProbeStdoutBytes() {
        return maxProbeStdoutBytes;
    }

    public int getMaxProbeStderrBytes() {
        return maxProbeStderrBytes;
    }

    public long getMaxAudioBytes() {
        return maxAudioBytes;
    }

    public Duration getMaxDuration() {
        return maxDuration;
    }
}
