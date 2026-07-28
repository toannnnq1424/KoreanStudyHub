package com.ksh.testsafety;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-classpath-only guard that runs after ConfigData has resolved the
 * datasource and before a DataSource, Flyway, or test fixture can connect.
 */
public final class DisposableTestDatabaseEnvironmentGuard
        implements EnvironmentPostProcessor, Ordered {

    private static final Pattern MYSQL_CATALOG = Pattern.compile(
            "^jdbc:mysql://[^/]+/([^?;]+)(?:[?;].*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPOSABLE_CATALOG = Pattern.compile(
            "^ksh_test_[a-z0-9][a-z0-9_]{5,62}$");

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url");
        Matcher matcher = url == null
                ? MYSQL_CATALOG.matcher("")
                : MYSQL_CATALOG.matcher(url.trim());
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    "TEST_DB_URL must be an explicit jdbc:mysql URL for a disposable Practice test catalog.");
        }
        String catalog = matcher.group(1)
                .toLowerCase(Locale.ROOT);
        if (!DISPOSABLE_CATALOG.matcher(catalog).matches()) {
            throw new IllegalStateException(
                    "Refusing test database catalog '" + catalog
                            + "'. Use a unique ksh_test_<run_id> catalog; ksh_db and shared ksh_test are forbidden.");
        }
        requireCredential(environment, "spring.datasource.username");
        requireCredential(environment, "spring.datasource.password");
    }

    private static void requireCredential(
            ConfigurableEnvironment environment, String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    property + " must be supplied explicitly for disposable database tests.");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
