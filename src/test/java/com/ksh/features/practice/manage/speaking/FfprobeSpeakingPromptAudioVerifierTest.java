package com.ksh.features.practice.manage.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.manage.service.PracticeUploadContentVerifier;
import com.ksh.features.practice.service.audio.FfprobeProcessResult;
import com.ksh.features.practice.service.audio.FfprobeProcessRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfprobeSpeakingPromptAudioVerifierTest {

    @Test
    void corruptAuthoringAudioFailsClosed() {
        FfprobeSpeakingPromptAudioVerifier verifier = verifier(
                path -> new FfprobeProcessResult(
                        1, "", "private probe failure"),
                new SpeakingPromptAuthoringAiProperties());
        byte[] bytes = mp3Bytes();

        assertThatThrownBy(() -> verifier.verifySttInput(
                bytes,
                "prompt.mp3",
                "audio/mpeg",
                SpeakingPromptAiContract.exactBytesSha256(bytes)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("corrupt or unsupported");
    }

    @Test
    void invalidGeneratedAudioFailsClosedOnTheTtsVerifierPath() {
        FfprobeSpeakingPromptAudioVerifier verifier = verifier(
                path -> new FfprobeProcessResult(
                        1, "", "private probe failure"),
                new SpeakingPromptAuthoringAiProperties());

        assertThatThrownBy(() -> verifier.verifyTtsOutput(
                mp3Bytes(),
                "speaking-prompt-ai.mp3",
                "audio/mpeg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("corrupt or unsupported");
    }

    @Test
    void declaredMimeMismatchFailsBeforeProbe() {
        FfprobeSpeakingPromptAudioVerifier verifier = verifier(
                path -> {
                    throw new AssertionError(
                            "MIME mismatch must fail before ffprobe");
                },
                new SpeakingPromptAuthoringAiProperties());
        byte[] bytes = mp3Bytes();

        assertThatThrownBy(() -> verifier.verifySttInput(
                bytes,
                "prompt.mp3",
                "audio/ogg",
                SpeakingPromptAiContract.exactBytesSha256(bytes)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "MIME type does not match verified content");
    }

    @Test
    void oversizedAuthoringAudioFailsBeforeSignatureOrProbe() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        properties.getStt().setMaxInputBytes(7L);
        FfprobeSpeakingPromptAudioVerifier verifier = verifier(
                path -> {
                    throw new AssertionError(
                            "Oversized input must fail before ffprobe");
                },
                properties);
        byte[] bytes = mp3Bytes();

        assertThatThrownBy(() -> verifier.verifySttInput(
                bytes,
                "prompt.mp3",
                "audio/mpeg",
                SpeakingPromptAiContract.exactBytesSha256(bytes)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "byte length is outside the configured bounds");
    }

    @Test
    void overDurationAuthoringAudioFailsClosed() {
        SpeakingPromptAuthoringAiProperties properties =
                new SpeakingPromptAuthoringAiProperties();
        properties.getStt().setMaxInputDuration(Duration.ofSeconds(1));
        FfprobeSpeakingPromptAudioVerifier verifier = verifier(
                path -> new FfprobeProcessResult(
                        0,
                        """
                                {
                                  "streams": [{
                                    "codec_type": "audio",
                                    "codec_name": "mp3",
                                    "duration": "2.000"
                                  }],
                                  "format": {
                                    "format_name": "mp3",
                                    "duration": "2.000"
                                  }
                                }
                                """,
                        ""),
                properties);
        byte[] bytes = mp3Bytes();

        assertThatThrownBy(() -> verifier.verifySttInput(
                bytes,
                "prompt.mp3",
                "audio/mpeg",
                SpeakingPromptAiContract.exactBytesSha256(bytes)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "duration is outside the configured bounds");
    }

    private static FfprobeSpeakingPromptAudioVerifier verifier(
            FfprobeProcessRunner runner,
            SpeakingPromptAuthoringAiProperties properties) {
        return new FfprobeSpeakingPromptAudioVerifier(
                new ObjectMapper(),
                runner,
                new PracticeUploadContentVerifier(),
                properties);
    }

    private static byte[] mp3Bytes() {
        return new byte[] {'I', 'D', '3', 1, 2, 3, 4, 5};
    }
}
