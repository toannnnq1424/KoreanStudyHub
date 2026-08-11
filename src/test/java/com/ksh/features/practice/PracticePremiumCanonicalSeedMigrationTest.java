package com.ksh.features.practice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PracticePremiumCanonicalSeedMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V118__practice_demo_canonical_seed_catalog.sql");
    private static final Path PREMIUM_MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V119__practice_topik35_premium_canonical_catalog.sql");
    private static final Path DEMO_REPAIR_MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V126__repair_practice_demo_assessment_contracts.sql");
    private static final Pattern STATIC_REFERENCE = Pattern.compile(
            "/(images|audio)/practice/topik35/([0-9a-f]{64})\\.(png|mp3)");

    @Test
    void migrationReplacesCatalogWithoutAddingOrDeletingTables() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("practice-demo-canonical-v1")
                .contains("WHERE id IN (1, 2, 3, 4, 5, 6)")
                .contains("status = 'ARCHIVED'")
                .contains("KSH Demo · Đọc theo ngữ cảnh")
                .contains("KSH Demo · Nghe tình huống")
                .contains("KSH Demo · Viết Q51–Q54")
                .contains("KSH Demo · Nói theo chủ đề")
                .contains("'premium', FALSE, 'demo', TRUE")
                .doesNotContainIgnoringCase(
                        "CREATE TABLE", "DROP TABLE", "TRUNCATE TABLE",
                        "DELETE FROM");
    }

    @Test
    void everySeedQuestionIsSnapshottedThroughCanonicalGroupOwnership()
            throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("JOIN practice_question_group_versions gv")
                .contains("JOIN practice_questions q ON q.group_id = gv.group_id")
                .doesNotContain("NULL, q.id, q.question_no");
    }

    @Test
    void demoWritingContainsTheCanonicalFourTaskPointProfile()
            throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("'Q51'")
                .contains("'Q52'")
                .contains("'Q53'")
                .contains("'Q54'")
                .contains("10.00,0,'Q51'")
                .contains("10.00,1,'Q52'")
                .contains("30.00,2,'Q53'")
                .contains("50.00,3,'Q54'")
                .contains("writing-blanks.v1")
                .contains("writing-blank-authority.v1");
    }

    @Test
    void demoRepairIsForwardOnlySnapshotCompatibleAndAddsNoSchemaSurface()
            throws Exception {
        String sql = Files.readString(DEMO_REPAIR_MIGRATION);

        assertThat(sql)
                .contains("practice-demo-canonical-v1")
                .contains("'$.correctValue', 'FALSE'")
                .contains("'$.correctOptionIds', JSON_ARRAY()")
                .contains("'$.schemaVersion', 'question-content-v3'")
                .contains("UPDATE practice_questions q")
                .contains("UPDATE practice_question_versions qv")
                .doesNotContain("topik35-premium-canonical-v1")
                .doesNotContainIgnoringCase(
                        "CREATE TABLE", "DROP TABLE", "TRUNCATE TABLE",
                        "DELETE FROM", "ALTER TABLE");
    }

    @Test
    void premiumMigrationPublishesCompleteTopik35CatalogWithoutSchemaChanges()
            throws Exception {
        String sql = Files.readString(PREMIUM_MIGRATION);

        assertThat(sql)
                .contains("Premium TOPIK 35 · Đọc 50 câu")
                .contains("Premium TOPIK 35 · Nghe 50 câu liên tục")
                .contains("Premium TOPIK 35 · Viết Q51–Q54")
                .contains("'questionCount',50")
                .contains("'continuousPlayback',TRUE")
                .contains("'startOnce',TRUE")
                .contains("\"seekAllowed\":false")
                .contains("\"replayAllowed\":false")
                .contains("10.00, 0, 'Q51'")
                .contains("10.00, 1, 'Q52'")
                .contains("30.00, 2, 'Q53'")
                .contains("50.00, 3, 'Q54'")
                .doesNotContainIgnoringCase(
                        "CREATE TABLE", "DROP TABLE", "TRUNCATE TABLE",
                        "DELETE FROM");
    }

    @Test
    void everyPremiumStaticReferenceExistsAndMatchesItsContentHash()
            throws Exception {
        String sql = Files.readString(PREMIUM_MIGRATION);
        var matcher = STATIC_REFERENCE.matcher(sql);
        var references = new java.util.LinkedHashSet<String>();
        while (matcher.find()) {
            references.add(matcher.group());
        }

        assertThat(references).hasSize(16);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (String reference : references) {
            String expectedHash = reference.substring(
                    reference.lastIndexOf('/') + 1,
                    reference.lastIndexOf('.'));
            Path asset = Path.of("src/main/resources/static" + reference);
            assertThat(asset).exists().isRegularFile();
            String actualHash = HexFormat.of().formatHex(
                    sha256.digest(Files.readAllBytes(asset)));
            assertThat(actualHash).isEqualTo(expectedHash);
        }
    }
}
