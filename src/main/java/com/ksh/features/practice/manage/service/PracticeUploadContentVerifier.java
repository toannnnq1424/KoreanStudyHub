package com.ksh.features.practice.manage.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Component
public class PracticeUploadContentVerifier {

    private static final Map<String, String> EXTENSION_TYPES = Map.ofEntries(
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".mp3", "audio/mpeg"),
            Map.entry(".wav", "audio/wav"),
            Map.entry(".m4a", "audio/mp4"),
            Map.entry(".ogg", "audio/ogg"),
            Map.entry(".webm", "audio/webm")
    );

    public VerifiedContent verify(byte[] bytes, String originalFilename,
                                  String expectedAssetType) {
        if (bytes == null || bytes.length < 4) {
            throw new IllegalArgumentException("Tệp tải lên không có nội dung hợp lệ.");
        }
        String extension = extension(originalFilename);
        String expectedMime = EXTENSION_TYPES.get(extension);
        if (expectedMime == null) {
            throw new IllegalArgumentException("Định dạng tệp không được hỗ trợ.");
        }
        String detected = detect(bytes);
        if (detected == null || !compatible(expectedMime, detected)) {
            throw new IllegalArgumentException(
                    "Nội dung tệp không khớp phần mở rộng đã chọn.");
        }
        boolean expectedImage = "IMAGE".equalsIgnoreCase(expectedAssetType);
        if (expectedImage != detected.startsWith("image/")) {
            throw new IllegalArgumentException("Tệp không đúng loại tài nguyên đã chọn.");
        }
        return new VerifiedContent(extension, detected);
    }

    private static String detect(byte[] bytes) {
        if (starts(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (starts(bytes, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a")) return "image/gif";
        if (ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "image/webp";
        if (ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WAVE")) return "audio/wav";
        if (ascii(bytes, 0, "OggS")) return "audio/ogg";
        if (starts(bytes, 0x1A, 0x45, 0xDF, 0xA3)) return "audio/webm";
        if (ascii(bytes, 0, "ID3")
                || ((bytes[0] & 0xFF) == 0xFF && ((bytes[1] & 0xE0) == 0xE0))) {
            return "audio/mpeg";
        }
        if (bytes.length >= 12 && ascii(bytes, 4, "ftyp")) return "audio/mp4";
        return null;
    }

    private static boolean compatible(String expected, String detected) {
        return expected.equals(detected)
                || ("image/jpeg".equals(expected) && "image/jpeg".equals(detected));
    }

    private static String extension(String filename) {
        if (filename == null || filename.isBlank()) return "";
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index).toLowerCase(Locale.ROOT);
    }

    private static boolean starts(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xFF) != signature[index]) return false;
        }
        return true;
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        byte[] expected = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) return false;
        }
        return true;
    }

    public record VerifiedContent(String extension, String mimeType) {
    }
}
