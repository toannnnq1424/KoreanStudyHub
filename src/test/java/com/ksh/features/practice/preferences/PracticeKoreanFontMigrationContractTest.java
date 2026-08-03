package com.ksh.features.practice.preferences;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeKoreanFontMigrationContractTest {

    private static final Path V64 = Path.of(
            "src/main/resources/db/migration/"
                    + "V70__practice_user_korean_font_preference.sql");
    private static final Path V65 = Path.of(
            "src/main/resources/db/migration/"
                    + "V71__practice_korean_webfont_and_size_preference.sql");
    private static final Path V66 = Path.of(
            "src/main/resources/db/migration/"
                    + "V72__practice_korean_webfont_allowlist_expansion.sql");
    @Test
    void forwardMigrationKeepsPreferenceInsidePracticeAndEnforcesAllowlist()
            throws Exception {
        String v64 = Files.readString(V64);
        String v65 = Files.readString(V65);
        String v66 = Files.readString(V66);

        assertThat(v64).contains(
                "CREATE TABLE practice_user_preferences",
                "user_id BIGINT NOT NULL",
                "PRIMARY KEY (user_id)",
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE",
                "korean_font VARCHAR(32) NOT NULL DEFAULT 'NANUM_MYEONGJO'",
                "'NANUM_MYEONGJO'",
                "'GUNGSUH'",
                "'DOTUM'",
                "'BATANG'",
                "'YOON_GOTHIC'",
                "CHECK (preference_schema_version = 1)");
        assertThat(v64).doesNotContain(
                "ALTER TABLE users",
                "DROP TABLE",
                "DROP COLUMN");
        assertThat(v65).contains(
                "UPDATE practice_user_preferences",
                "WHEN 'GUNGSUH' THEN 'SONG_MYUNG'",
                "WHEN 'DOTUM' THEN 'NANUM_GOTHIC'",
                "WHEN 'YOON_GOTHIC' THEN 'IBM_PLEX_SANS_KR'",
                "ADD COLUMN korean_font_size VARCHAR(24)",
                "'SONG_MYUNG'",
                "'NANUM_GOTHIC'",
                "'IBM_PLEX_SANS_KR'",
                "'DO_HYEON'",
                "'JUA'",
                "'GAEGU'",
                "'DEFAULT'",
                "'LARGE'",
                "'EXTRA_LARGE'",
                "CHECK (preference_schema_version = 2)");
        assertThat(v65).doesNotContain(
                "ALTER TABLE users",
                "DROP TABLE",
                "DROP COLUMN");
        assertThat(v66).contains(
                "DROP CHECK chk_practice_user_preferences_korean_font",
                "WHEN 'SONG_MYUNG' THEN 'NANUM_MYEONGJO'",
                "WHEN 'IBM_PLEX_SANS_KR' THEN 'NANUM_GOTHIC'",
                "'DIPHYLLEIA'",
                "'BLACK_AND_WHITE_PICTURE'",
                "'SUNFLOWER'",
                "'GUGI'",
                "'NANUM_PEN_SCRIPT'");
        for (PracticeKoreanFont font : PracticeKoreanFont.ALLOWED) {
            assertThat(v66).contains("'" + font.name() + "'");
        }
        assertThat(v66).doesNotContain(
                "ALTER TABLE users",
                "DROP TABLE",
                "DROP COLUMN",
                "'GRANDIFLORA_ONE'",
                "'BLACK_HAN_SANS'");
    }
}
