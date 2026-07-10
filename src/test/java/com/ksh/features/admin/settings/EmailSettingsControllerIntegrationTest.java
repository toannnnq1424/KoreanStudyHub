package com.ksh.features.admin.settings;

import com.ksh.config.CacheConfig;
import com.ksh.features.admin.settings.dto.EmailSettingsDtos;
import com.ksh.entities.SystemSetting;
import com.ksh.features.admin.settings.repository.SystemSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test cho {@code /admin/settings/email} — chay context day du
 * + DB that. Cover:
 * <ul>
 *   <li>Auth guards (anonymous, STUDENT, LECTURER, HEAD, ADMIN)</li>
 *   <li>Form render voi masked password</li>
 *   <li>Save valid settings — redirect voi success flash</li>
 *   <li>Save invalid encryption — re-render form voi field error</li>
 *   <li>Save empty password — gia tri cu duoc giu nguyen</li>
 *   <li>Test send invalid recipient — JSON error</li>
 *   <li>Test send empty host — JSON error voi message cu the</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailSettingsControllerIntegrationTest {

    private static final String MASKED = EmailSettingsDtos.MASKED;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemSettingsRepository repository;

    @Autowired
    private CacheManager cacheManager;

    /** Backup gia tri toan bo group SMTP de restore sau moi test. */
    private Map<String, String> backupSmtpRows;

    @BeforeEach
    void setUp() {
        evictSmtpCache();
        backupSmtpRows = new java.util.HashMap<>();
        for (SystemSetting s : repository.findBySettingGroup("SMTP")) {
            backupSmtpRows.put(s.getSettingKey(), s.getSettingValue());
        }
    }

    @AfterEach
    void tearDown() {
        // Restore tat ca SMTP row de cac test khac khong bi anh huong
        for (Map.Entry<String, String> entry : backupSmtpRows.entrySet()) {
            repository.findBySettingKey(entry.getKey()).ifPresent(s -> {
                s.setSettingValue(entry.getValue());
                repository.save(s);
            });
        }
        evictSmtpCache();
    }

    // ─────────────────── Auth guards ───────────────────

    @Test
    @WithAnonymousUser
    void anonymous_redirects_to_login() throws Exception {
        mockMvc.perform(get("/admin/settings/email"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_forbidden() throws Exception {
        mockMvc.perform(get("/admin/settings/email"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void lecturer_forbidden() throws Exception {
        mockMvc.perform(get("/admin/settings/email"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("head@ksh.edu.vn")
    void head_forbidden() throws Exception {
        mockMvc.perform(get("/admin/settings/email"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────── Form render ───────────────────

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void admin_form_renders_with_masked_password() throws Exception {
        // Set a known password so test khong phu thuoc vao seed
        repository.findBySettingKey("smtp.password").ifPresent(s -> {
            s.setSettingValue("real-secret-do-not-leak");
            repository.save(s);
        });

        mockMvc.perform(get("/admin/settings/email"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(MASKED)))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("real-secret-do-not-leak"))))
                .andExpect(content().string(containsString("Cấu hình Email")));
    }

    // ─────────────────── Save (POST) ───────────────────

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_valid_settings_redirects_with_success() throws Exception {
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "587")
                        .param("encryption", "tls")
                        .param("username", "noreply@example.com")
                        .param("password", "new-secret")
                        .param("fromName", "ksh Test")
                        .param("fromEmail", "noreply@example.com")
                        .param("replyTo", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings/email"));

        assertThat(repository.findBySettingKey("smtp.host"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo("smtp.example.com");
        assertThat(repository.findBySettingKey("smtp.password"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo("new-secret");
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_invalid_encryption_renders_form_with_error() throws Exception {
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "587")
                        .param("encryption", "bogus")  // not in {none,tls,ssl}
                        .param("username", "u")
                        .param("password", "")
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cấu hình Email")));

        // Verify khong co row nao bi update — host phai con la gia tri seed
        assertThat(repository.findBySettingKey("smtp.host"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo(backupSmtpRows.get("smtp.host"));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_empty_password_preserves_existing_value() throws Exception {
        // Set up known password
        repository.findBySettingKey("smtp.password").ifPresent(s -> {
            s.setSettingValue("keep-this-secret");
            repository.save(s);
        });

        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "465")
                        .param("encryption", "ssl")
                        .param("username", "u")
                        .param("password", "") // empty — should keep
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(repository.findBySettingKey("smtp.password"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo("keep-this-secret");
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_masked_placeholder_also_preserves_password() throws Exception {
        repository.findBySettingKey("smtp.password").ifPresent(s -> {
            s.setSettingValue("keep-this-secret-too");
            repository.save(s);
        });

        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "587")
                        .param("encryption", "tls")
                        .param("username", "u")
                        .param("password", MASKED) // masked placeholder verbatim
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(repository.findBySettingKey("smtp.password"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo("keep-this-secret-too");
    }

    // ─────────────────── Validation boundary ───────────────────

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_empty_host_renders_form_with_error() throws Exception {
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "")
                        .param("port", "587")
                        .param("encryption", "tls")
                        .param("username", "u")
                        .param("password", "")
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Host là bắt buộc")));

        assertThat(repository.findBySettingKey("smtp.host"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo(backupSmtpRows.get("smtp.host"));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_port_zero_renders_form_with_error() throws Exception {
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "0")
                        .param("encryption", "tls")
                        .param("username", "u")
                        .param("password", "")
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Port phải từ 1 đến 65535")));

        assertThat(repository.findBySettingKey("smtp.host"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo(backupSmtpRows.get("smtp.host"));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_port_out_of_range_renders_form_with_error() throws Exception {
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "65536")
                        .param("encryption", "tls")
                        .param("username", "u")
                        .param("password", "")
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Port phải từ 1 đến 65535")));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_port_non_numeric_returns_type_mismatch_error() throws Exception {
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "abc")
                        .param("encryption", "tls")
                        .param("username", "u")
                        .param("password", "")
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", ""))
                .andExpect(status().isOk());
        // Khong update DB
        assertThat(repository.findBySettingKey("smtp.host"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo(backupSmtpRows.get("smtp.host"));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_invalid_from_email_renders_form_with_error() throws Exception {
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "587")
                        .param("encryption", "tls")
                        .param("username", "u")
                        .param("password", "")
                        .param("fromName", "ksh")
                        .param("fromEmail", "not-an-email")
                        .param("replyTo", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("From Email không hợp lệ")));

        assertThat(repository.findBySettingKey("smtp.host"))
                .get().extracting(SystemSetting::getSettingValue)
                .isEqualTo(backupSmtpRows.get("smtp.host"));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_invalid_reply_to_renders_form_with_error() throws Exception {
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "587")
                        .param("encryption", "tls")
                        .param("username", "u")
                        .param("password", "")
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reply-To không hợp lệ")));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void save_empty_reply_to_is_accepted() throws Exception {
        // reply_to la optional — empty phai pass validation
        mockMvc.perform(post("/admin/settings/email")
                        .with(csrf())
                        .param("host", "smtp.example.com")
                        .param("port", "587")
                        .param("encryption", "tls")
                        .param("username", "u")
                        .param("password", "")
                        .param("fromName", "ksh")
                        .param("fromEmail", "from@example.com")
                        .param("replyTo", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings/email"));
    }

    // ─────────────────── Test send ───────────────────

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void test_send_invalid_recipient_returns_json_error() throws Exception {
        mockMvc.perform(post("/admin/settings/email/test")
                        .with(csrf())
                        .param("testRecipient", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value(containsString("không hợp lệ")));
    }

    @Test
    @WithUserDetails("admin@ksh.edu.vn")
    void test_send_empty_host_returns_specific_error() throws Exception {
        // Force smtp.host empty
        repository.findBySettingKey("smtp.host").ifPresent(s -> {
            s.setSettingValue("");
            repository.saveAndFlush(s);
        });
        evictSmtpCache();

        mockMvc.perform(post("/admin/settings/email/test")
                        .with(csrf())
                        .param("testRecipient", "valid@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("SMTP host is not configured"));
    }

    private void evictSmtpCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConfig.CACHE_SETTINGS_GROUP);
        if (cache != null) {
            cache.evict("SMTP");
        }
    }
}
