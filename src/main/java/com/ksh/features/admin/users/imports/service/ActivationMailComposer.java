package com.ksh.features.admin.users.imports.service;

import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.features.admin.users.service.AdminUsersAuditWriter;
import com.ksh.features.auth.service.AccountActivationService;
import com.ksh.features.mail.outbox.MailOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Creates an activation token and durable outbox email in the same transaction. */
@Component
public class ActivationMailComposer {

    public static final String MAIL_SOURCE = "ACCOUNT_ACTIVATION";
    private static final String SUBJECT = "[KSH] Kích hoạt tài khoản của bạn";

    private final AccountActivationService activationService;
    private final MailOutboxService mailOutboxService;
    private final AdminUsersAuditWriter auditWriter;
    private final String baseUrl;

    public ActivationMailComposer(AccountActivationService activationService,
                                  MailOutboxService mailOutboxService,
                                  AdminUsersAuditWriter auditWriter,
                                  @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.activationService = activationService;
        this.mailOutboxService = mailOutboxService;
        this.auditWriter = auditWriter;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    public void issueAndQueue(User user, Long actorId) {
        String rawToken = activationService.issueToken(user);
        String link = baseUrl + "/activate?token=" + rawToken;
        mailOutboxService.enqueueSystemMail(
                user.getEmail(), SUBJECT, body(user, link), MAIL_SOURCE);
        auditWriter.write(user.getId(), UserActivity.TYPE_ACTIVATION_SENT,
                "Đã xếp hàng email kích hoạt tài khoản", null, actorId);
    }

    private String body(User user, String link) {
        String name = user.getFullName() == null || user.getFullName().isBlank()
                ? user.getEmail() : user.getFullName();
        return "Xin chào " + name + ",\n\n"
                + "Quản trị viên đã tạo tài khoản Korean Study Hub cho bạn. "
                + "Mở liên kết sau để đặt mật khẩu và kích hoạt tài khoản:\n\n"
                + link + "\n\n"
                + "Liên kết có hiệu lực trong " + activationService.tokenTtlDays() + " ngày. "
                + "Nếu bạn không mong đợi email này, hãy liên hệ quản trị viên.\n\n"
                + "— Korean Study Hub";
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:8080";
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
