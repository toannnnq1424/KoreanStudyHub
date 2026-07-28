package com.ksh.features.mail.outbox;

import com.ksh.features.mail.MailService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailOutboxProcessorTest {

    private static final String WORKER_ID = "test-worker";
    private static final MailOutboxDelivery DELIVERY =
            new MailOutboxDelivery(7L, "student@ksh.edu.vn", "Subject", "Body");

    @Test
    void processDue_records_success_after_transport_accepts_message() {
        MailOutboxTransactionService transactions =
                mock(MailOutboxTransactionService.class);
        MailService mailService = mock(MailService.class);
        when(transactions.findClaimableIds(10)).thenReturn(List.of(7L));
        when(transactions.claim(7L, WORKER_ID)).thenReturn(Optional.of(DELIVERY));
        when(mailService.send(
                DELIVERY.recipientEmail(),
                DELIVERY.subject(),
                DELIVERY.body())).thenReturn(true);
        when(transactions.recordSuccess(7L, WORKER_ID)).thenReturn(true);
        MailOutboxProcessor processor =
                new MailOutboxProcessor(transactions, mailService, WORKER_ID);

        int processed = processor.processDue(10);

        assertThat(processed).isEqualTo(1);
        verify(transactions).recordSuccess(7L, WORKER_ID);
        verify(transactions, never()).recordFailure(
                7L,
                WORKER_ID,
                MailOutboxTransactionService.ERROR_DELIVERY_FAILED);
    }

    @Test
    void processDue_records_retryable_failure_when_transport_returns_false() {
        MailOutboxTransactionService transactions =
                mock(MailOutboxTransactionService.class);
        MailService mailService = mock(MailService.class);
        when(transactions.findClaimableIds(10)).thenReturn(List.of(7L));
        when(transactions.claim(7L, WORKER_ID)).thenReturn(Optional.of(DELIVERY));
        when(mailService.send(
                DELIVERY.recipientEmail(),
                DELIVERY.subject(),
                DELIVERY.body())).thenReturn(false);
        when(transactions.recordFailure(
                7L,
                WORKER_ID,
                MailOutboxTransactionService.ERROR_DELIVERY_FAILED)).thenReturn(true);
        MailOutboxProcessor processor =
                new MailOutboxProcessor(transactions, mailService, WORKER_ID);

        int processed = processor.processDue(10);

        assertThat(processed).isEqualTo(1);
        verify(transactions).recordFailure(
                7L,
                WORKER_ID,
                MailOutboxTransactionService.ERROR_DELIVERY_FAILED);
        verify(transactions, never()).recordSuccess(7L, WORKER_ID);
    }

    @Test
    void processDue_converts_transport_exception_to_generic_failure() {
        MailOutboxTransactionService transactions =
                mock(MailOutboxTransactionService.class);
        MailService mailService = mock(MailService.class);
        when(transactions.findClaimableIds(10)).thenReturn(List.of(7L));
        when(transactions.claim(7L, WORKER_ID)).thenReturn(Optional.of(DELIVERY));
        doThrow(new IllegalStateException("transport detail"))
                .when(mailService)
                .send(
                        DELIVERY.recipientEmail(),
                        DELIVERY.subject(),
                        DELIVERY.body());
        when(transactions.recordFailure(
                7L,
                WORKER_ID,
                MailOutboxTransactionService.ERROR_DELIVERY_FAILED)).thenReturn(true);
        MailOutboxProcessor processor =
                new MailOutboxProcessor(transactions, mailService, WORKER_ID);

        processor.processDue(10);

        verify(transactions).recordFailure(
                7L,
                WORKER_ID,
                MailOutboxTransactionService.ERROR_DELIVERY_FAILED);
    }

    @Test
    void processDue_skips_candidate_lost_to_another_node() {
        MailOutboxTransactionService transactions =
                mock(MailOutboxTransactionService.class);
        MailService mailService = mock(MailService.class);
        when(transactions.findClaimableIds(10)).thenReturn(List.of(7L));
        when(transactions.claim(7L, WORKER_ID)).thenReturn(Optional.empty());
        MailOutboxProcessor processor =
                new MailOutboxProcessor(transactions, mailService, WORKER_ID);

        int processed = processor.processDue(10);

        assertThat(processed).isZero();
        verify(mailService, never()).send(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
