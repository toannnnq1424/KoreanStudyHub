package com.ksh.features.admin.settings.service;

import com.ksh.features.admin.settings.dto.StorageProfileDtos.ProfileForm;
import com.ksh.features.admin.settings.dto.StorageProfileDtos.ProfileRow;
import com.ksh.features.admin.settings.dto.StorageProfileDtos.ConnectionTestResult;
import com.ksh.features.admin.settings.dto.StorageProfileDtos.ConnectionTestStatus;
import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfile;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileException;
import com.ksh.features.storage.profile.StorageProfileR2Clients;
import com.ksh.features.storage.profile.StorageProfileRepository;
import com.ksh.features.storage.profile.StorageProfileResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;
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

    /**
     * Tests one saved profile without holding a database transaction open while
     * waiting for R2. The response deliberately contains no raw SDK error text.
     */
    public ConnectionTestResult testConnection(StorageProfileCode code) {
        Optional<StorageProfile> saved = repository.findById(code);
        if (saved.isEmpty()) {
            return failed("Không tìm thấy cấu hình lưu trữ đã lưu.");
        }

        StorageProfile profile = saved.get();
        if (profile.getBackend() == StorageBackend.LOCAL) {
            return new ConnectionTestResult(ConnectionTestStatus.NOT_APPLICABLE,
                    "Profile đang dùng lưu trữ Local, không có kết nối R2 để kiểm tra.");
        }

        try {
            var resolved = resolver.validate(profile);
            var request = HeadBucketRequest.builder()
                    .bucket(resolved.bucket())
                    .overrideConfiguration(options -> options
                            .apiCallAttemptTimeout(Duration.ofSeconds(5))
                            .apiCallTimeout(Duration.ofSeconds(8)))
                    .build();
            r2Clients.client(resolved).headBucket(request);
            return new ConnectionTestResult(ConnectionTestStatus.SUCCESS,
                    "Kết nối R2 thành công; bucket có thể truy cập.");
        } catch (StorageProfileException exception) {
            log.warn("R2 connection test rejected invalid profile {} ({})",
                    code, exception.errorCode());
            return failed("Cấu hình R2 đã lưu chưa đầy đủ hoặc không hợp lệ.");
        } catch (S3Exception exception) {
            log.warn("R2 HeadBucket test failed for profile {} with HTTP {}",
                    code, exception.statusCode());
            log.debug("R2 HeadBucket response for profile {}", code, exception);
            if (exception.statusCode() == 401 || exception.statusCode() == 403) {
                return failed("R2 từ chối thông tin xác thực hoặc quyền truy cập bucket.");
            }
            if (exception.statusCode() == 404) {
                return failed("Không tìm thấy bucket R2 đã cấu hình.");
            }
            return failed("Không thể kết nối R2. Hãy kiểm tra endpoint, bucket và mạng.");
        } catch (RuntimeException exception) {
            log.warn("R2 connection test failed for profile {} ({})",
                    code, exception.getClass().getSimpleName());
            log.debug("R2 connection failure for profile {}", code, exception);
            return failed("Không thể kết nối R2. Hãy kiểm tra endpoint, bucket và mạng.");
        }
    }

    private static ConnectionTestResult failed(String message) {
        return new ConnectionTestResult(ConnectionTestStatus.FAILED, message);
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
