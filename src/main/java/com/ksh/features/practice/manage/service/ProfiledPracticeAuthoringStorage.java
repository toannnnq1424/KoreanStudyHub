package com.ksh.features.practice.manage.service;

import com.ksh.features.storage.StoredObject;
import com.ksh.features.storage.StoredObjectResource;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileObjectStore;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
@Primary
public class ProfiledPracticeAuthoringStorage implements AssetStorageService {
    public static final StorageProfileCode PROFILE = StorageProfileCode.PRACTICE_AUTHORING;

    private final StorageProfileObjectStore profileStore;

    public ProfiledPracticeAuthoringStorage(StorageProfileObjectStore profileStore) {
        this.profileStore = profileStore;
    }

    @Override
    public String providerCode() {
        return profileStore.backendCodeForWrite(PROFILE).name();
    }

    @Override
    public String profileCode() {
        return PROFILE.name();
    }

    @Override
    public StoredAsset store(InputStream content, String filename, String relativePath)
            throws IOException {
        String namespace = requireNamespace(relativePath);
        Path temporary = Files.createTempFile("ksh-practice-authoring-", ".bin");
        try {
            MessageDigest digest = sha256();
            long size;
            try (InputStream input = content;
                 DigestInputStream hashing = new DigestInputStream(input, digest);
                 OutputStream output = Files.newOutputStream(temporary)) {
                size = hashing.transferTo(output);
            }
            if (size <= 0L) throw new IOException("Authoring object is empty");
            String hash = HexFormat.of().formatHex(digest.digest());
            String key = namespace + "/" + hash + safeExtension(filename);
            boolean newlyCreated = !profileStore.exists(PROFILE, key);
            try (InputStream input = Files.newInputStream(temporary)) {
                StorageBackend backend = profileStore.put(
                        PROFILE, key, input, "application/octet-stream", size);
                return new StoredAsset(key, size, hash, newlyCreated,
                        PROFILE.name(), backend.name());
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public Resource load(String storageProfileCode, String storageKey) throws IOException {
        requireExactProfile(storageProfileCode);
        return new StoredObjectResource(profileStore.open(PROFILE, storageKey),
                "private Practice authoring object");
    }

    @Override
    public boolean exists(String storageProfileCode, String storageKey) {
        requireExactProfile(storageProfileCode);
        return profileStore.exists(PROFILE, storageKey);
    }

    @Override
    public void delete(String storageProfileCode, String storageKey) throws IOException {
        requireExactProfile(storageProfileCode);
        profileStore.delete(PROFILE, storageKey);
    }

    @Override
    public AssetMetadata inspect(String storageProfileCode, String storageKey)
            throws IOException {
        try (InputStream input = load(storageProfileCode, storageKey).getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            return image == null ? new AssetMetadata(0, 0)
                    : new AssetMetadata(image.getWidth(), image.getHeight());
        }
    }

    private static void requireExactProfile(String value) {
        if (!PROFILE.name().equals(value)) {
            throw new IllegalArgumentException("STORAGE_IDENTITY_INVALID");
        }
    }

    private static String requireNamespace(String value) {
        String namespace = com.ksh.features.storage.profile.StorageProfileResolver
                .requireSafeObjectKey(value == null ? "" : value.replaceAll("/+$", ""));
        if (!namespace.startsWith("lecturer-assets/")) {
            throw new IllegalArgumentException("STORAGE_IDENTITY_INVALID");
        }
        return namespace;
    }

    private static String safeExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".bin";
        String extension = filename.substring(filename.lastIndexOf('.'));
        return extension.matches("\\.[A-Za-z0-9]{1,10}")
                ? extension.toLowerCase(Locale.ROOT) : ".bin";
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
