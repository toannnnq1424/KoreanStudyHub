package com.ksh.features.practice;

import com.ksh.features.practice.web.PracticeMediaRoutes;
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

    @Test
    void resultPagesDoNotRenderProviderOrPrivateStorageDiagnostics() throws IOException {
        String result = read("templates/practice/result.html");
        String detail = read("templates/practice/result-detail.html");
        String speakingFragment = read("templates/practice/result/speaking.html");
        String writingFragment = read("templates/practice/result/writing.html");

        assertThat(result + detail + speakingFragment + writingFragment)
                .doesNotContain(
                        "apiKey",
                        "Authorization",
                        "Bearer ",
                        "providerRaw",
                        "rawProvider",
                        "rawRequest",
                        "rawResponse",
                        "promptBody",
                        "storageKey",
                        "storagePath",
                        "localFilePath",
                        "privateRoot");
    }

    @Test
    void privateLearnerMediaBoundaryDoesNotUsePublicUploadsRoute() throws IOException {
        String player = read("templates/practice/player.html");
        String result = read("templates/practice/result.html");
        String detail = read("templates/practice/result-detail.html");
        String javascript = read("static/js/practice.js");

        assertThat(PracticeMediaRoutes.playbackPath(1L, 2L, 3L))
                .isEqualTo("/practice/attempts/1/questions/2/speaking-media/3/content")
                .doesNotStartWith("/uploads/");
        assertThat(player + result + detail + javascript)
                .contains("speaking-media")
                .doesNotContain("/uploads/practice-speaking")
                .doesNotContain("private-storage")
                .doesNotContain("storageKey")
                .doesNotContain("storagePath");
    }

    @Test
    void publicUploadsAndPrivateSpeakingMediaRootsRemainSeparatedInConfiguration() throws IOException {
        String applicationProperties = Files.readString(Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8);
        String webConfig = Files.readString(Path.of("src/main/java/com/ksh/config/WebConfig.java"), StandardCharsets.UTF_8);
        String audioProperties = Files.readString(
                Path.of("src/main/java/com/ksh/features/practice/service/audio/SpeakingAudioProperties.java"),
                StandardCharsets.UTF_8);

        assertThat(webConfig).contains("/uploads/**");
        assertThat(applicationProperties).contains("app.upload.dir=${UPLOAD_DIR:uploads}");
        assertThat(audioProperties)
                .contains("@Value(\"${app.practice.speaking-audio.local-root:private-storage/practice-speaking-audio}\")")
                .contains("@Value(\"${app.upload.dir:uploads}\")");
        assertThat(audioProperties).contains("publicUploadRoot");
        assertThat(audioProperties).contains("privateLocalRoot");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
