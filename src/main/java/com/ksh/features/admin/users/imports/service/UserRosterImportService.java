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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Preview/confirm orchestration for creating inactive accounts from Excel. */
@Service
public class UserRosterImportService {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int PLACEHOLDER_BYTES = 32;
    private static final String EXPIRED =
            "Phiên import đã hết hạn, đã được dùng hoặc không thuộc tài khoản này. Vui lòng tải lại file.";

    private final UserRosterParser parser;
    private final UserRosterRowValidator validator;
    private final UserImportSessionStore sessionStore;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivationMailComposer activationMailComposer;
    private final AdminUsersAuditWriter auditWriter;

    public UserRosterImportService(UserRosterParser parser,
                                   UserRosterRowValidator validator,
                                   UserImportSessionStore sessionStore,
                                   UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   ActivationMailComposer activationMailComposer,
                                   AdminUsersAuditWriter auditWriter) {
        this.parser = parser;
        this.validator = validator;
        this.sessionStore = sessionStore;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activationMailComposer = activationMailComposer;
        this.auditWriter = auditWriter;
    }

    @Transactional(readOnly = true)
    public UserImportSession previewUpload(MultipartFile file, Long adminId) {
        if (adminId == null) throw new InvalidRosterFileException("Không xác định được quản trị viên.");
        UserRosterParser.ParsedRoster parsed = parser.parse(file);
        List<UserImportRow> rows = validator.validate(parsed.rows());
        UserImportSession session = new UserImportSession(
                UUID.randomUUID(), adminId, Instant.now(), parsed.fileName(), rows);
        sessionStore.save(session);
        return session;
    }

    @Transactional
    public UserImportResult confirmImport(UUID sessionId, Long adminId) {
        UserImportSession session = sessionStore.claim(sessionId, adminId)
                .orElseThrow(() -> new InvalidRosterFileException(EXPIRED));
        restoreOnRollback(session);

        int created = 0;
        int existing = 0;
        int errors = 0;
        int defaulted = 0;

        for (UserImportRow row : session.getRows()) {
            UserImportRowStatus status = row.getStatus();
            if (status.isSkipped()) {
                existing++;
                continue;
            }
            if (!status.isCreatable()) {
                errors++;
                continue;
            }

            String email = row.getEmail().trim().toLowerCase(Locale.ROOT);
            User currentOwner = userRepository.findByEmailIncludingDeleted(email).orElse(null);
            if (currentOwner != null) {
                UserRosterRowValidator.attachExisting(row, currentOwner);
                existing++;
                continue;
            }

            User saved = userRepository.save(UserFactory.newPendingActivation(
                    email,
                    passwordEncoder.encode(randomUnknownPassword()),
                    displayName(row.getFullName(), email),
                    row.getRole(),
                    blankToNull(row.getPhone()),
                    row.getSubjectId()));

            auditWriter.write(saved.getId(), UserActivity.TYPE_IMPORTED,
                    "Tạo tài khoản mới từ Excel", auditWriter.serialize(metadata(session, row)), adminId);
            activationMailComposer.issueAndQueue(saved, adminId);
            created++;
            if (row.isRoleDefaulted()) defaulted++;
        }

        // Surface unique/FK failures before commit. A failure rolls back every
        // account, token, audit and outbox row and restores the one-shot preview.
        userRepository.flush();
        return new UserImportResult(session.totalRows(), created, existing,
                errors, defaulted, session.getRows());
    }

    private void restoreOnRollback(UserImportSession session) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) sessionStore.restore(session);
            }
        });
    }

    private static Map<String, Object> metadata(UserImportSession session, UserImportRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sessionId", session.getId().toString());
        value.put("fileName", session.getFileName());
        value.put("rowNumber", row.getRowNumber());
        return value;
    }

    private static String randomUnknownPassword() {
        byte[] bytes = new byte[PLACEHOLDER_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String displayName(String raw, String email) {
        String value = blankToNull(raw);
        if (value != null) return value;
        String localPart = email.substring(0, email.indexOf('@'));
        return localPart.length() <= 150 ? localPart : localPart.substring(0, 150);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
