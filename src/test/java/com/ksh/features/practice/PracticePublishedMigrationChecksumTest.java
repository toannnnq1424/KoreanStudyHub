package com.ksh.features.practice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticePublishedMigrationChecksumTest {

    private static final Path MIGRATION_ROOT =
            Path.of("src/main/resources/db/migration");
    private static final Path MANIFEST =
            Path.of("docs/operations/practice-migrations-v1-v56.sha256");

    @Test
    void publishedV1ThroughV56RemainByteImmutable() throws Exception {
        List<String> entries = Files.readAllLines(MANIFEST).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertThat(entries).hasSize(56);

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (String entry : entries) {
            String[] fields = entry.split("  ", 2);
            assertThat(fields).hasSize(2);
            Path migration = MIGRATION_ROOT.resolve(fields[1]);
            assertThat(migration).exists();
            String actual = HexFormat.of().formatHex(
                    sha256.digest(Files.readAllBytes(migration)));
            assertThat(actual)
                    .as("published migration %s", fields[1])
                    .isEqualTo(fields[0]);
            sha256.reset();
        }
    }
}
