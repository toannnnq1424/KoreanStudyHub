package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PracticeUploadContentVerifierTest {

    private final PracticeUploadContentVerifier verifier = new PracticeUploadContentVerifier();

    @Test
    void acceptsPngByContentSignature() {
        byte[] png = bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00);

        PracticeUploadContentVerifier.VerifiedContent result =
                verifier.verify(png, "question.PNG", "IMAGE");

        assertEquals("image/png", result.mimeType());
        assertEquals(".png", result.extension());
    }

    @Test
    void rejectsExecutableRenamedAsImage() {
        byte[] executable = "MZ-not-an-image".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(executable, "question.png", "IMAGE"));
    }

    @Test
    void rejectsImageWhenAudioWasRequested() {
        byte[] jpeg = bytes(0xff, 0xd8, 0xff, 0xe0, 0x00);

        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(jpeg, "audio.jpg", "AUDIO"));
    }

    @Test
    void acceptsId3Mp3AsAudio() {
        byte[] mp3 = "ID3\u0004\u0000\u0000sample".getBytes(
                java.nio.charset.StandardCharsets.ISO_8859_1);

        assertEquals("audio/mpeg",
                verifier.verify(mp3, "listen.mp3", "AUDIO").mimeType());
    }

    @Test
    void rejectsUnsupportedExtensionEvenWithKnownSignature() {
        byte[] png = bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);

        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(png, "question.svg", "IMAGE"));
    }

    @Test
    void rejectsTruncatedContent() {
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(new byte[]{1, 2, 3}, "listen.mp3", "AUDIO"));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
