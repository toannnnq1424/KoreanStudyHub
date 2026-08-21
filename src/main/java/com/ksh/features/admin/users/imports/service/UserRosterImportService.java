package com.ksh.features.admin.users.imports.service;

import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.users.imports.InvalidRosterFileException;
import com.ksh.features.admin.users.imports.dto.UserImportResult;
import com.ksh.features.admin.users.imports.dto.UserImportRow;
import com.ksh.features.admin.users.imports.dto.UserImportRowStatus;
import com.ksh.features.admin.users.imports.parser.UserRosterParser;
import com.ksh.features.admin.users.imports.session.UserImportSession;
import com.ksh.features.admin.users.imports.session.UserImportSessionStore;
import com.ksh.features.admin.users.imports.validator.UserRosterRowValidator;
import com.ksh.features.admin.users.service.AdminUsersAuditWriter;
import com.ksh.features.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Top-level service for the admin roster import.
 *
 * <p>{@link #previewUpload} parses and validates the file and stages a
 * {@link UserImportSession} — it writes nothing. {@link #confirmImport} replays
 * that session, creating one account per creatable row and queueing its
 * activation email.
 *
 * <p>Existing accounts are never touched. A row whose email already belongs to
 * an account (matched case-insensitively, soft-deleted rows included) is
 * reported as skipped and no field of that account is read into a write.
 *
 * <p>Created accounts start unusable: {@code is_active = 0},
 * {@code activated_at = NULL}, and a BCrypt hash of a freshly generated random
 * secret. That secret is discarded immediately and never logged, displayed, or
 * returned — nobody, including the importing administrator, knows a password
 * that authenticates the account until its owner sets one through activation.
 *
 * <p>Authorization is layered: the controller restricts the endpoints to
 * {@code ADMIN}, and {@link UserImportSessionStore} enforces that only the
 * administrator who staged a preview may confirm it.
 */
@Service
public class UserRosterImportService {

    private static final Logger log = LoggerFactory.getLogger(UserRosterImportService.class);

    private static final SecureRandom RNG = new SecureRandom();
    /** 32 random bytes is far beyond guessing range for a secret nobody ever learns. */
    private static final int PLACEHOLDER_SECRET_BYTES = 32;

    private static final String MSG_SESSION_EXPIRED =
            "Phiên import đã hết hạn hoặc không tồn tại. Vui lòng tải file lên lại.";

    private final UserRosterParser parser;
    private final UserRosterRowValidator rowValidator;
    private final UserImportSessionStore sessionStore;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivationMailComposer activationMailComposer;
    private final AdminUsersAuditWriter auditWriter;

    public UserRosterImportService(UserRosterParser parser,
                                   UserRosterRowValidator rowValidator,
                                   UserImportSessionStore sessionStore,
                                   UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   ActivationMailComposer activationMailComposer,
                                   AdminUsersAuditWriter auditWriter) {
        this.parser = parser;
        this.rowValidator = rowValidator;
        this.sessionStore = sessionStore;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activationMailComposer = activationMailComposer;
        this.auditWriter = auditWriter;
    }

    /**
     * Parses and validates the uploaded roster, staging a preview session. No
     * account is created or modified.
     *
     * @param file    the uploaded spreadsheet
     * @param adminId the acting administrator, who alone may confirm the result
     * @return the staged session
     * @throws InvalidRosterFileException on whole-file failures (format, size, headers …)
     */
    @Transactional(readOnly = true)
    public UserImportSession previewUpload(MultipartFile file, Long adminId) {
        UserRosterParser.ParsedRoster parsed = parser.parse(file);
        List<UserImportRow> rows = rowValidator.validate(parsed.rows());

        UserImportSession session = new UserImportSession(
                UUID.randomUUID(), adminId, Instant.now(), parsed.fileName(), rows);
        sessionStore.save(session);
        return session;
    }

    /**
     * Creates an account for every creatable row of a staged session and queues
     * one activation email per created account.
     *
     * <p>Rows already marked as existing or erroneous are counted and skipped.
     * A row that fails to persist is re-marked {@code PERSISTENCE_FAILED} and
     * counted as an error; because the whole confirm runs in one transaction,
     * a constraint violation surfacing at flush time rolls the batch back — in
     * which case the after-commit mail hooks never fire and no activation email
     * is delivered for those rows.
     *
     * @param sessionId the staged session id
     * @param adminId   the acting administrator; must own the session
     * @return per-category counts plus the per-row report
     * @throws InvalidRosterFileException when the session is missing, expired,
     *                                    or belongs to another administrator
     */
    @Transactional
    public UserImportResult confirmImport(UUID sessionId, Long adminId) {
        UserImportSession session = sessionStore.get(sessionId, adminId)
                .orElseThrow(() -> new InvalidRosterFileException(MSG_SESSION_EXPIRED));

        int created = 0;
        int alreadyExisted = 0;
        int errors = 0;
        int roleDefaulted = 0;

        for (UserImportRow row : session.getRows()) {
            UserImportRowStatus status = row.getStatus();
            if (status == null) continue;

            if (status.isSkipped()) {
                alreadyExisted++;
                continue;
            }
            if (!status.isCreatable()) {
                errors++;
                continue;
            }

            try {
                User saved = createPendingAccount(row);
                auditWriter.write(saved.getId(), UserActivity.TYPE_IMPORTED,
                        "Tạo tài khoản từ import: " + saved.getEmail(),
                        auditWriter.serialize(importMetadata(session)), adminId);
                activationMailComposer.issueAndQueue(saved, adminId);
                created++;
                // Counted only on success, so a row that failed to persist never
                // inflates the "defaulted role" figure in the summary.
                if (row.isRoleDefaulted()) roleDefaulted++;
            } catch (RuntimeException ex) {
                // Report the row rather than aborting the loop; a failure that
                // surfaces only at flush time still rolls the transaction back.
                log.warn("Failed to create imported account at row {}: {}",
                        row.getRowNumber(), ex.getMessage());
                row.mark(UserImportRowStatus.PERSISTENCE_FAILED);
                errors++;
            }
        }

        sessionStore.delete(sessionId);
        return new UserImportResult(session.totalRows(), created, alreadyExisted, errors,
                roleDefaulted, session.getRows());
    }

    /**
     * Persists one not-yet-activated account. The generated secret exists only
     * as a local variable long enough to be hashed.
     */
    private User createPendingAccount(UserImportRow row) {
        String normalizedEmail = row.getEmail().trim().toLowerCase(Locale.ROOT);
        String fullName = blankToFallback(row.getFullName(), normalizedEmail);
        User user = UserFactory.newPendingActivation(
                normalizedEmail,
                passwordEncoder.encode(generateUndisclosedSecret()),
                fullName,
                row.getRole(),
                blankToNull(row.getPhone()),
                row.getSubjectId());
        return userRepository.save(user);
    }

    /**
     * Audit metadata for an imported account: enough to trace the row back to
     * its upload, and nothing sensitive. The generated secret is deliberately
     * absent — nobody, including the auditor, may learn it.
     */
    private static Map<String, Object> importMetadata(UserImportSession session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId().toString());
        payload.put("fileName", session.getFileName());
        return payload;
    }

    /** Random secret nobody ever sees: it is hashed and discarded in one expression. */
    private static String generateUndisclosedSecret() {
        byte[] bytes = new byte[PLACEHOLDER_SECRET_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToFallback(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}