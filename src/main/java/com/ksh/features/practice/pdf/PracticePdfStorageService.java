package com.ksh.features.practice.pdf;

import com.ksh.features.practice.manage.service.AssetStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class PracticePdfStorageService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final AssetStorageService storage;
    private final Path legacyRoot;

    public PracticePdfStorageService(
            AssetStorageService storage,
            @Value("${app.upload.dir:${user.home}/.ksh/uploads}") String uploadDir) {
        this.storage = storage;
        this.legacyRoot = Paths.get(uploadDir, "practice-pdfs").toAbsolutePath().normalize();
    }

    public StoredPdf store(MultipartFile file, Long uploaderId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file PDF.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("File PDF không được vượt quá 20MB.");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()
                || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file PDF.");
        }
        if (!hasPdfHeader(file)) {
            throw new IllegalArgumentException("File tải lên không phải PDF hợp lệ.");
        }

        String namespace = "practice-pdfs/" + uploaderId + "/temporary/objects/"
                + UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
        AssetStorageService.StoredAsset stored;
        try (var input = file.getInputStream()) {
            stored = storage.store(input, originalFilename, namespace);
        }
        return new StoredPdf(stored.storageKey(), null, originalFilename,
                stored.sizeBytes(), stored.storageProfileCode());
    }

    public InputStream open(String storageProfileCode, String storedPath) throws IOException {
        if (storageProfileCode != null) {
            return storage.load(storageProfileCode, storedPath).getInputStream();
        }
        Path path = Paths.get(storedPath == null ? "" : storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(legacyRoot) || !java.nio.file.Files.isRegularFile(path)) {
            throw new java.io.FileNotFoundException("Không tìm thấy PDF legacy.");
        }
        return java.nio.file.Files.newInputStream(path);
    }

    public byte[] readBytes(String storageProfileCode, String storedPath) throws IOException {
        try (InputStream input = open(storageProfileCode, storedPath)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(MAX_SIZE + 1));
            if (bytes.length == 0 || bytes.length > MAX_SIZE) {
                throw new IOException("PDF bytes exceed the bounded storage contract");
            }
            return bytes;
        }
    }

    public void delete(String storageProfileCode, String storedPath) throws IOException {
        if (storageProfileCode != null) {
            storage.delete(storageProfileCode, storedPath);
            return;
        }
        Path path = Paths.get(storedPath == null ? "" : storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(legacyRoot)) {
            throw new IllegalArgumentException("STORAGE_IDENTITY_INVALID");
        }
        java.nio.file.Files.deleteIfExists(path);
    }

    public boolean existsLegacy(String storedPath) {
        try {
            Path path = Paths.get(storedPath == null ? "" : storedPath)
                    .toAbsolutePath().normalize();
            return path.startsWith(legacyRoot) && java.nio.file.Files.isRegularFile(path);
        } catch (RuntimeException invalidPath) {
            return false;
        }
    }

    private static boolean hasPdfHeader(MultipartFile file) throws IOException {
        byte[] header;
        try (var input = file.getInputStream()) {
            header = input.readNBytes(5);
        }
        return header.length == 5
                && header[0] == (byte) 0x25
                && header[1] == (byte) 0x50
                && header[2] == (byte) 0x44
                && header[3] == (byte) 0x46
                && header[4] == (byte) 0x2D;
    }

    public record StoredPdf(String storedPath, Path absolutePath,
                            String originalFilename, long sizeBytes,
                            String storageProfileCode) {
        public StoredPdf(String storedPath, Path absolutePath,
                         String originalFilename, long sizeBytes) {
            this(storedPath, absolutePath, originalFilename, sizeBytes, null);
        }
    }
}
