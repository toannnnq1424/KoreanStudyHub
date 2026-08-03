package com.ksh.features.admin.settings;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSettingsInformationArchitectureStaticContractTest {
    private static final Path TEMPLATES = Path.of("src/main/resources/templates/admin");
    private static final Set<String> OFFICIAL_LINKS = Set.of(
            "https://platform.openai.com/api-keys",
            "https://aistudio.google.com/apikey",
            "https://platform.deepseek.com/api_keys",
            "https://www.alibabacloud.com/help/en/model-studio/get-api-key",
            "https://developers.cloudflare.com/r2/api/tokens/");

    @Test
    void overviewSeparatesGlobalAndPracticeScopesWithUnambiguousCards() throws Exception {
        String overview = read("settings.html");
        assertThat(overview)
                .contains("Toàn hệ thống", "Riêng cho Practice")
                .contains("AI toàn hệ thống", "Lưu trữ toàn hệ thống")
                .contains("AI cho Practice", "Lưu trữ Practice")
                .contains("Quản lý duy nhất GENERAL_UPLOADS")
                .contains("PRACTICE_AUTHORING", "PRACTICE_SPEAKING")
                .doesNotContain("<h3>Lưu trữ (Storage)</h3>", "<h3>Storage profiles</h3>");
        assertThat(Jsoup.parse(overview).select("section.settings-zone")).hasSize(2);
    }

    @Test
    void practiceAiIsSimpleFirstThreeStepCapabilityAwareControlPlane() throws Exception {
        String list = read("settings-practice-ai.html");
        String profile = read("settings-practice-ai-profile-form.html");
        String binding = read("settings-practice-ai-binding-form.html");
        assertThat(list)
                .contains("Chọn nhà cung cấp", "Chọn model", "Gán cho mục đích")
                .contains("enabledBindingCount == purposeCount", "purposeCount + ' đã bật'")
                .contains("Chưa thiết lập", "Sẵn sàng", "Tạm tắt", "Cần kiểm tra")
                .contains("không fallback sang AI toàn hệ thống")
                .doesNotContain("<table", ">ACTIVE<", ">DISABLED<", ">MISSING<");
        assertThat(profile).contains("Chi tiết kỹ thuật / Nâng cao", "********");
        assertThat(profile)
                .contains("PRACTICE_PRIMARY")
                .contains("Lưu và chọn model")
                .contains("#fields.hasAnyErrors()")
                .contains("#fields.hasErrors('profileCode')");
        assertThat(binding)
                .contains("Chi tiết kỹ thuật / Nâng cao")
                .contains("connectTimeoutMs", "readTimeoutMs", "maxRetries")
                .contains("maxRequestBytes", "maxResponseBytes", "retentionCode")
                .contains("directAudioInput", "regionEvidenceId",
                        "nonTrainingEvidenceId", "retentionEvidenceId",
                        "deletionSlaEvidenceId")
                .contains("Direct-audio chỉ chấp nhận hai cặp endpoint/model")
                .contains("không fallback global")
                .contains("th:href=\"@{/admin/settings/practice-ai/profiles/new}\"")
                .contains("Thêm nhà cung cấp")
                .contains("gpt-5.6-terra", "gpt-5.6-luna")
                .contains("gpt-5-nano", "gpt-5-mini")
                .contains("gpt-5.4-mini", "gpt-5.4-nano")
                .contains("gemini-3.6-flash", "gemini-3.5-flash-lite")
                .contains("gpt-4o-mini-transcribe", "gpt-4o-transcribe")
                .contains("tts-1", "tts-1-hd")
                .contains("deepseek-v4-flash", "deepseek-v4-pro")
                .contains("qwen3.6-flash", "qwen3.7-plus", "qwen3.7-max")
                .contains("OpenAI-compatible chưa đồng nghĩa tương thích Practice")
                .contains("Practice không hạ chuẩn strict schema")
                .contains("th:disabled=\"${#lists.isEmpty(profiles)}\"");
    }

    @Test
    void storagePagesExposeOnlyTheIdentityOwnedByTheirScope() throws Exception {
        String global = read("settings-storage.html");
        String practice = read("settings-storage-profiles.html");
        String controller = Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/settings/controller/StorageProfileController.java"));
        assertThat(global)
                .contains("GENERAL_UPLOADS", "/storage-profiles/GENERAL_UPLOADS/edit")
                .doesNotContain("PRACTICE_AUTHORING", "PRACTICE_SPEAKING", "testStorageBtn");
        assertThat(practice)
                .contains("PRACTICE_AUTHORING", "PRACTICE_SPEAKING")
                .contains("không fallback sang GENERAL_UPLOADS")
                .doesNotContain("th:each=\"profile : ${service.profiles")
                .doesNotContain("<table");
        assertThat(controller)
                .contains("PRACTICE_CODES", "StorageProfileCode.PRACTICE_AUTHORING")
                .contains("StorageProfileCode.PRACTICE_SPEAKING")
                .contains("StorageProfileCode.GENERAL_UPLOADS ? REDIRECT_GLOBAL");
    }

    @Test
    void externalCredentialLinksAreFixedOfficialAllowlistAndSafeNewTabs() throws Exception {
        List<String> files = List.of(
                "settings-ai-form.html",
                "settings-practice-ai-profile-form.html",
                "settings-storage.html",
                "settings-storage-profiles.html",
                "settings-storage-profile-form.html");
        for (String file : files) {
            Document document = Jsoup.parse(read(file));
            for (Element link : document.select("a[href^=https://]")) {
                assertThat(link.attr("href")).as(file).isIn(OFFICIAL_LINKS);
                assertThat(link.attr("target")).as(file).isEqualTo("_blank");
                assertThat(link.attr("rel")).as(file).contains("noopener", "noreferrer");
            }
            assertThat(read(file)).as(file)
                    .doesNotContain("th:href=\"${form.baseUrl", "th:href=\"${provider.baseUrl");
        }
    }

    @Test
    void routeCsrfAndSecretContractsRemainOwned() throws Exception {
        String practice = read("settings-practice-ai.html");
        String globalAi = read("settings-ai.html");
        String storage = read("settings-storage-profiles.html");
        String controllers = Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/settings/controller/PracticeAiControlPlaneController.java"))
                + Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/settings/controller/StorageProfileController.java"))
                + Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/settings/controller/StorageSettingsController.java"));
        for (String template : List.of(practice, globalAi, storage)) {
            Document document = Jsoup.parse(template);
            assertThat(document.select("form")).isNotEmpty();
            assertThat(document.select("form input[th:name='${_csrf.parameterName}']"))
                    .hasSameSizeAs(document.select("form"));
        }
        assertThat(controllers)
                .contains("/admin/settings/practice-ai", "/admin/settings/storage-profiles")
                .contains("@PostMapping(value = \"/test\"")
                .contains("@GetMapping(value = \"/{code}/secret\"")
                .contains("CacheControl.noStore()");
        assertThat(practice + storage).doesNotContain("th:text=\"${profile.credentialSecret}",
                "th:text=\"${profile.secretAccessKey}");
    }

    @Test
    void redesignedCssOwnsResponsiveOverflowFocusAndReducedMotion() throws Exception {
        String css = Files.readString(Path.of(
                "src/main/resources/static/css/admin-settings-hub.css"));
        assertThat(css)
                .contains("min-width: 0", "overflow-wrap: anywhere")
                .contains("@media (max-width: 1180px)", "@media (max-width: 900px)",
                        "@media (max-width: 620px)")
                .contains(":focus-visible", "prefers-reduced-motion")
                .contains("grid-template-columns: 1fr")
                .contains("input:not([type=\"checkbox\"]):not([type=\"hidden\"])")
                .contains("min-height: 44px", "border-radius: 10px")
                .contains("select:disabled", "cursor: not-allowed")
                .contains(".settings-body .class-detail-layout {",
                        "grid-template-columns: minmax(0, 1fr)")
                .contains(".settings-body .class-detail-layout > aside { display: none; }")
                .contains(".settings-official-links > div { flex: none; }");
    }

    @Test
    void reportedFormEditorAndImportRegressionsHaveExplicitOwners() throws Exception {
        String css = Files.readString(Path.of(
                "src/main/resources/static/css/admin-settings-hub.css"));
        String prompt = read("settings-ai-prompts.html");
        String binding = read("settings-practice-ai-binding-form.html");
        String profile = read("settings-practice-ai-profile-form.html");
        String practiceAiJs = Files.readString(Path.of(
                "src/main/resources/static/js/admin-settings-practice-ai.js"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/settings/controller/"
                        + "PracticeAiControlPlaneController.java"));
        String dto = Files.readString(Path.of(
                "src/main/java/com/ksh/features/admin/settings/dto/"
                        + "PracticeAiSettingsDtos.java"));
        String editor = Files.readString(Path.of(
                "src/main/resources/templates/practice/manage/editor.html"));
        String editorCss = Files.readString(Path.of(
                "src/main/resources/static/css/practice/manage-editor.css"));
        String preview = Files.readString(Path.of(
                "src/main/resources/templates/practice/manage/fragments/"
                        + "draft-preview.html"));
        String candidate = Files.readString(Path.of(
                "src/main/resources/static/js/practice/candidate-review.js"));
        String excel = Files.readString(Path.of(
                "src/main/resources/templates/practice/manage/excel-import.html"));

        assertThat(prompt)
                .contains("admin-settings-hub.css", "settings-prompt-textarea")
                .contains("settings-body", "settings-page");
        assertThat(css)
                .contains(".settings-prompt-panel .settings-prompt-textarea")
                .contains("min-height: 240px", "resize: vertical")
                .contains(".settings-model-suggestions");
        assertThat(dto).contains(
                "PRACTICE_PRIMARY", "Vui lòng nhập tên dễ nhận biết",
                "withProviderProfileId");
        assertThat(controller)
                .contains("/edit?profileId=", "@RequestParam(required = false) Long profileId")
                .contains("Đã lưu nhà cung cấp. Tiếp theo")
                .contains("isDirectAudioProfile(form)");
        assertThat(profile).contains("Lưu và chọn model", "Chưa thể lưu nhà cung cấp");
        assertThat(profile).contains("Các dịch vụ dưới đây có endpoint OpenAI-compatible")
                .contains("Practice vẫn kiểm tra strict schema và audio riêng");
        assertThat(binding).contains("data-base-url=${profile.baseUrl}", "model-suggestions");
        assertThat(binding)
                .contains("data-settings-combobox", "role=\"listbox\"", "role=\"option\"")
                .contains("class=\"settings-combobox-chevron\"", "viewBox=\"0 0 24 24\"")
                .doesNotContain(">⌄</span>")
                .contains("settings-combobox-native", "eleven_multilingual_v2")
                .contains("cần adapter riêng")
                .contains("aria-autocomplete=\"list\"", "data-model-count", "data-model-empty")
                .contains("toàn bộ model đã xác minh phù hợp mục đích")
                .contains("Trang không gọi API provider")
                .contains("id=\"providerProfileId\"", "hidden required");
        assertThat(practiceAiJs)
                .contains("parsed.hostname === 'api.openai.com'")
                .contains("parsed.hostname === 'generativelanguage.googleapis.com'")
                .contains("data-suggestion-provider")
                .contains("openProviderCombobox", "chooseProvider", "ArrowDown", "Escape")
                .contains("aria-selected", "providerSelect.dispatchEvent")
                .contains("normalizeModelSearch", "filterModelSuggestions")
                .contains("openModelSuggestions", "visibleModelOptions")
                .contains("parsed.hostname === 'api.deepseek.com'")
                .contains("parsed.hostname === 'dashscope-intl.aliyuncs.com'")
                .contains("path === '/compatible-mode/v1'");
        assertThat(profile)
                .contains("GOOGLE_CLOUD_ADC", "không lưu secret")
                .contains("id=\"credentialMode\"");
        assertThat(practiceAiJs)
                .contains("syncCredentialMode", "credentialSecret.disabled = adc")
                .contains("credentialSecret.value = ''");

        assertThat(editor)
                .contains("pi-body practice-editor-body")
                .contains("markValidationPanelPointerActivation()")
                .contains("validationPanelPointerActivation || event?.detail > 0")
                .contains("window.returnFromPreviewToEditor");
        assertThat(editorCss)
                .contains("body.practice-editor-body", "@media (max-width: 720px)")
                .contains("overscroll-behavior: contain", "scrollbar-gutter: stable")
                .doesNotContain("html {\n  height: 100%;\n  overflow: hidden;");
        assertThat(preview).contains("Chỉnh sửa nội dung", "returnFromPreviewToEditor()");
        assertThat(candidate).contains("window.returnFromPreviewToEditor");
        assertThat(excel)
                .contains("download=\"ksh-practice-quick-v1.xlsx\"")
                .contains("overscroll-behavior:contain", "scrollbar-gutter:stable")
                .contains("#excel-file::file-selector-button");
        String pdfController = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/controller/"
                        + "PracticePdfImportApiController.java"));
        String pdfImport = Files.readString(Path.of(
                "src/main/resources/templates/practice/manage/import-wizard.html"));
        assertThat(pdfController)
                .contains("PRACTICE_PDF_AUTHORING_UNAVAILABLE", "causeCode")
                .contains("PDF không bị chặn");
        assertThat(pdfImport)
                .contains("payload.code === 'PRACTICE_PDF_AUTHORING_UNAVAILABLE'")
                .contains("PDF không bị cấm", "liên hệ quản trị viên");
    }

    @Test
    void everyInteractiveSelectorAndLoadedStaticAssetHasAnOwner() throws Exception {
        String templates = read("settings-ai.html")
                + read("settings-practice-ai.html")
                + read("settings-practice-ai-profile-form.html")
                + read("settings-storage-profile-form.html");
        String scripts = Files.readString(Path.of("src/main/resources/static/js/admin-settings-ai.js"))
                + Files.readString(Path.of("src/main/resources/static/js/admin-settings-practice-ai.js"))
                + Files.readString(Path.of("src/main/resources/static/js/admin-settings-storage-profiles.js"));
        for (String selector : List.of("js-ai-reveal", "js-ai-copy", "js-ai-test",
                "js-ai-delete", "js-practice-ai-reveal", "js-practice-ai-test",
                "js-storage-profile-reveal")) {
            assertThat(templates).as(selector).contains(selector);
            assertThat(scripts).as(selector).contains("." + selector);
        }
        assertThat(Files.exists(Path.of(
                "src/main/resources/static/js/admin-settings-storage.js"))).isFalse();
    }

    @Test
    void practiceEditorStructureMenuHasOneClickOwnerForExcelAndPdfEntryFlow() throws Exception {
        String editor = Files.readString(Path.of(
                "src/main/resources/templates/practice/manage/editor.html"));
        assertThat(editor)
                .contains("id=\"add-structure-trigger\"")
                .contains("data-action=\"toggle-add-menu\"")
                .contains("const toggleBtn = e.target.closest('[data-action=\"toggle-add-menu\"]')")
                .doesNotContain("data-action=\"toggle-add-menu\" onclick=\"toggleAddMenu(event)\"");
    }

    private static String read(String file) throws Exception {
        return Files.readString(TEMPLATES.resolve(file));
    }
}
