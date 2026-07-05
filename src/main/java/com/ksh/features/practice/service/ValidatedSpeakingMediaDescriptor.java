package com.ksh.features.practice.service;

import com.ksh.entities.PracticeSpeakingStorageProvider;

import java.util.Locale;

public record ValidatedSpeakingMediaDescriptor(
        PracticeSpeakingStorageProvider storageProvider,
        String storageKey,
        String mimeType,
        String container,
        String codec,
        long byteSize,
        long durationMs,
        String contentHash
) {
    private static final int MAX_STORAGE_KEY_LENGTH = 512;
    private static final int MAX_MIME_TYPE_LENGTH = 128;
    private static final int MAX_CONTAINER_LENGTH = 32;
    private static final int MAX_CODEC_LENGTH = 64;
    private static final String SHA256_HEX = "^[0-9a-f]{64}$";

    public ValidatedSpeakingMediaDescriptor {
        if (storageProvider == null) {
            throw new IllegalArgumentException("storageProvider is required.");
        }
        storageKey = requireBoundedText(storageKey, "storageKey", MAX_STORAGE_KEY_LENGTH).toLowerCase(Locale.ROOT);
        validateStorageKey(storageKey);
        mimeType = requireBoundedText(mimeType, "mimeType", MAX_MIME_TYPE_LENGTH);
        container = requireBoundedText(container, "container", MAX_CONTAINER_LENGTH);
        codec = requireBoundedText(codec, "codec", MAX_CODEC_LENGTH);
        if (byteSize <= 0) {
            throw new IllegalArgumentException("byteSize must be positive.");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive.");
        }
        contentHash = requireBoundedText(contentHash, "contentHash", 64);
        if (!contentHash.matches(SHA256_HEX)) {
            throw new IllegalArgumentException("contentHash must be lowercase SHA-256 hex.");
        }
    }

    private static String requireBoundedText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        if (trimmed.chars().anyMatch(ch -> Character.isISOControl(ch))) {
            throw new IllegalArgumentException(field + " contains control characters.");
        }
        return trimmed;
    }

    private static void validateStorageKey(String storageKey) {
        String normalized = storageKey.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("//") || normalized.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("storageKey must be relative.");
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("storageKey contains path traversal.");
            }
        }
    }
}
