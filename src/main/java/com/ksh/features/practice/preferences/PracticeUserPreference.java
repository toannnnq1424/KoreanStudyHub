package com.ksh.features.practice.preferences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Practice-owned, account-scoped learner presentation preferences.
 */
@Entity
@Table(name = "practice_user_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeUserPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "korean_font", nullable = false, length = 32)
    private PracticeKoreanFont koreanFont;

    @Enumerated(EnumType.STRING)
    @Column(name = "korean_font_size", nullable = false, length = 24)
    private PracticeKoreanFontSize koreanFontSize;

    @Column(name = "preference_schema_version", nullable = false)
    private int preferenceSchemaVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
