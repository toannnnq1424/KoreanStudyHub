package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeStimulusLanguageAuthorityMigrationTest {

    @Test
    void v74AddsSeparateInstructionAndStimulusLanguageAuthorityToDraftAndSnapshot() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V74__practice_stimulus_language_authority.sql"));

        assertTrue(migration.contains("ALTER TABLE practice_question_groups"));
        assertTrue(migration.contains("ALTER TABLE practice_question_group_versions"));
        assertTrue(migration.contains(
                "instruction_language_tag VARCHAR(8) NOT NULL DEFAULT 'vi'"));
        assertTrue(migration.contains(
                "stimulus_language_tag VARCHAR(8) NOT NULL DEFAULT 'ko'"));
        assertTrue(migration.contains(
                "CHECK (instruction_language_tag IN ('ko', 'vi'))"));
        assertTrue(migration.contains(
                "CHECK (stimulus_language_tag IN ('ko', 'vi'))"));
    }
}
