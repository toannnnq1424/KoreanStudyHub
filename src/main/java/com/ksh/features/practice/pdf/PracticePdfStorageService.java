package com.ksh.features.practice.pdf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class PracticePdfStorageService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final Path uploadRoot;

    public PracticePdfStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir, "practice-pdfs").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create practice PDF upload root: " + uploadRoot, ex);
        }
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

        Path userDir = uploadRoot.resolve(String.valueOf(uploaderId)).normalize();
        if (!userDir.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Đường dẫn lưu file không hợp lệ.");
        }
        Files.createDirectories(userDir);

        String filename = UUID.randomUUID() + ".pdf";
        Path destination = userDir.resolve(filename).normalize();
        file.transferTo(destination.toFile());

        return new StoredPdf(
                "practice-pdfs/" + uploaderId + "/" + filename,
                destination,
                originalFilename,
                file.getSize()
        );
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
                            String originalFilename, long sizeBytes) {
    }
}
