package com.ksh.features.admin.settings.service;

import com.ksh.features.admin.settings.dto.StorageProfileDtos.ProfileForm;
import com.ksh.features.admin.settings.dto.StorageProfileDtos.ProfileRow;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfile;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileR2Clients;
import com.ksh.features.storage.profile.StorageProfileRepository;
import com.ksh.features.storage.profile.StorageProfileResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.ksh.features.admin.settings.dto.StorageProfileDtos.MASKED;

@Service
public class StorageProfileAdminService {
    private static final Logger log = LoggerFactory.getLogger(StorageProfileAdminService.class);

    private final StorageProfileRepository repository;
    private final StorageProfileResolver resolver;
    private final StorageProfileR2Clients r2Clients;
    private final JdbcTemplate jdbcTemplate;

    public StorageProfileAdminService(StorageProfileRepository repository,
                                      StorageProfileResolver resolver,
                                      StorageProfileR2Clients r2Clients,
                                      JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.resolver = resolver;
        this.r2Clients = r2Clients;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<ProfileRow> profiles() {
        return repository.findAllOrdered().stream().map(StorageProfileAdminService::row).toList();
    }

    @Transactional(readOnly = true)
    public List<StorageProfileCode> missingCodes() {
        var configured = repository.findAll().stream()
                .map(StorageProfile::getProfileCode)
                .collect(java.util.stream.Collectors.toSet());
        return Arrays.stream(StorageProfileCode.values())
                .filter(code -> !configured.contains(code))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProfileForm> form(StorageProfileCode code) {
        return repository.findById(code).map(profile -> new ProfileForm(
                profile.getProfileCode(), profile.getBackend(), value(profile.getAccountId()),
                value(profile.getAccessKeyId()), MASKED, value(profile.getBucket()),
                value(profile.getEndpoint()), value(profile.getRegion()), profile.getKeyPrefix(),
                profile.isEnabled(), profile.getRevision()));
    }

    @Transactional
    public StorageProfileCode save(ProfileForm form, Long actorId) {
        StorageProfileCode code = java.util.Objects.requireNonNull(form.profileCode(), "profileCode");
        StorageBackend backend = java.util.Objects.requireNonNull(form.backend(), "backend");
        String prefix = StorageProfileResolver.requireFixedPrefix(code, form.keyPrefix());
        String replacementSecret = newSecret(form.secretAccessKey())
                ? form.secretAccessKey().trim() : null;
        StorageProfile profile;
        if (form.revision() == null) {
            if (repository.existsById(code)) {
                throw new IllegalStateException("STORAGE_PROFILE_REVISION_CONFLICT");
            }
            profile = new StorageProfile(code, backend, trim(form.accountId()),
                    trim(form.accessKeyId()), replacementSecret, trim(form.bucket()),
                    trim(form.endpoint()), region(form.region()), form.enabled(), actorId);
        } else {
            profile = repository.findByCodeForUpdate(code)
                    .orElseThrow(() -> new IllegalArgumentException("STORAGE_PROFILE_NOT_FOUND"));
            if (profile.getRevision() != form.revision()) {
                throw new IllegalStateException("STORAGE_PROFILE_REVISION_CONFLICT");
            }
            if (!profile.getKeyPrefix().equals(prefix)) {
                throw new IllegalArgumentException("STORAGE_PROFILE_PREFIX_IMMUTABLE");
            }
            profile.update(backend, trim(form.accountId()), trim(form.accessKeyId()),
                    replacementSecret, trim(form.bucket()), trim(form.endpoint()),
                    region(form.region()), form.enabled(), actorId);
        }
        if (profile.isEnabled()) {
            resolver.validate(profile);
        }
        repository.saveAndFlush(profile);
        r2Clients.invalidate(code);
        log.info("Storage profile {} revision updated by admin {}", code, actorId);
        return code;
    }

    @Transactional
    public boolean toggle(StorageProfileCode code, long expectedRevision, Long actorId) {
        StorageProfile profile = repository.findByCodeForUpdate(code)
                .orElseThrow(() -> new IllegalArgumentException("STORAGE_PROFILE_NOT_FOUND"));
        requireRevision(profile, expectedRevision);
        profile.toggle(actorId);
        if (profile.isEnabled()) resolver.validate(profile);
        repository.saveAndFlush(profile);
        r2Clients.invalidate(code);
        return profile.isEnabled();
    }

    @Transactional
    public void delete(StorageProfileCode code, long expectedRevision) {
        StorageProfile profile = repository.findByCodeForUpdate(code)
                .orElseThrow(() -> new IllegalArgumentException("STORAGE_PROFILE_NOT_FOUND"));
        requireRevision(profile, expectedRevision);
        if (profile.isEnabled()) {
            throw new IllegalStateException("STORAGE_PROFILE_DELETE_REQUIRES_DISABLED");
        }
        if (referenceCount(code) > 0L) {
            throw new IllegalStateException("STORAGE_PROFILE_STILL_REFERENCED");
        }
        repository.delete(profile);
        r2Clients.invalidate(code);
        log.info("Unreferenced disabled storage profile {} deleted", code);
    }

    @Transactional(readOnly = true)
    public Optional<String> revealSecret(StorageProfileCode code) {
        return repository.findById(code)
                .map(StorageProfile::getSecretAccessKey)
                .filter(secret -> secret != null && !secret.isBlank());
    }

    private long referenceCount(StorageProfileCode code) {
        String value = code.name();
        Long count = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM lecturer_assets WHERE storage_profile_code = ?)
                  + (SELECT COUNT(*) FROM practice_asset_lifecycle_tasks WHERE storage_profile_code = ?)
                  + (SELECT COUNT(*) FROM practice_speaking_media WHERE storage_profile_code = ?)
                  + (SELECT COUNT(*) FROM practice_speaking_media_cleanup_tasks WHERE storage_profile_code = ?)
                  + (SELECT COUNT(*) FROM practice_storage_migration_jobs
                       WHERE source_profile_code = ? OR target_profile_code = ?)
                """, Long.class, value, value, value, value, value, value);
        return count == null ? 0L : count;
    }

    private static void requireRevision(StorageProfile profile, long expectedRevision) {
        if (profile.getRevision() != expectedRevision) {
            throw new IllegalStateException("STORAGE_PROFILE_REVISION_CONFLICT");
        }
    }

    private static ProfileRow row(StorageProfile profile) {
        return new ProfileRow(profile.getProfileCode(), profile.getBackend(),
                value(profile.getAccountId()), value(profile.getAccessKeyId()),
                value(profile.getBucket()), value(profile.getEndpoint()),
                value(profile.getRegion()), profile.getKeyPrefix(), profile.isEnabled(),
                profile.getRevision(), profile.getUpdatedAt());
    }

    private static boolean newSecret(String value) {
        return value != null && !value.isBlank() && !MASKED.equals(value.trim());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String region(String value) {
        String normalized = trim(value);
        return normalized.isBlank() ? "auto" : normalized.toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
