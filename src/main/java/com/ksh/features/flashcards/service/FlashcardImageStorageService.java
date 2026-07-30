package com.ksh.features.flashcards.service;

import com.ksh.features.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/** Stores optional card-side images in the configured object storage. */
@Service
public class FlashcardImageStorageService {
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final ObjectStorage storage;

    public FlashcardImageStorageService(@Qualifier("objectStorage") ObjectStorage storage) {
        this.storage = storage;
    }

    public String store(Long cardId, String side, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Ảnh trống hoặc vượt quá 5 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !TYPES.contains(contentType) || !validMagic(file)) {
            throw new IllegalArgumentException("Chỉ nhận ảnh JPEG, PNG hoặc WebP hợp lệ");
        }
        String ext = "image/png".equals(contentType) ? "png" : ("image/webp".equals(contentType) ? "webp" : "jpg");
        String key = "flashcards/" + cardId + "-" + side + "-" + UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) { storage.put(key, in, contentType, file.getSize()); }
        return "/uploads/" + key;
    }

    private boolean validMagic(MultipartFile file) throws IOException {
        byte[] h;
        try (InputStream in = file.getInputStream()) { h = in.readNBytes(12); }
        if (h.length >= 4 && (h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return true;
        if (h.length >= 3 && (h[0] & 0xff) == 0xff && (h[1] & 0xff) == 0xd8 && (h[2] & 0xff) == 0xff) return true;
        return h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }
}
