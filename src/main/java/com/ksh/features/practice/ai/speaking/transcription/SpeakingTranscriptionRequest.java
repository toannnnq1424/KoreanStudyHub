package com.ksh.features.practice.ai.speaking.transcription;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record SpeakingTranscriptionRequest(
        Long mediaId,
        Long attemptId,
        Long questionId,
        Long mediaVersion,
        String mimeType,
        Long byteSize,
        Long durationMs,
        String language,
        InputStreamSupplier inputStreamSupplier
) {
    public SpeakingTranscriptionRequest {
        Objects.requireNonNull(inputStreamSupplier, "inputStreamSupplier");
    }

    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream open() throws IOException;
    }

    @Override
    public String toString() {
        return "SpeakingTranscriptionRequest{"
                + "mediaId=" + mediaId
                + ", attemptId=" + attemptId
                + ", questionId=" + questionId
                + ", mediaVersion=" + mediaVersion
                + ", mimeType='" + mimeType + '\''
                + ", byteSize=" + byteSize
                + ", durationMs=" + durationMs
                + ", language='" + language + '\''
                + '}';
    }
}
