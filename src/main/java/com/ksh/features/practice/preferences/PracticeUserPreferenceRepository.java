package com.ksh.features.practice.preferences;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeUserPreferenceRepository
        extends JpaRepository<PracticeUserPreference, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO practice_user_preferences (
                user_id,
                korean_font,
                korean_font_size,
                preference_schema_version
            ) VALUES (
                :userId,
                :koreanFont,
                :koreanFontSize,
                :schemaVersion
            )
            ON DUPLICATE KEY UPDATE
                korean_font = VALUES(korean_font),
                korean_font_size = VALUES(korean_font_size),
                preference_schema_version = VALUES(preference_schema_version),
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(@Param("userId") Long userId,
               @Param("koreanFont") String koreanFont,
               @Param("koreanFontSize") String koreanFontSize,
               @Param("schemaVersion") int schemaVersion);
}
