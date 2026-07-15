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
        String player = read("templates/practice/player-speaking.html");

        assertThat(player).contains(
                "speakingMediaUploadEnabled",
                "speakingDeliveryJson",
                "data-interrupt-url",
                "data-prompt-audio",
                "data-prompt-media",
                "data-prompt-image",
                "data-state-label",
                "data-wave",
                "_csrf.token",
                "spp-submit-form");
        assertThat(player).doesNotContain(
                "textarea",
                "answer_",
                "storageKey",
                "contentHash",
                "originalFilename",
                "userId");
    }

    @Test
    void javascriptCoversRecordingConsentUploadDeleteAndSubmitStates() throws IOException {
        String javascript = read("static/js/practice/player-speaking.js");

        assertThat(javascript).contains(
                "MediaRecorder.isTypeSupported",
                "navigator.mediaDevices.getUserMedia",
                "currentQuestion.imageReference",
                "Thời gian chuẩn bị còn lại",
                "Thời gian trả lời còn lại",
                "Đang lưu câu trả lời",
                "payload.status !== \"READY\"",
                "executeQuestion(currentIndex + 1)",
                "submitForm.requestSubmit()",
                "promptAudioCleanup",
                "removeEventListener(\"ended\", onEnded)",
                "removeEventListener(\"error\", onAudioError)",
                "interruptRequest",
                "keepalive: true");
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
        String player = read("templates/practice/player-speaking.html");
        String result = read("templates/practice/result.html");
        String detail = read("templates/practice/result-detail.html");
        String javascript = read("static/js/practice/player-speaking.js");

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
