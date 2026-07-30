package com.ksh.features.practice.preferences;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class PracticeKoreanFontPreferenceService {

    public static final int SCHEMA_VERSION = 2;
    public static final String CACHE_NAMESPACE = "practice-korean-font-preference-v2";

    private final PracticeUserPreferenceRepository repository;

    public PracticeKoreanFontPreferenceService(PracticeUserPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Snapshot read(Long userId) {
        requireUserId(userId);
        return repository.findById(userId)
                .map(row -> new Snapshot(
                        row.getUserId(),
                        Objects.requireNonNullElse(
                                row.getKoreanFont(),
                                PracticeKoreanFont.DEFAULT),
                        Objects.requireNonNullElse(
                                row.getKoreanFontSize(),
                                PracticeKoreanFontSize.DEFAULT_VALUE),
                        SCHEMA_VERSION))
                .orElseGet(() -> new Snapshot(
                        userId,
                        PracticeKoreanFont.DEFAULT,
                        PracticeKoreanFontSize.DEFAULT_VALUE,
                        SCHEMA_VERSION));
    }

    @Transactional
    public Snapshot update(Long userId,
                           PracticeKoreanFont koreanFont,
                           PracticeKoreanFontSize koreanFontSize,
                           int schemaVersion) {
        requireUserId(userId);
        if (koreanFont == null) {
            throw new IllegalArgumentException("Korean font is required.");
        }
        if (koreanFontSize == null) {
            throw new IllegalArgumentException("Korean font size is required.");
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported preference schema version.");
        }
        repository.upsert(
                userId,
                koreanFont.name(),
                koreanFontSize.name(),
                SCHEMA_VERSION);
        return new Snapshot(
                userId,
                koreanFont,
                koreanFontSize,
                SCHEMA_VERSION);
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Authenticated user id is required.");
        }
    }

    public record Snapshot(Long accountId,
                           PracticeKoreanFont koreanFont,
                           PracticeKoreanFontSize koreanFontSize,
                           int schemaVersion) {
    }
}
