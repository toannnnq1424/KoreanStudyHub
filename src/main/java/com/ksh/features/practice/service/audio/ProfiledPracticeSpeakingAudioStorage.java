package com.ksh.features.practice.service.audio;

import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.storage.StoredObject;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileObjectStore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@Primary
public class ProfiledPracticeSpeakingAudioStorage implements SpeakingAudioStorage {
    private static final StorageProfileCode PROFILE = StorageProfileCode.PRACTICE_SPEAKING;
    private static final String TEMPORARY_PREFIX = "learner-speaking/temporary/";
    private static final String READY_PREFIX = "learner-speaking/ready/";

    private final StorageProfileObjectStore profiles;
    private final long maxAudioBytes;

    public ProfiledPracticeSpeakingAudioStorage(StorageProfileObjectStore profiles,
                                                SpeakingAudioProperties properties) {
        this.profiles = profiles;
        this.maxAudioBytes = properties.getMaxAudioBytes();
    }

    @Override
    public StoredSpeakingAudioObject writeTemporary(InputStream content,
                                                    Long declaredContentLength) {
        if (content == null || (declaredContentLength != null && declaredContentLength <= 0L)) {
            throw validation(SpeakingAudioValidationCategory.EMPTY);
        }
        if (declaredContentLength != null && declaredContentLength > maxAudioBytes) {
            throw validation(SpeakingAudioValidationCategory.TOO_LARGE);
        }
        Path inspection = null;
        try {
            inspection = Files.createTempFile("ksh-speaking-inspection-", ".bin");
            MessageDigest digest = sha256();
            long size = 0L;
            byte[] buffer = new byte[8192];
            try (InputStream input = content;
                 OutputStream output = Files.newOutputStream(inspection)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > maxAudioBytes) {
                        throw validation(SpeakingAudioValidationCategory.TOO_LARGE);
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (size == 0L) throw validation(SpeakingAudioValidationCategory.EMPTY);
            String key = TEMPORARY_PREFIX + UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
            StorageBackend backend;
            try (InputStream input = Files.newInputStream(inspection)) {
                backend = profiles.put(PROFILE, key, input,
                        "application/octet-stream", size);
            }
            return new StoredSpeakingAudioObject(
                    key, size, HexFormat.of().formatHex(digest.digest()), inspection,
                    PROFILE.name(), provider(backend));
        } catch (SpeakingAudioValidationException exception) {
            deleteInspection(inspection);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            deleteInspection(inspection);
            throw new SpeakingAudioValidationException(
                    SpeakingAudioValidationCategory.STORAGE_FAILURE,
                    "Audio storage operation failed", exception);
        }
    }

    @Override
    public String promoteTemporary(String storageProfileCode, String temporaryKey) {
        requireExact(storageProfileCode);
        String temporary = requireKey(temporaryKey, TEMPORARY_PREFIX);
        String ready = READY_PREFIX + UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
        try {
            profiles.copy(PROFILE, temporary, ready);
            if (!profiles.exists(PROFILE, ready)) {
                throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE);
            }
            return ready;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof SpeakingAudioValidationException validation) throw validation;
            throw new SpeakingAudioValidationException(
                    SpeakingAudioValidationCategory.STORAGE_FAILURE,
                    "Audio promotion failed", exception);
        }
    }

    @Override
    public InputStream open(String storageProfileCode, String storageKey) {
        requireExact(storageProfileCode);
        try {
            StoredObject object = profiles.open(PROFILE, requireKey(storageKey, "learner-speaking/"));
            return object.inputStream();
        } catch (IOException | RuntimeException exception) {
            throw new SpeakingAudioValidationException(
                    SpeakingAudioValidationCategory.STORAGE_FAILURE,
                    "Audio object is unavailable", exception);
        }
    }

    @Override
    public boolean exists(String storageProfileCode, String storageKey) {
        requireExact(storageProfileCode);
        return profiles.exists(PROFILE, requireKey(storageKey, "learner-speaking/"));
    }

    @Override
    public void delete(String storageProfileCode, String storageKey) {
        requireExact(storageProfileCode);
        try {
            profiles.delete(PROFILE, requireKey(storageKey, "learner-speaking/"));
        } catch (IOException | RuntimeException exception) {
            throw new SpeakingAudioValidationException(
                    SpeakingAudioValidationCategory.STORAGE_FAILURE,
                    "Audio delete was not confirmed", exception);
        }
    }

    private static PracticeSpeakingStorageProvider provider(StorageBackend backend) {
        return backend == StorageBackend.LOCAL
                ? PracticeSpeakingStorageProvider.LOCAL
                : PracticeSpeakingStorageProvider.OBJECT_STORAGE;
    }

    private static void requireExact(String value) {
        if (!PROFILE.name().equals(value)) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE);
        }
    }

    private static String requireKey(String value, String requiredPrefix) {
        String key;
        try {
            key = com.ksh.features.storage.profile.StorageProfileResolver
                    .requireSafeObjectKey(value);
        } catch (RuntimeException exception) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE);
        }
        if (!key.startsWith(requiredPrefix)) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE);
        }
        return key;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static SpeakingAudioValidationException validation(
            SpeakingAudioValidationCategory category) {
        return new SpeakingAudioValidationException(category,
                "Audio storage identity is invalid");
    }

    private static void deleteInspection(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
