package com.ksh.features.practice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeSpeakingMediaUiResourceTest {

    private static final Path RESOURCE_ROOT = Path.of("src", "main", "resources");

    @Test
    void playerKeepsTextFallbackAndGatesSpeakingRecorder() throws IOException {
        String player = read("templates/practice/player.html");

        assertThat(player).contains(
                "q.questionType() == 'SPEAKING'",
                "speakingMediaUploadEnabled",
                "data-upload-url",
                "_csrf.token",
                "answer_");
        assertThat(player).doesNotContain(
                "storageKey",
                "contentHash",
                "originalFilename",
                "userId");
        assertThat(player.indexOf("</form>"))
                .isLessThan(player.indexOf("<dialog id=\"ksh-speaking-consent\""));
    }

    @Test
    void javascriptCoversRecordingConsentUploadDeleteAndSubmitStates() throws IOException {
        String javascript = read("static/js/practice.js");

        assertThat(javascript).contains(
                "window.MediaRecorder",
                "navigator.mediaDevices?.getUserMedia",
                "NotAllowedError",
                "NotFoundError",
                "window.sessionStorage",
                "X-CSRF-TOKEN",
                "xhr.upload.addEventListener('progress'",
                "method: 'DELETE'",
                "form.dataset.speakingMediaPlaybackEnabled === 'true'",
                "ksh-recorder-rerecord",
                "panel.dataset.busy === 'recording'",
                "panel.dataset.busy === 'uploading'");
        assertThat(javascript).doesNotContain("pronunciationScore", "officialTopikScore");
    }

    @Test
    void resultPagesUseOwnerProtectedPlaybackPathWithoutUnsupportedClaims() throws IOException {
        String result = read("templates/practice/result.html");
        String detail = read("templates/practice/result-detail.html");

        assertThat(result).contains("speakingMediaPlaybackEnabled", "media.playbackPath()");
        assertThat(detail).contains("speakingMediaPlaybackEnabled", "media.playbackPath()");
        assertThat(result + detail).doesNotContain(
                "storageKey",
                "contentHash",
                "originalFilename",
                "AI pronunciation",
                "chấm phát âm bằng AI");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
