package com.ksh.features.classes;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NonPracticeLearningFlowMigrationContractTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void subject_seed_and_class_lifecycle_are_explicit() throws IOException {
        String sql = read("V88__subject_catalog_and_class_lifecycle.sql");

        assertThat(sql).contains("'KOR311'", "'KOR321'", "'KOR411'");
        assertThat(sql).contains("WHERE NOT EXISTS");
        assertThat(sql).contains("'DRAFT','ACTIVE','ARCHIVED'");
        assertThat(sql).doesNotContain("CREATE TABLE");
    }

    @Test
    void compaction_adds_only_the_approved_co_lecturer_table() throws IOException {
        List<String> files = List.of(
                "V88__subject_catalog_and_class_lifecycle.sql",
                "V89__add_class_co_lecturers.sql",
                "V90__remove_class_invites.sql",
                "V91__subject_activity_audit.sql");
        String combined = files.stream().map(this::readUnchecked).reduce("", String::concat);

        assertThat(count(combined, "CREATE TABLE")).isEqualTo(1);
        assertThat(combined).contains("CREATE TABLE class_co_lecturers");
        assertThat(combined).doesNotContain("CREATE TABLE subjects");
        assertThat(combined).doesNotContain("practice_");
    }

    @Test
    void invite_storage_and_only_the_unused_department_activity_table_are_removed()
            throws IOException {
        String invites = read("V90__remove_class_invites.sql");
        String activities = read("V91__subject_activity_audit.sql");

        assertThat(invites).contains("DROP COLUMN invite_code_id", "DROP TABLE class_invite_codes");
        assertThat(activities).contains(
                "DROP TABLE activity_departments",
                "RENAME TABLE department_activities TO subject_activities",
                "RENAME COLUMN department_id TO subject_id");
        assertThat(activities).doesNotContain("DROP TABLE department_activities");
    }

    private String read(String file) throws IOException {
        return Files.readString(MIGRATIONS.resolve(file));
    }

    private String readUnchecked(String file) {
        try {
            return read(file);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private long count(String value, String token) {
        return value.lines().filter(line -> line.contains(token)).count();
    }
}
