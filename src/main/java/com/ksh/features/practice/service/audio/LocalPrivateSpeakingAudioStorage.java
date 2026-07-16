package com.ksh.features.practice.service.audio;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalPrivateSpeakingAudioStorage implements SpeakingAudioStorage {
    private static final String TEMPORARY_PREFIX = "learner-speaking/temporary/";
    private static final String READY_PREFIX = "learner-speaking/ready/";
    private static final int BUFFER_SIZE = 8192;

    private final Path privateRoot;
    private final Path publicRoot;
    private final long maxAudioBytes;

    public LocalPrivateSpeakingAudioStorage(SpeakingAudioProperties properties) {
        this.privateRoot = properties.getPrivateLocalRoot().toAbsolutePath().normalize();
        this.publicRoot = properties.getPublicUploadRoot().toAbsolutePath().normalize();
        this.maxAudioBytes = properties.getMaxAudioBytes();
        rejectPublicRootOverlap();
    }

    @Override
    public StoredSpeakingAudioObject writeTemporary(InputStream content, Long declaredContentLength) {
        if (content == null) {
            throw validation(SpeakingAudioValidationCategory.EMPTY, "Audio stream is required");
        }
        if (declaredContentLength != null) {
            if (declaredContentLength == 0L) {
                throw validation(SpeakingAudioValidationCategory.EMPTY, "Audio file is empty");
            }
            if (declaredContentLength > maxAudioBytes) {
                throw validation(SpeakingAudioValidationCategory.TOO_LARGE, "Audio file is too large");
            }
        }

        String key = TEMPORARY_PREFIX + UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
        Path target = resolveObjectPath(key);
        boolean completed = false;
        try {
            Files.createDirectories(target.getParent());
            verifyResolvedParent(target.getParent());

            MessageDigest digest = sha256Digest();
            long count = 0L;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream in = content;
                 OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    count += read;
                    if (count > maxAudioBytes) {
                        throw validation(SpeakingAudioValidationCategory.TOO_LARGE, "Audio file is too large");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (count == 0L) {
                throw validation(SpeakingAudioValidationCategory.EMPTY, "Audio file is empty");
            }
            completed = true;
            return new StoredSpeakingAudioObject(key, count, HexFormat.of().formatHex(digest.digest()), target);
        } catch (SpeakingAudioValidationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage operation failed", ex);
        } finally {
            if (!completed) {
                deleteFileIfPresent(target);
            }
        }
    }

    @Override
    public String promoteTemporary(String temporaryKey) {
        validateKey(temporaryKey);
        if (!temporaryKey.startsWith(TEMPORARY_PREFIX)) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Temporary audio object is invalid");
        }
        String finalKey = READY_PREFIX + UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
        Path source = resolveObjectPath(temporaryKey);
        Path target = resolveObjectPath(finalKey);
        try {
            rejectSymbolicLink(source);
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Temporary audio object is unavailable");
            }
            Files.createDirectories(target.getParent());
            verifyResolvedParent(target.getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage object already exists");
            }
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(source, target);
            }
            return finalKey;
        } catch (SpeakingAudioValidationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage operation failed", ex);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path path = resolveObjectPath(storageKey);
        try {
            rejectSymbolicLink(path);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio object is unavailable");
            }
            return Files.newInputStream(path, StandardOpenOption.READ);
        } catch (SpeakingAudioValidationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage operation failed", ex);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        Path path = resolveObjectPath(storageKey);
        rejectSymbolicLink(path);
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void delete(String storageKey) {
        Path path = resolveObjectPath(storageKey);
        try {
            rejectSymbolicLink(path);
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage object is invalid");
            }
            Files.deleteIfExists(path);
        } catch (SpeakingAudioValidationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage operation failed", ex);
        }
    }

    Path resolveObjectPathForTest(String storageKey) {
        return resolveObjectPath(storageKey);
    }

    private void rejectPublicRootOverlap() {
        if (privateRoot.equals(publicRoot) || privateRoot.startsWith(publicRoot) || publicRoot.startsWith(privateRoot)) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Private audio root is not isolated");
        }
    }

    private Path resolveObjectPath(String storageKey) {
        validateKey(storageKey);
        Path resolved = privateRoot.resolve(storageKey.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(privateRoot) || resolved.equals(privateRoot)) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage key is invalid");
        }
        return resolved;
    }

    private void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage object is invalid");
        }
    }

    private void validateKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage key is invalid");
        }
        String key = storageKey.trim();
        if (!key.equals(storageKey) || !key.equals(key.toLowerCase(Locale.ROOT))) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage key is invalid");
        }
        if (key.startsWith("/") || key.startsWith("\\") || key.contains("\\") || key.contains("..")) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage key is invalid");
        }
        if (key.length() >= 2 && Character.isLetter(key.charAt(0)) && key.charAt(1) == ':') {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage key is invalid");
        }
        for (int i = 0; i < key.length(); i++) {
            if (Character.isISOControl(key.charAt(i))) {
                throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage key is invalid");
            }
        }
        for (String segment : key.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage key is invalid");
            }
        }
    }

    private void verifyResolvedParent(Path parent) throws IOException {
        Path realRoot = privateRoot.toRealPath();
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(realRoot)) {
            throw validation(SpeakingAudioValidationCategory.STORAGE_FAILURE, "Audio storage path is invalid");
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private static SpeakingAudioValidationException validation(SpeakingAudioValidationCategory category, String message) {
        return new SpeakingAudioValidationException(category, message);
    }

    private static SpeakingAudioValidationException validation(SpeakingAudioValidationCategory category, String message, Throwable cause) {
        return new SpeakingAudioValidationException(category, message, cause);
    }

    private static void deleteFileIfPresent(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup for a temporary object that has no DB row.
        }
    }
}
