package com.ksh.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityVersionMigrationContractTest {

    @Test
    void migrationAddsNonNullMonotonicUserSecurityVersion() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V125__add_user_security_version.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0");
    }
}
