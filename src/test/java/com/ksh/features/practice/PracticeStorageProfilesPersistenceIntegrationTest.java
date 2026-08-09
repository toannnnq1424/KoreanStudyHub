package com.ksh.features.practice;

import com.ksh.features.storage.profile.StorageBackend;
import com.ksh.features.storage.profile.StorageProfileCode;
import com.ksh.features.storage.profile.StorageProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PracticeStorageProfilesPersistenceIntegrationTest {
    @Autowired StorageProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;

    @Test
    void freshSchemaHasExactProfilesNonNullIdentitiesAndNoMigrationWork() {
        assertThat(profiles.findAll()).hasSize(3)
                .extracting(profile -> profile.getProfileCode())
                .containsExactlyInAnyOrder(StorageProfileCode.values());
        assertThat(profiles.findAll()).allSatisfy(profile -> {
            assertThat(profile.getBackend()).isEqualTo(StorageBackend.LOCAL);
            assertThat(profile.getKeyPrefix())
                    .isEqualTo(profile.getProfileCode().fixedKeyPrefix());
        });

        Integer nullableIdentityColumns = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND column_name = 'storage_profile_code'
                   AND table_name IN (
                       'lecturer_assets', 'practice_pdf_import_sessions',
                       'practice_asset_lifecycle_tasks', 'practice_speaking_media',
                       'practice_speaking_media_cleanup_tasks')
                   AND is_nullable = 'YES'
                """, Integer.class);
        assertThat(nullableIdentityColumns).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_storage_migration_jobs", Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT MAX(CAST(version AS UNSIGNED))
                  FROM flyway_schema_history WHERE success = 1
                """, Integer.class)).isGreaterThanOrEqualTo(111);
    }
}
