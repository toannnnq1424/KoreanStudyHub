package com.ksh.features.lessons;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class KoreanVideoSeedContractTest {
    @Test void seed_preserves_existing_media_and_covers_both_lesson_scopes() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V152__seed_korean_reference_videos.sql"));
        assertThat(sql).contains("UPDATE lesson_templates t", "UPDATE lessons l",
                "t.video_library_asset_id IS NULL", "l.video_library_asset_id IS NULL",
                "NULLIF(TRIM(t.video_url), '') IS NULL", "NULLIF(TRIM(l.video_url), '') IS NULL",
                "NULLIF(TRIM(t.video_provider), '') IS NULL", "NULLIF(TRIM(l.video_provider), '') IS NULL",
                "code REGEXP '^(KOR|KRL|TOP)'", "NOT EXISTS", "u.is_deleted = 0");
        assertThat(sql).doesNotContain("SET content_type", "SET content_richtext", "DELETE FROM");
    }
}
