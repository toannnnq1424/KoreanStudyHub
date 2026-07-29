package com.ksh.features.mail.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxMetricsFailureIsolationTest {

    @Test
    void provider_or_registration_failure_cannot_break_component_startup() {
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> registries = mock(ObjectProvider.class);
        when(registries.orderedStream())
                .thenThrow(new IllegalStateException("exporter unavailable"));

        assertThatCode(() -> new MailOutboxMetrics(registries))
                .doesNotThrowAnyException();
    }

    @Test
    void update_failure_cannot_break_admin_snapshot_path() {
        MailOutboxMetrics metrics = withoutRegistry();

        assertThatCode(() -> metrics.update(null))
                .doesNotThrowAnyException();
    }

    @Test
    void counter_failures_are_isolated_and_one_counter_does_not_block_the_other() {
        MailOutboxMetrics metrics = withoutRegistry();
        Counter sent = mock(Counter.class);
        Counter failed = mock(Counter.class);
        doThrow(new IllegalStateException("sent exporter unavailable"))
                .when(sent).increment(anyDouble());
        doThrow(new IllegalStateException("failed exporter unavailable"))
                .when(failed).increment(anyDouble());
        setField(metrics, "sentRetentionDeleted", sent);
        setField(metrics, "failedRetentionDeleted", failed);

        assertThatCode(() -> metrics.recordRetention(summary()))
                .doesNotThrowAnyException();
        verify(sent).increment(2);
        verify(failed).increment(3);
    }

    private static MailOutboxMetrics withoutRegistry() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        return new MailOutboxMetrics(
                beans.getBeanProvider(MeterRegistry.class));
    }

    private static MailOutboxRetentionSummary summary() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 8, 0);
        MailOutboxOperationalSnapshot snapshot =
                new MailOutboxOperationalSnapshot(
                        now,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0);
        return new MailOutboxRetentionSummary(
                now,
                now.minusDays(30),
                now.minusDays(90),
                10,
                2,
                3,
                snapshot);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
