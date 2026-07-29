package com.ksh.features.mail.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailOutboxSchedulerIsolationTest {

    @Test
    void worker_uses_private_mail_thread_and_stops_cleanly() throws Exception {
        MailOutboxProcessor processor = mock(MailOutboxProcessor.class);
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        when(processor.processDue(10)).thenAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            invoked.countDown();
            return 0;
        });
        MailOutboxWorker worker = new MailOutboxWorker(
                processor,
                10,
                Duration.ZERO,
                Duration.ofHours(1));
        try {
            worker.start();
            assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).startsWith("ksh-mail-outbox-");
            assertThat(worker.isRunning()).isTrue();
        } finally {
            worker.stop();
        }
        assertThat(worker.isRunning()).isFalse();
    }
}
