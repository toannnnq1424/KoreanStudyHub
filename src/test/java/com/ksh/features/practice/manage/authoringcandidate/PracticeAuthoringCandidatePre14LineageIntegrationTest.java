package com.ksh.features.practice.manage.authoringcandidate;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
        named = "RUN_AIM2_PRE14_DB_TESTS", matches = "true")
class PracticeAuthoringCandidatePre14LineageIntegrationTest {

    @Test
    void freshAuthoritativePre14LineageStopsAtPracticeVersion75()
            throws Exception {
        String url = required("TEST_DB_URL");
        String username = required("TEST_DB_USERNAME");
        String password = required("TEST_DB_PASSWORD");
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .target(MigrationVersion.fromVersion("75"))
                .load();

        flyway.migrate();
        try (Connection connection = DriverManager.getConnection(
                url, username, password);
             Statement statement = connection.createStatement()) {
            assertThat(number(statement, """
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE success = 1
                    """)).isEqualTo(75);
            assertThat(number(statement, """
                    SELECT MAX(CAST(version AS UNSIGNED))
                    FROM flyway_schema_history
                    """)).isEqualTo(75);
            assertThat(number(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name IN (
                        'practice_attempts',
                        'practice_attempt_evaluations',
                        'practice_attempt_question_evaluations')
            """)).isEqualTo(1);
        }
    }

    private static int number(Statement statement, String sql)
            throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
