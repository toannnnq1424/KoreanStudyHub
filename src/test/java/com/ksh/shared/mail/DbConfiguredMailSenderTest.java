package com.ksh.shared.mail;

import com.ksh.shared.settings.SystemSettingGroups;
import com.ksh.shared.settings.repository.SystemSettingsRepository;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DbConfiguredMailSender}.
 *
 * <p>Covers:
 * <ul>
 *   <li>send() returns false when host is empty (no network call)</li>
 *   <li>sendWithDetail() returns specific error message when host is empty</li>
 *   <li>From header uses display name when smtp.from_name is set</li>
 *   <li>From header uses bare email when smtp.from_name is blank</li>
 *   <li>Reply-To header is set when smtp.reply_to is configured</li>
 * </ul>
 *
 * <p>Tests do NOT send real email — they build a {@link MimeMessage} in memory
 * and inspect its headers via reflection on the private {@code buildMessage} method.
 */
@ExtendWith(MockitoExtension.class)
class DbConfiguredMailSenderTest {

    @Mock
    private SystemSettingsRepository repository;

    @InjectMocks
    private DbConfiguredMailSender sender;

    // ─────────────────── Empty-host short-circuit ───────────────────

    @Test
    void send_with_empty_host_returns_false_without_throwing() {
        when(repository.loadGroupAsMap(SystemSettingGroups.SMTP)).thenReturn(Map.of(
                "smtp.host", "",
                "smtp.from_email", "from@example.com"
        ));
        boolean ok = sender.send("to@example.com", "subj", "body");
        assertThat(ok).isFalse();
    }

    @Test
    void sendWithDetail_with_empty_host_returns_specific_error() {
        when(repository.loadGroupAsMap(SystemSettingGroups.SMTP)).thenReturn(Map.of(
                "smtp.host", ""
        ));
        MailSendResult result = sender.sendWithDetail("to@example.com", "s", "b");
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("SMTP host is not configured");
    }

    // ─────────────────── MimeMessage header assertions ───────────────────

    @Test
    void from_header_uses_display_name_when_from_name_is_set() throws Exception {
        MimeMessage message = buildTestMessage(Map.of(
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.encryption", "tls",
                "smtp.username", "u",
                "smtp.password", "p",
                "smtp.from_name", "KSH Team",
                "smtp.from_email", "noreply@ksh.edu.vn",
                "smtp.reply_to", ""
        ));

        Address[] from = message.getFrom();
        assertThat(from).hasSize(1);
        InternetAddress addr = (InternetAddress) from[0];
        assertThat(addr.getAddress()).isEqualTo("noreply@ksh.edu.vn");
        assertThat(addr.getPersonal()).isEqualTo("KSH Team");
    }

    @Test
    void from_header_uses_bare_email_when_from_name_is_blank() throws Exception {
        MimeMessage message = buildTestMessage(Map.of(
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.encryption", "tls",
                "smtp.username", "u",
                "smtp.password", "p",
                "smtp.from_name", "",   // empty — no personal name
                "smtp.from_email", "noreply@ksh.edu.vn",
                "smtp.reply_to", ""
        ));

        Address[] from = message.getFrom();
        assertThat(from).hasSize(1);
        InternetAddress addr = (InternetAddress) from[0];
        assertThat(addr.getAddress()).isEqualTo("noreply@ksh.edu.vn");
        assertThat(addr.getPersonal()).isNull();
    }

    @Test
    void reply_to_is_set_when_configured() throws Exception {
        MimeMessage message = buildTestMessage(Map.of(
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.encryption", "tls",
                "smtp.username", "u",
                "smtp.password", "p",
                "smtp.from_name", "KSH",
                "smtp.from_email", "noreply@ksh.edu.vn",
                "smtp.reply_to", "support@ksh.edu.vn"
        ));

        Address[] replyTo = message.getReplyTo();
        assertThat(replyTo).isNotEmpty();
        assertThat(((InternetAddress) replyTo[0]).getAddress())
                .isEqualTo("support@ksh.edu.vn");
    }

    // ─────────────────── Helper ───────────────────

    /**
     * Invokes the private {@code buildMessage} method via reflection to
     * inspect MimeMessage headers without making a real SMTP connection.
     * Acceptable for boundary testing of header-composition logic.
     */
    private MimeMessage buildTestMessage(Map<String, String> cfg) throws Exception {
        // Build a minimal JavaMailSenderImpl — only used to create a MimeMessage session.
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(cfg.get("smtp.host"));
        impl.setPort(Integer.parseInt(cfg.getOrDefault("smtp.port", "587")));

        Method buildMessage = DbConfiguredMailSender.class.getDeclaredMethod(
                "buildMessage",
                JavaMailSenderImpl.class, Map.class,
                String.class, String.class, String.class);
        buildMessage.setAccessible(true);

        return (MimeMessage) buildMessage.invoke(
                sender, impl, new HashMap<>(cfg),
                "to@example.com", "Subject", "Body");
    }
}
