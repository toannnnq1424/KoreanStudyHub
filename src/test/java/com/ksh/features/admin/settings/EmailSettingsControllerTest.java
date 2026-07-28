package com.ksh.features.admin.settings;

import com.ksh.features.admin.settings.controller.EmailSettingsController;
import com.ksh.features.admin.settings.dto.EmailSettingsDtos.EmailSettingsForm;
import com.ksh.features.admin.settings.service.EmailSettingsService;
import com.ksh.features.mail.outbox.MailOutboxOperationalSnapshot;
import com.ksh.features.mail.outbox.MailOutboxOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailSettingsControllerTest {

    @Test
    void email_settings_page_keeps_admin_permission_and_adds_non_pii_outbox_read_model() {
        EmailSettingsService settings = mock(EmailSettingsService.class);
        MailOutboxOperationsService operations =
                mock(MailOutboxOperationsService.class);
        EmailSettingsForm form = new EmailSettingsForm(
                "smtp.example.com",
                587,
                "tls",
                "mailer",
                "********",
                "KSH",
                "noreply@example.com",
                "");
        MailOutboxOperationalSnapshot snapshot =
                new MailOutboxOperationalSnapshot(
                        LocalDateTime.of(2026, 7, 29, 8, 0),
                        1,
                        2,
                        3,
                        4,
                        5,
                        6,
                        7,
                        120);
        when(settings.load()).thenReturn(form);
        when(operations.snapshot()).thenReturn(snapshot);
        EmailSettingsController controller =
                new EmailSettingsController(settings, operations);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.view(null, model);

        assertThat(view).isEqualTo("admin/settings-email");
        assertThat(model.get("form")).isSameAs(form);
        assertThat(model.get("mailOutboxSnapshot")).isSameAs(snapshot);
        assertThat(model.get("defaultTestRecipient")).isEqualTo("");
        PreAuthorize authorization =
                EmailSettingsController.class.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
                .isEqualTo("hasAuthority('PERM_system.smtp')");
        assertThat(snapshot.toString())
                .doesNotContain("@", "recipient", "subject", "body");
    }

    @Test
    void smtp_page_remains_available_when_snapshot_query_fails() {
        EmailSettingsService settings = mock(EmailSettingsService.class);
        MailOutboxOperationsService operations =
                mock(MailOutboxOperationsService.class);
        when(settings.load()).thenReturn(mock(EmailSettingsForm.class));
        when(operations.snapshot())
                .thenThrow(new IllegalStateException("database unavailable"));
        EmailSettingsController controller =
                new EmailSettingsController(settings, operations);
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.view(null, model))
                .isEqualTo("admin/settings-email");
        assertThat(model.get("mailOutboxSnapshotError")).isEqualTo(true);
    }
}
