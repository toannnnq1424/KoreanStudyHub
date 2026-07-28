package com.ksh.testsafety;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisposableTestDatabaseEnvironmentGuardTest {

    private final DisposableTestDatabaseEnvironmentGuard guard =
            new DisposableTestDatabaseEnvironmentGuard();

    @Test
    void acceptsUniqueDisposableCatalogWithExplicitCredentials() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:mysql://localhost:3306/ksh_test_post13h_20260729?useUnicode=true")
                .withProperty(
                        "spring.datasource.username", "test_user")
                .withProperty(
                        "spring.datasource.password", "test_password");

        assertThatCode(() -> guard.postProcessEnvironment(
                environment, new SpringApplication(Object.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDeveloperAndSharedTestCatalogs() {
        for (String catalog : new String[]{"ksh_db", "ksh_test"}) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty(
                            "spring.datasource.url",
                            "jdbc:mysql://localhost:3306/" + catalog)
                    .withProperty(
                            "spring.datasource.username", "root")
                    .withProperty(
                            "spring.datasource.password", "secret");

            assertThatThrownBy(() -> guard.postProcessEnvironment(
                    environment, new SpringApplication(Object.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Refusing test database catalog");
        }
    }

    @Test
    void rejectsMissingExplicitCredentials() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:mysql://localhost:3306/ksh_test_gate_123456")
                .withProperty(
                        "spring.datasource.username", "test_user");

        assertThatThrownBy(() -> guard.postProcessEnvironment(
                environment, new SpringApplication(Object.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }
}
