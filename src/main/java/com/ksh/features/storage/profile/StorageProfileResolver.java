package com.ksh.features.storage.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class StorageProfileResolver {
    private static final Pattern BUCKET = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");
    private static final Pattern ACCOUNT = Pattern.compile("^[A-Za-z0-9_-]{3,128}$");

    private final StorageProfileRepository repository;
    private final boolean allowLocal;

    public StorageProfileResolver(
            StorageProfileRepository repository,
            @Value("${app.storage-profiles.allow-local:false}") boolean allowLocal) {
        this.repository = repository;
        this.allowLocal = allowLocal;
    }

    @Transactional(readOnly = true)
    public ResolvedStorageProfile resolveForWrite(StorageProfileCode code) {
        StorageProfile profile = load(code);
        if (!profile.isEnabled()) {
            throw new StorageProfileException("STORAGE_PROFILE_UNAVAILABLE");
        }
        return validate(profile);
    }

    @Transactional(readOnly = true)
    public ResolvedStorageProfile resolveForRead(StorageProfileCode code) {
        return validate(load(code));
    }

    public ResolvedStorageProfile validate(StorageProfile profile) {
        if (profile == null || profile.getProfileCode() == null
                || profile.getBackend() == null) {
            throw new StorageProfileException("STORAGE_PROFILE_UNAVAILABLE");
        }
        StorageProfileCode code = profile.getProfileCode();
        String prefix = requireFixedPrefix(code, profile.getKeyPrefix());
        if (profile.getBackend() == StorageBackend.LOCAL) {
            if (!allowLocal) {
                throw new StorageProfileException("STORAGE_PROFILE_UNAVAILABLE");
            }
            return snapshot(profile, prefix);
        }
        requireR2(profile);
        return snapshot(profile, prefix);
    }

    public static String requireFixedPrefix(StorageProfileCode code, String value) {
        if (code == null || value == null || !value.equals(code.fixedKeyPrefix())) {
            throw new StorageProfileException("STORAGE_IDENTITY_INVALID");
        }
        requireSafeRelativeKey(value);
        return value;
    }

    public static String requireSafeObjectKey(String value) {
        String key = requireSafeRelativeKey(value);
        if (key.length() > 512 || !key.equals(key.toLowerCase(Locale.ROOT))) {
            throw new StorageProfileException("STORAGE_IDENTITY_INVALID");
        }
        return key;
    }

    private StorageProfile load(StorageProfileCode code) {
        if (code == null) {
            throw new StorageProfileException("STORAGE_IDENTITY_INVALID");
        }
        return repository.findById(code)
                .orElseThrow(() -> new StorageProfileException("STORAGE_PROFILE_UNAVAILABLE"));
    }

    private static String requireSafeRelativeKey(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.startsWith("/") || value.startsWith("\\")
                || value.contains("\\") || value.contains("//") || value.contains(":")
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new StorageProfileException("STORAGE_IDENTITY_INVALID");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new StorageProfileException("STORAGE_IDENTITY_INVALID");
            }
        }
        return value;
    }

    private static void requireR2(StorageProfile profile) {
        if (!present(profile.getAccountId()) || !ACCOUNT.matcher(profile.getAccountId()).matches()
                || !present(profile.getAccessKeyId())
                || !present(profile.getSecretAccessKey())
                || !present(profile.getBucket()) || !BUCKET.matcher(profile.getBucket()).matches()
                || !present(profile.getEndpoint()) || !httpsEndpoint(profile.getEndpoint())
                || !present(profile.getRegion())) {
            throw new StorageProfileException("STORAGE_PROFILE_UNAVAILABLE");
        }
    }

    private static boolean httpsEndpoint(String value) {
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }

    private static ResolvedStorageProfile snapshot(StorageProfile profile, String prefix) {
        return new ResolvedStorageProfile(
                profile.getProfileCode(), profile.getBackend(), profile.getAccountId(),
                profile.getAccessKeyId(), profile.getSecretAccessKey(), profile.getBucket(),
                profile.getEndpoint(), profile.getRegion(), prefix, profile.getRevision());
    }
}
