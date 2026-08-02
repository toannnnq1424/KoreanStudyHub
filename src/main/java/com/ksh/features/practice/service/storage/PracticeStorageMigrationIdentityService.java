package com.ksh.features.practice.service.storage;

import com.ksh.entities.PracticeStorageMigrationJob;
import com.ksh.entities.PracticeStorageMigrationLogicalType;
import com.ksh.entities.PracticeStorageMigrationStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.features.practice.repository.PracticeStorageMigrationJobRepository;
import com.ksh.features.storage.profile.StorageBackend;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticeStorageMigrationIdentityService {
    private final PracticeStorageMigrationJobRepository repository;
    private final PracticeStorageMigrationJobService jobs;
    private final JdbcTemplate jdbc;

    public PracticeStorageMigrationIdentityService(
            PracticeStorageMigrationJobRepository repository,
            PracticeStorageMigrationJobService jobs,
            JdbcTemplate jdbc) {
        this.repository = repository;
        this.jobs = jobs;
        this.jdbc = jdbc;
    }

    /**
     * Compare-and-set the logical identity and durable cleanup transition in
     * one database transaction. Bytes have already been size/hash verified.
     */
    @Transactional
    public boolean switchVerifiedTarget(Long jobId) {
        PracticeStorageMigrationJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException("STORAGE_MIGRATION_JOB_NOT_FOUND"));
        if (job.getStatus() == PracticeStorageMigrationStatus.CLEANUP_PENDING
                || job.getStatus() == PracticeStorageMigrationStatus.DELETING_SOURCE
                || job.getStatus() == PracticeStorageMigrationStatus.COMPLETED) {
            return false;
        }
        if (job.getStatus() != PracticeStorageMigrationStatus.COPIED_VERIFIED
                || job.getTargetStorageProvider() == null) {
            return false;
        }
        int updated = switch (job.getLogicalType()) {
            case LECTURER_ASSET -> updateLecturerAsset(job);
            case PDF_IMPORT_SESSION -> throw new IllegalStateException(
                    "PDF_IMPORT_SESSION_MIGRATION_RETIRED");
            case SPEAKING_MEDIA -> updateSpeakingMedia(job);
        };
        if (updated != 1) {
            throw new IllegalStateException("STORAGE_MIGRATION_LOGICAL_IDENTITY_CONFLICT");
        }
        jobs.markLogicalIdentityUpdated(jobId);
        return true;
    }

    private int updateLecturerAsset(PracticeStorageMigrationJob job) {
        requireTypeProfile(job, PracticeStorageMigrationLogicalType.LECTURER_ASSET);
        return jdbc.update("""
                UPDATE lecturer_assets
                   SET storage_profile_code = ?, storage_provider = ?, storage_key = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND storage_key = ?
                   AND storage_profile_code = ?
                   AND size_bytes = ? AND LOWER(sha256) = ?
                """, job.getTargetProfileCode().name(), job.getTargetStorageProvider().name(),
                job.getTargetStorageKey(), job.getLogicalId(), job.getSourceStorageKey(),
                job.getSourceProfileCode().name(), job.getExpectedSize(), job.getExpectedSha256());
    }

    private int updateSpeakingMedia(PracticeStorageMigrationJob job) {
        requireTypeProfile(job, PracticeStorageMigrationLogicalType.SPEAKING_MEDIA);
        String provider = job.getTargetStorageProvider() == StorageBackend.LOCAL
                ? PracticeSpeakingStorageProvider.LOCAL.name()
                : PracticeSpeakingStorageProvider.OBJECT_STORAGE.name();
        return jdbc.update("""
                UPDATE practice_speaking_media
                   SET storage_profile_code = ?, storage_provider = ?, storage_key = ?
                 WHERE id = ? AND storage_key = ?
                   AND storage_profile_code = ?
                   AND byte_size = ? AND LOWER(content_hash) = ?
                   AND status <> 'DELETED'
                """, job.getTargetProfileCode().name(), provider,
                job.getTargetStorageKey(), job.getLogicalId(), job.getSourceStorageKey(),
                job.getSourceProfileCode().name(), job.getExpectedSize(), job.getExpectedSha256());
    }

    private static void requireTypeProfile(PracticeStorageMigrationJob job,
                                           PracticeStorageMigrationLogicalType type) {
        if (job.getLogicalType() != type
                || job.getSourceProfileCode() != type.requiredProfile()
                || job.getTargetProfileCode() != type.requiredProfile()) {
            throw new IllegalStateException("STORAGE_MIGRATION_PROFILE_INVALID");
        }
    }
}
