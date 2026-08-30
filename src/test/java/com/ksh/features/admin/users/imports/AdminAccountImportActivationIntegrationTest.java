package com.ksh.features.admin.users.imports;

import com.ksh.entities.AccountActivationToken;
import com.ksh.entities.User;
import com.ksh.entities.UserActivity;
import com.ksh.entities.UserFactory;
import com.ksh.features.admin.users.imports.service.ActivationMailComposer;
import com.ksh.features.admin.users.imports.service.UserRosterImportService;
import com.ksh.features.admin.users.repository.UserActivityRepository;
import com.ksh.features.auth.repository.AccountActivationTokenRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.auth.service.AccountActivationService;
import com.ksh.features.mail.outbox.MailOutboxJob;
import com.ksh.features.mail.outbox.MailOutboxRepository;
import com.ksh.features.mail.outbox.MailOutboxStatus;
import com.ksh.security.Role;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MySQL/Flyway proof for preview -> confirm -> durable email -> owner activation. */
@SpringBootTest(properties = {
        "app.mail.outbox.worker-enabled=false",
        "app.mail.outbox.retention.enabled=false",
        "spring.task.scheduling.enabled=false",
        "app.base-url=https://ksh.example.test"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminAccountImportActivationIntegrationTest {

    private static final Pattern RAW_TOKEN =
            Pattern.compile("https://ksh\\.example\\.test/activate\\?token=([A-Za-z0-9_-]+)");

    @Autowired private UserRosterImportService importService;
    @Autowired private AccountActivationService activationService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountActivationTokenRepository tokenRepository;
    @Autowired private MailOutboxRepository outboxRepository;
    @Autowired private UserActivityRepository activityRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void importCreatesPendingAccountAndDurableMailThenOwnerActivationEnablesLogin() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String adminEmail = "import-admin-" + suffix + "@ksh.test";
        String studentEmail = "import-student-" + suffix + "@ksh.test";
        User admin = userRepository.saveAndFlush(UserFactory.newAdminCreated(
                adminEmail, passwordEncoder.encode("admin-password"), "Import Admin",
                Role.ADMIN, true, null, null));
        Long adminId = admin.getId();
        Long studentId = null;
        Long outboxId = null;

        try {
            var preview = importService.previewUpload(workbook(studentEmail), adminId);
            assertThat(preview.totalRows()).isEqualTo(1);
            assertThat(preview.creatableCount()).isEqualTo(1);

            var result = importService.confirmImport(preview.getId(), adminId);
            assertThat(result.totalProcessed()).isEqualTo(1);
            assertThat(result.created()).isEqualTo(1);
            assertThat(result.alreadyExisted()).isZero();
            assertThat(result.errors()).isZero();
            assertThatThrownBy(() -> importService.confirmImport(preview.getId(), adminId))
                    .isInstanceOf(InvalidRosterFileException.class)
                    .hasMessageContaining("đã hết hạn, đã được dùng");

            User pending = userRepository.findByEmailIgnoreCase(studentEmail).orElseThrow();
            studentId = pending.getId();
            Long importedUserId = pending.getId();
            assertThat(pending.isActive()).isFalse();
            assertThat(pending.isEmailVerified()).isFalse();
            assertThat(pending.isPendingActivation()).isTrue();
            assertThat(pending.getRole()).isEqualTo(Role.STUDENT);
            assertThat(userRepository.searchUsersForAdmin(
                    studentEmail, null, "PENDING", PageRequest.of(0, 20)).getContent())
                    .extracting(row -> row.getId())
                    .contains(importedUserId);
            assertThat(userRepository.searchUsersForAdmin(
                    studentEmail, null, "INACTIVE", PageRequest.of(0, 20)).getContent())
                    .extracting(row -> row.getId())
                    .doesNotContain(importedUserId);

            List<AccountActivationToken> tokens = tokenRepository.findAll().stream()
                    .filter(token -> importedUserId.equals(token.getUser().getId()))
                    .toList();
            assertThat(tokens).singleElement().satisfies(token ->
                    assertThat(token.getTokenDigest()).matches("[0-9a-f]{64}"));

            MailOutboxJob activationMail = outboxRepository.findAll().stream()
                    .filter(job -> studentEmail.equals(job.getRecipientEmail()))
                    .filter(job -> ActivationMailComposer.MAIL_SOURCE.equals(job.getSource()))
                    .findFirst().orElseThrow();
            outboxId = activationMail.getId();
            assertThat(activationMail.getNotificationId()).isNull();
            assertThat(activationMail.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
            Matcher matcher = RAW_TOKEN.matcher(activationMail.getBody());
            assertThat(matcher.find()).isTrue();
            String rawToken = matcher.group(1);
            assertThat(tokens.get(0).getTokenDigest())
                    .isNotEqualTo(rawToken)
                    .isEqualTo(sha256(rawToken));

            assertThat(activationService.activate(rawToken, "owner-password")).isTrue();
            User activated = userRepository.findById(studentId).orElseThrow();
            assertThat(activated.isActive()).isTrue();
            assertThat(activated.isEmailVerified()).isTrue();
            assertThat(activated.isPendingActivation()).isFalse();
            assertThat(passwordEncoder.matches("owner-password", activated.getPasswordHash())).isTrue();
            assertThat(userRepository.searchUsersForAdmin(
                    studentEmail, null, "ACTIVE", PageRequest.of(0, 20)).getContent())
                    .extracting(row -> row.getId())
                    .contains(importedUserId);
            assertThat(activationService.activate(rawToken, "another-password")).isFalse();

            Long finalStudentId = studentId;
            assertThat(activityRepository.findAll().stream()
                    .filter(activity -> finalStudentId.equals(activity.getTargetUserId()))
                    .map(UserActivity::getType))
                    .contains(UserActivity.TYPE_IMPORTED,
                            UserActivity.TYPE_ACTIVATION_SENT,
                            UserActivity.TYPE_SELF_ACTIVATED);
        } finally {
            if (outboxId != null) outboxRepository.deleteById(outboxId);
            outboxRepository.flush();
            if (studentId != null) userRepository.deleteById(studentId);
            userRepository.deleteById(adminId);
            userRepository.flush();
        }
    }

    private static MockMultipartFile workbook(String email) throws Exception {
        byte[] bytes;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Accounts");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Email");
            header.createCell(1).setCellValue("Họ và tên");
            header.createCell(2).setCellValue("Vai trò");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(email);
            row.createCell(1).setCellValue("Minji Kim");
            row.createCell(2).setCellValue("STUDENT");
            book.write(out);
            bytes = out.toByteArray();
        }
        return new MockMultipartFile("file", "accounts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
