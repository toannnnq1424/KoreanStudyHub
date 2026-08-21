package com.ksh.features.admin.users.imports.service;

import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.features.admin.users.service.AdminUsersAuditWriter;
import com.ksh.features.auth.service.AccountActivationService;
import com.ksh.features.mail.job.MailJob;
import com.ksh.features.mail.job.MailJobEnqueueHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.ksh.common.IConstant.URL_ACTIVATE;

/**
 * Issues an activation token for an account and queues its activation email.
 *
 * <p>Shared by the roster import (a fan-out over many rows) and the admin
 * re-send action (a single interactive click). Both go through
 * {@link MailJobEnqueueHelper#enqueueAfterCommit} — sending SMTP on the request
 * thread while looping over import rows is forbidden by
 * {@code .claude/rules/mail-job-queue.md}, and after-commit delivery means a
 * rolled-back import sends nothing at all.
 *
 * <p>The raw token is embedded in the link and never logged, and never reaches
 * the audit entry this writes — the audit log is readable by every
 * administrator, and a leaked token is equivalent to account takeover.
 */
@Component
public class ActivationMailComposer {

    /** Stable label so activation mail is greppable in the worker's logs. */
    public static final String MAIL_SOURCE_ACTIVATION = "ACCOUNT_ACTIVATION";

    private static final String SUBJECT = "[KSH] Kích hoạt tài khoản của bạn";

    private final AccountActivationService activationService;
    private final MailJobEnqueueHelper mailJobEnqueueHelper;
    private final AdminUsersAuditWriter auditWriter;
    private final String baseUrl;

    public ActivationMailComposer(AccountActivationService activationService,
                                  MailJobEnqueueHelper mailJobEnqueueHelper,
                                  AdminUsersAuditWriter auditWriter,
                                  @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.activationService = activationService;
        this.mailJobEnqueueHelper = mailJobEnqueueHelper;
        this.auditWriter = auditWriter;
        this.baseUrl = baseUrl;
    }

    /**
     * Issues a fresh token for {@code user} — invalidating any outstanding one
     * — queues the activation email for background delivery, and records an
     * {@code ACTIVATION_SENT} audit entry.
     *
     * <p>The audit row is written inside the caller's transaction, so a
     * rolled-back import leaves neither the account, nor the token, nor the
     * audit entry behind — matching the mail that after-commit delivery
     * likewise never sends.
     *
     * @param user    the account whose owner must activate it
     * @param actorId the administrator triggering it, or {@code null} if none
     */
    public void issueAndQueue(User user, Long actorId) {
        String rawToken = activationService.issueToken(user);
        String link = baseUrl + URL_ACTIVATE + "?token=" + rawToken;
        mailJobEnqueueHelper.enqueueAfterCommit(
                MailJob.of(user.getEmail(), SUBJECT, body(user, link), MAIL_SOURCE_ACTIVATION));
        // Metadata deliberately null: the only detail worth recording would be
        // the token, and that must never reach the audit log.
        auditWriter.write(user.getId(), UserActivity.TYPE_ACTIVATION_SENT,
                "Gửi email kích hoạt tới " + user.getEmail(), null, actorId);
    }

    private String body(User user, String link) {
        String name = user.getFullName() == null || user.getFullName().isBlank()
                ? user.getEmail()
                : user.getFullName();
        return "Xin chào " + name + ",\n\n"
                + "Tài khoản KSH của bạn đã được tạo. Hãy mở liên kết dưới đây để đặt "
                + "mật khẩu và kích hoạt tài khoản:\n\n"
                + link + "\n\n"
                + "Liên kết có hiệu lực trong " + activationService.tokenTtlDays() + " ngày. "
                + "Nếu bạn không yêu cầu tài khoản này, hãy bỏ qua email.\n\n"
                + "— Hệ thống KSH";
    }
}