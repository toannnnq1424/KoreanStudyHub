package com.ksh.features.practice.manage.service;

import com.ksh.features.storage.profile.StorageProfileResolver;
import com.ksh.features.storage.profile.StorageProfileException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stores locally prepared Practice seed assets behind the canonical authoring
 * storage port. Callers persist the returned logical key, never a filesystem
 * path, bucket name, or delivery URL.
 */
@Service
public class PracticeSeedAssetStorage {
    private static final Pattern BUNDLE_ID = Pattern.compile(
            "^[a-z0-9][a-z0-9-]{2,63}$");
    private static final Pattern MEDIA_TYPE = Pattern.compile(
            "^[a-z0-9][a-z0-9.+-]{0,63}/[a-z0-9][a-z0-9.+-]{0,127}$");
    private static final Set<String> ALLOWED_PROFILE_CODES =
            Set.of("PRACTICE_AUTHORING");

    private final AssetStorageService storage;

    public PracticeSeedAssetStorage(AssetStorageService storage) {
        this.storage = storage;
    }

    public StoredSeedAsset store(String bundleId,
                                 AssetKind kind,
                                 InputStream content,
                                 String originalFilename,
                                 String mediaType) throws IOException {
        String canonicalBundleId = requireBundleId(bundleId);
        if (kind == null || content == null) {
            throw new IllegalArgumentException("PRACTICE_SEED_ASSET_INVALID");
        }
        String canonicalMediaType = requireMediaType(mediaType);
        String namespace = "practice-seed/" + canonicalBundleId + "/" + kind.path();
        AssetStorageService.StoredAsset stored = storage.store(
                content, originalFilename, namespace);
        return requireCanonicalResult(namespace, canonicalMediaType, stored);
    }

    private static String requireBundleId(String value) {
        if (value == null || !BUNDLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("PRACTICE_SEED_ASSET_INVALID");
        }
        return value;
    }

    private static String requireMediaType(String value) {
        if (value == null) {
            throw new IllegalArgumentException("PRACTICE_SEED_ASSET_INVALID");
        }
        String canonical = value.trim().toLowerCase(Locale.ROOT);
        if (!value.equals(canonical) || !MEDIA_TYPE.matcher(canonical).matches()) {
            throw new IllegalArgumentException("PRACTICE_SEED_ASSET_INVALID");
        }
        return canonical;
    }

    private static StoredSeedAsset requireCanonicalResult(
            String namespace,
            String mediaType,
            AssetStorageService.StoredAsset stored) {
        if (stored == null
                || !ALLOWED_PROFILE_CODES.contains(stored.storageProfileCode())
                || stored.sha256() == null
                || !stored.sha256().matches("[0-9a-f]{64}")
                || stored.sizeBytes() <= 0L) {
            throw new IllegalArgumentException("PRACTICE_SEED_STORAGE_RESULT_INVALID");
        }
        String key;
        try {
            key = StorageProfileResolver.requireSafeObjectKey(stored.storageKey());
        } catch (StorageProfileException exception) {
            throw new IllegalArgumentException(
                    "PRACTICE_SEED_STORAGE_RESULT_INVALID", exception);
        }
        String expectedPrefix = namespace + "/" + stored.sha256();
        if (!key.matches(Pattern.quote(expectedPrefix) + "\\.[a-z0-9]{1,10}")) {
            throw new IllegalArgumentException("PRACTICE_SEED_STORAGE_RESULT_INVALID");
        }
        return new StoredSeedAsset(key, mediaType, stored.sizeBytes(),
                stored.sha256(), stored.newlyCreated(), stored.storageProfileCode(),
                stored.storageProvider());
    }

    public enum AssetKind {
        SOURCE_DOCUMENT("source/document"),
        SOURCE_AUDIO("source/audio"),
        SOURCE_IMAGE("source/image"),
        DERIVED_AUDIO_MP3("derived/audio-mp3"),
        DERIVED_PAGE_IMAGE("derived/page-image"),
        DERIVED_TRANSCRIPT("derived/transcript"),
        REVIEW_ARTIFACT("review/artifact");

        private final String path;

        AssetKind(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    public record StoredSeedAsset(String logicalKey,
                                  String mediaType,
                                  long sizeBytes,
                                  String sha256,
                                  boolean newlyCreated,
                                  String storageProfileCode,
                                  String storageProvider) {
    }
}
