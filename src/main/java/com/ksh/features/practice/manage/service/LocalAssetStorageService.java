package com.ksh.features.practice.manage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class LocalAssetStorageService implements AssetStorageService {

    private final Path uploadRoot;

    public LocalAssetStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String providerCode() {
        return "LOCAL";
    }

    @Override
    public StoredAsset store(InputStream content, String filename, String relativePath) throws IOException {
        String cleanPath = relativePath.replace("\\", "/");
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }

        Path targetDir = uploadRoot.resolve(cleanPath).normalize();
        if (!targetDir.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Đường dẫn lưu trữ không hợp lệ.");
        }
        Files.createDirectories(targetDir);

        // Copy to a temp file first to calculate SHA-256 and get exact size
        Path tempFile = Files.createTempFile("upload-", ".tmp");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        try {
            long sizeBytes;
            try (DigestInputStream dis = new DigestInputStream(content, digest)) {
                sizeBytes = Files.copy(dis, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String sha256 = sb.toString();
            String storedFilename = sha256 + safeExtension(filename);
            Path targetFile = targetDir.resolve(storedFilename).normalize();
            if (!targetFile.startsWith(targetDir)) {
                throw new IllegalArgumentException("Tên tệp lưu trữ không hợp lệ.");
            }

            boolean newlyCreated;
            try {
                Files.move(tempFile, targetFile);
                newlyCreated = true;
            } catch (FileAlreadyExistsException duplicate) {
                // The key is content-addressed, so the existing object has the same bytes.
                newlyCreated = false;
            }

            String storageKey = cleanPath + "/" + storedFilename;
            return new StoredAsset(storageKey, sizeBytes, sha256, newlyCreated);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public Resource load(String storageKey) throws IOException {
        Path file = uploadRoot.resolve(storageKey).normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file)) {
            throw new java.io.FileNotFoundException("Không tìm thấy asset: " + storageKey);
        }
        return new FileSystemResource(file);
    }

    @Override
    public boolean exists(String storageKey) {
        Path file = uploadRoot.resolve(storageKey).normalize();
        return file.startsWith(uploadRoot) && Files.exists(file);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path file = uploadRoot.resolve(storageKey).normalize();
        if (file.startsWith(uploadRoot) && Files.exists(file)) {
            Files.delete(file);
        }
    }

    @Override
    public AssetMetadata inspect(String storageKey) throws IOException {
        Path file = uploadRoot.resolve(storageKey).normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file)) {
            throw new java.io.FileNotFoundException("Không tìm thấy asset: " + storageKey);
        }
        try (InputStream in = Files.newInputStream(file)) {
            BufferedImage img = ImageIO.read(in);
            if (img != null) {
                return new AssetMetadata(img.getWidth(), img.getHeight());
            }
        }
        return new AssetMetadata(0, 0);
    }

    private static String safeExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".bin";
        String extension = filename.substring(filename.lastIndexOf('.'));
        return extension.matches("\\.[A-Za-z0-9]{1,10}")
                ? extension.toLowerCase(java.util.Locale.ROOT)
                : ".bin";
    }
}
