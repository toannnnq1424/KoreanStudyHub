package com.ksh.admin.settings.service;

import com.ksh.admin.settings.dto.EmailSettingsDtos;
import com.ksh.admin.settings.dto.EmailSettingsDtos.EmailSettingsForm;
import com.ksh.admin.settings.dto.EmailSettingsDtos.TestResult;
import com.ksh.shared.mail.MailSendResult;
import com.ksh.shared.mail.MailService;
import com.ksh.shared.settings.SystemSettingGroups;
import com.ksh.shared.settings.entity.SystemSetting;
import com.ksh.shared.settings.repository.SystemSettingsRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Service to process Email Settings for admin: load current configuration,
 * save new configuration, and send test email to verify SMTP transport.
 *
 * <p>Masking: keys in {@link #SECRET_KEYS} (currently only {@code smtp.password})
 * are replaced by {@link #MASKED} before returning to the view. During save,
 * if the form submits an empty value or exactly {@code MASKED}, the service
 * skips overwriting the {@code smtp.password} record.
 *
 * <p>{@link #save} is {@code @Transactional} — all upserts run within
 * a single transaction, rolling back all-or-nothing if a failure occurs.
 */
@Service
public class EmailSettingsService {

    private static final Logger log = LoggerFactory.getLogger(EmailSettingsService.class);

    private static final String GROUP = SystemSettingGroups.SMTP;
    private static final String MASKED = EmailSettingsDtos.MASKED;
    private static final Set<String> SECRET_KEYS = Set.of("smtp.password");

    private static final String TEST_SUBJECT = "ksh — SMTP test email";
    private static final String TEST_BODY =
            "This is a test email from ksh. If you received this, your SMTP "
                    + "configuration works.";

    private final SystemSettingsRepository repository;
    private final MailService mailService;

    public EmailSettingsService(SystemSettingsRepository repository, MailService mailService) {
        this.repository = repository;
        this.mailService = mailService;
    }

    /**
     * Load Email Settings from database. The password is always masked as {@code "********"}
     * before being returned to the form, even if the value is empty (for UI consistency).
     */
    @Transactional(readOnly = true)
    public EmailSettingsForm load() {
        Map<String, String> cfg = repository.loadGroupAsMap(GROUP);
        return new EmailSettingsForm(
                cfg.getOrDefault("smtp.host", ""),
                parsePortOrNull(cfg.get("smtp.port")),
                cfg.getOrDefault("smtp.encryption", "tls"),
                cfg.getOrDefault("smtp.username", ""),
                MASKED, // password luon mask
                cfg.getOrDefault("smtp.from_name", ""),
                cfg.getOrDefault("smtp.from_email", ""),
                cfg.getOrDefault("smtp.reply_to", "")
        );
    }

    /**
     * Save Email Settings. Atomicity is guaranteed by {@code @Transactional}:
     * if any row upsert fails, all written rows are rolled back.
     *
     * <p>Password handling: if {@code form.password()} is null/blank or
     * equals {@link #MASKED}, the {@code smtp.password} row is completely skipped
     * (no value update, no {@code updated_by} update).
     */
    @Transactional
    public void save(EmailSettingsForm form, Long currentUserId) {
        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("smtp.host", form.host().trim());
        incoming.put("smtp.port", form.port() == null ? "" : String.valueOf(form.port()));
        incoming.put("smtp.encryption", form.encryption().trim());
        incoming.put("smtp.username", form.username().trim());
        incoming.put("smtp.from_name", form.fromName().trim());
        incoming.put("smtp.from_email", form.fromEmail().trim());
        incoming.put("smtp.reply_to", form.replyTo() == null ? "" : form.replyTo().trim());

        // Password: only update when the user enters a new value (non-empty and not MASKED)
        if (form.password() != null && !form.password().isBlank()
                && !MASKED.equals(form.password())) {
            incoming.put("smtp.password", form.password());
        }

        upsertAll(incoming, currentUserId);
    }

    /**
     * Sends a test email to {@code to}. Returns {@link TestResult} with {@code ok}
     * status and {@code error} message (on failure) for rendering UI toast.
     *
     * <p>Does not use {@code @Transactional} because the method delegates to
     * mail transport (network call). Repository invocations inside
     * {@code DbConfiguredMailSender} are run under Spring Data JPA's transaction.
     */
    public TestResult sendTest(String to) {
        if (to == null || to.isBlank()) {
            return new TestResult(false, "Vui lòng nhập email người nhận");
        }
        try {
            InternetAddress addr = new InternetAddress(to.trim());
            addr.validate();
        } catch (AddressException e) {
            return new TestResult(false, "Email người nhận không hợp lệ");
        }

        MailSendResult result =
                mailService.sendWithDetail(to.trim(), TEST_SUBJECT, TEST_BODY);
        if (result.ok()) {
            return new TestResult(true, null);
        }
        return new TestResult(false, result.errorMessage());
    }

    // ─────────────────────────────────────────────────────────────────

    private void upsertAll(Map<String, String> incoming, Long currentUserId) {
        // Load all existing rows for SMTP group — only update existing rows
        // (V1/V9 seed has inserted all necessary rows). If a row is missing, insert new.
        Map<String, SystemSetting> existing = new HashMap<>();
        for (SystemSetting s : repository.findBySettingGroup(GROUP)) {
            existing.put(s.getSettingKey(), s);
        }

        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            SystemSetting row = existing.get(key);
            if (row == null) {
                row = new SystemSetting(key, value, GROUP);
            } else {
                row.setSettingValue(value);
            }
            row.setUpdatedBy(currentUserId);
            repository.save(row);
        }

        log.info("Email settings saved by user {} (updated {} keys{})",
                currentUserId, incoming.size(),
                incoming.containsKey("smtp.password") ? ", incl. password" : ", password unchanged");
    }

    private Integer parsePortOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}