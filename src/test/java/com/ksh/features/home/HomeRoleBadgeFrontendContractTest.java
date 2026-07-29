package com.ksh.features.home;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HomeRoleBadgeFrontendContractTest {

    @Test
    void homeRendersEveryApplicationRoleIncludingLeader() throws IOException {
        String template = Files.readString(
                Path.of("src/main/resources/templates/home.html"));

        assertThat(template)
                .contains("hasRole('STUDENT')")
                .contains("hasRole('LECTURER')")
                .contains("hasRole('LEADER')")
                .contains("hasRole('ADMIN')");
    }
}
