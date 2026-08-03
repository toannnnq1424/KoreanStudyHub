package com.ksh.features.admin.settings;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAiFixedProviderPresetStaticContractTest {

    private static final Path TEMPLATES = Path.of(
            "src/main/resources/templates/admin");
    private static final Set<String> PRESET_KEY_LINKS = Set.of(
            "https://console.x.ai/team/default/api-keys",
            "https://console.groq.com/keys");

    @Test
    void templatesExposeTwoFixedCheckStateProfilesWithSafeOfficialLinks()
            throws Exception {
        String list = Files.readString(TEMPLATES.resolve(
                "settings-practice-ai.html"));
        String form = Files.readString(TEMPLATES.resolve(
                "settings-practice-ai-profile-form.html"));
        Document document = Jsoup.parse(list + form);

        assertThat(list)
                .contains("XAI_GROK", "GROQ")
                .contains("Cần kiểm tra", "Tạo profile tắt")
                .contains("th:name=\"${_csrf.parameterName}\"")
                .contains("không có fallback ngầm")
                .doesNotContain("fetch('/models", "fetch(\"/models", "grok-4", "llama-");
        assertThat(form)
                .contains("Secret của preset cố định không có API hiển thị lại")
                .contains("fixedPreset == null")
                .doesNotContain("th:text=\"*{credentialSecret}\"");

        for (Element link : document.select("a[href^=https://]")) {
            if (!PRESET_KEY_LINKS.contains(link.attr("href"))) {
                continue;
            }
            assertThat(link.attr("target")).isEqualTo("_blank");
            assertThat(link.attr("rel")).contains("noopener", "noreferrer");
        }
        assertThat(document.select(
                "a[href='https://console.x.ai/team/default/api-keys']"))
                .hasSize(2);
        assertThat(document.select(
                "a[href='https://console.groq.com/keys']"))
                .hasSize(2);
    }

    @Test
    void sourceKeepsPresetOutsideBindingTransportAndGlobalFallback() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/controlplane/"
                        + "PracticeAiFixedProviderPresetRegistry.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/settings/controller/"
                        + "PracticeAiControlPlaneController.java"));
        String service = Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/settings/service/"
                        + "PracticeAiControlPlaneAdminService.java"));

        assertThat(registry)
                .contains("https://api.x.ai/v1")
                .contains("https://api.groq.com/openai/v1")
                .doesNotContain("modelId", "AiProviderRepository", "/models");
        assertThat(controller)
                .contains("@PostMapping(\"/profiles/presets/{presetKey}\")")
                .contains("createFixedProviderPreset")
                .doesNotContain("RestClient", "AiProviderRepository");
        assertThat(service)
                .contains("PRACTICE_AI_PROVIDER_PRESET_VERIFICATION_REQUIRED")
                .contains("fixedPreset.isEmpty() && form.enabled()")
                .doesNotContain("findEnabledOrdered(");
        String resolver = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/ai/controlplane/"
                        + "PracticeAiBindingResolver.java"));
        assertThat(resolver)
                .contains("PRACTICE_AI_PROVIDER_PRESET_VERIFICATION_REQUIRED")
                .contains("findByProfileCode(profile.getProfileCode())");
    }
}
