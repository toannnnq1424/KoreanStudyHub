package com.ksh.features.mail.outbox;

import com.ksh.features.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Claims and delivers due mail jobs without holding a database transaction
 * during the SMTP call.
 */
@Component
public class MailOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxProcessor.class);

    private final MailOutboxTransactionService transactions;
    private final MailService mailService;
    private final String workerId;

    @Autowired
    public MailOutboxProcessor(
            MailOutboxTransactionService transactions,
            MailService mailService) {
        this(transactions, mailService, UUID.randomUUID().toString());
    }

    MailOutboxProcessor(
            MailOutboxTransactionService transactions,
            MailService mailService,
            String workerId) {
        this.transactions = transactions;
        this.mailService = mailService;
        this.workerId = workerId;
    }

    public int processDue(int batchSize) {
        List<Long> ids = transactions.findClaimableIds(batchSize);
        int claimed = 0;
        for (Long id : ids) {
            var delivery = transactions.claim(id, workerId);
            if (delivery.isEmpty()) {
                continue;
            }
            claimed++;
            deliver(delivery.get());
        }
        return claimed;
    }

    private void deliver(MailOutboxDelivery delivery) {
        boolean sent;
        try {
            sent = mailService.send(
                    delivery.recipientEmail(),
                    delivery.subject(),
                    delivery.body());
        } catch (RuntimeException ignored) {
            sent = false;
        }

        try {
            if (sent) {
                if (!transactions.recordSuccess(delivery.jobId(), workerId)) {
                    log.warn("Mail outbox success had a stale lease for job {}", delivery.jobId());
                }
            } else if (!transactions.recordFailure(
                    delivery.jobId(),
                    workerId,
                    MailOutboxTransactionService.ERROR_DELIVERY_FAILED)) {
                log.warn("Mail outbox failure had a stale lease for job {}", delivery.jobId());
            }
        } catch (RuntimeException ignored) {
            // The lease will expire and make the durable row claimable again.
            log.error("Could not persist mail outbox outcome for job {}", delivery.jobId());
        }
    }
}
