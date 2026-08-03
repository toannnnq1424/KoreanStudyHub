package com.ksh.features.practice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectAudioReviewerAccessAuditRetentionWorkerTest {

    @Test
    void runOnceDelegatesWithBoundedConfiguredBatch() {
        DirectAudioReviewerAccessAuditRetention retention =
                mock(DirectAudioReviewerAccessAuditRetention.class);
        when(retention.purgeExpired(1_000)).thenReturn(8);
        DirectAudioReviewerAccessAuditRetentionWorker worker =
                new DirectAudioReviewerAccessAuditRetentionWorker(retention, 10_000);

        assertThat(worker.runOnce()).isEqualTo(8);
        verify(retention).purgeExpired(1_000);
    }
}
