package com.ksh.features.practice.preferences;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeKoreanFontUiContractTest {

    private static final Path HEAD = Path.of(
            "src/main/resources/templates/fragments/head.html");
    private static final Path SIDEBAR = Path.of(
            "src/main/resources/templates/fragments/practice-sidebar.html");
    private static final Path SETTINGS = Path.of(
            "src/main/resources/templates/practice/preferences.html");
    private static final Path CATALOG = Path.of(
            "src/main/resources/templates/practice/index.html");
    private static final Path CSS = Path.of(
            "src/main/resources/static/css/practice-korean-font.css");
    private static final Path JS = Path.of(
            "src/main/resources/static/js/practice-korean-font.js");
    private static final Path FONT_DIR = Path.of(
            "src/main/resources/static/fonts/practice-korean");
    private static final List<Path> PLAYERS = List.of(
            Path.of("src/main/resources/templates/practice/player.html"),
            Path.of("src/main/resources/templates/practice/player-writing.html"),
            Path.of("src/main/resources/templates/practice/player-speaking.html"));

    @Test
    void settingsExposeCompleteServerOwnedKoreanCatalogAndThreeAccessibleSizes()
            throws Exception {
        String settings = Files.readString(SETTINGS);
        String sidebar = Files.readString(SIDEBAR);
        String catalog = Files.readString(CATALOG);
        String enumSource = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/preferences/"
                        + "PracticeKoreanFont.java"));

        assertThat(settings).contains(
                "data-practice-korean-font-form",
                "name=\"koreanFont\"",
                "name=\"koreanFontSize\"",
                "name=\"schemaVersion\"",
                "data-practice-korean-content",
                "pkf-choice-sample",
                "categoryLabel()",
                "description()",
                "lang=\"ko\"",
                "hasRole('LECTURER')",
                "th:href=\"@{/practice/manage}\"",
                "Quay lại quản lý bộ đề",
                "aria-live=\"polite\"");
        assertThat(settings.indexOf("pkf-preview-top"))
                .as("the current selection preview precedes the long font catalog")
                .isLessThan(settings.indexOf("pkf-choice-grid"));
        assertThat(settings).contains(
                "/css/practice-index.css",
                "Cỡ chữ nội dung Hàn",
                "Giao diện tiếng Việt không thay đổi");
        assertThat(sidebar).contains(
                "hasAnyRole('STUDENT','LECTURER')",
                "th:href=\"@{/practice/preferences}\"",
                "Kiểu chữ Hàn");
        assertThat(catalog).contains(
                "sec:authorize=\"hasRole('STUDENT')\"",
                "th:href=\"@{/practice/preferences}\"",
                "Chọn kiểu chữ cho nội dung học tiếng Hàn");
        assertThat(enumSource).contains(
                "NANUM_MYEONGJO",
                "DIPHYLLEIA",
                "GOWUN_BATANG",
                "NOTO_SERIF_KR",
                "NANUM_GOTHIC",
                "GOTHIC_A1",
                "GOWUN_DODUM",
                "ORBIT",
                "GAEGU",
                "SUNFLOWER",
                "GUGI",
                "NANUM_PEN_SCRIPT");
        assertThat(PracticeKoreanFont.ALLOWED).hasSize(17);
    }

    @Test
    void loadLastStyleAndVersionedAccountCacheKeepServerPrecedence()
            throws Exception {
        String head = Files.readString(HEAD);
        String script = Files.readString(JS);

        assertThat(head.indexOf("<th:block th:replace=\"${extraCss}\">"))
                .isLessThan(head.indexOf("/css/practice-korean-font.css"));
        assertThat(head).contains(
                "practice-korean-font-account",
                "practice-korean-font-size",
                "practice-korean-font-schema",
                "practice-korean-font-preference-v2",
                "Canonical server preference always overrides");
        assertThat(script).contains(
                "practice-korean-font-preference-v2",
                "localStorage.setItem",
                "${schemaVersion}|${serverFont}|${serverSize}",
                "document.documentElement.dataset.practiceKoreanFont",
                "document.documentElement.dataset.practiceKoreanSize");
        assertThat(script).doesNotContain(
                "JSON.parse",
                "JSON.stringify",
                "innerHTML",
                "getBoundingClientRect",
                "offsetWidth",
                "compositionstart",
                "compositionend");
    }

    @Test
    void fontDeliveryIsSelfHostedOfLAndScopedToMarkedKoreanLearningContent()
            throws Exception {
        String css = Files.readString(CSS);

        assertThat(css).contains(
                ":root[data-practice-korean-font=\"NANUM_MYEONGJO\"]",
                ":root[data-practice-korean-font=\"NANUM_PEN_SCRIPT\"]",
                ".pkf-preview-top",
                "position: sticky",
                "gap: 18px",
                "data-practice-korean-size=\"LARGE\"",
                "data-practice-korean-size=\"EXTRA_LARGE\"",
                "[lang=\"ko\"]",
                "@font-face",
                "local(\"Nanum Myeongjo\")",
                "/fonts/practice-korean/nanum-myeongjo-regular.ttf",
                "/fonts/practice-korean/diphylleia-regular.ttf",
                "/fonts/practice-korean/gowun-batang-regular.ttf",
                "/fonts/practice-korean/noto-serif-kr-variable.ttf",
                "/fonts/practice-korean/nanum-gothic-regular.ttf",
                "/fonts/practice-korean/gothic-a1-regular.ttf",
                "/fonts/practice-korean/gowun-dodum-regular.ttf",
                "/fonts/practice-korean/gaegu-regular.ttf",
                "/fonts/practice-korean/sunflower-medium.ttf",
                "/fonts/practice-korean/gugi-regular.ttf",
                "/fonts/practice-korean/nanum-pen-script-regular.ttf",
                "unicode-range:",
                "U+AC00-D7AF",
                "var(--font, -apple-system");
        assertThat(css).doesNotContain(
                "@import",
                "https://",
                "http://",
                "[data-practice-korean-content] {",
                ".pi-nav-item {",
                "body {",
                "song-myung-regular.ttf",
                "ibm-plex-sans-kr-regular.ttf",
                "black-han-sans-regular.ttf");
        assertThat(Files.readString(FONT_DIR.resolve("README.md"))).contains(
                "Google Fonts repository",
                "OFL");
        try (var files = Files.list(FONT_DIR)) {
            assertThat(files.filter(path -> path.toString().endsWith(".ttf")))
                    .hasSize(17)
                    .allMatch(path -> path.toFile().length() > 0);
        }
        try (var files = Files.list(FONT_DIR)) {
            assertThat(files.filter(path -> path.getFileName().toString()
                            .startsWith("OFL-")
                    && path.toString().endsWith(".txt")))
                    .hasSize(17)
                    .allMatch(path -> path.toFile().length() > 0);
        }
        assertThat(Files.readString(PLAYERS.get(0))).contains(
                "th:lang=\"${g.instructionLanguageTag()}\"",
                "th:lang=\"${g.stimulusLanguageTag()}\"",
                "th:lang=\"${q.languageTag()}\"");
        assertThat(Files.readString(PLAYERS.get(1))).contains(
                "th:lang=\"${g.instructionLanguageTag()}\"",
                "th:lang=\"${g.stimulusLanguageTag()}\"",
                "th:lang=\"${q.languageTag()}\"");
        assertThat(Files.readString(PLAYERS.get(2))).contains(
                "data-prompt-text").doesNotContain(
                "data-prompt-text lang=\"ko\"");
        assertThat(Files.readString(Path.of(
                "src/main/resources/static/js/practice/player-speaking.js")))
                .contains(
                        "currentQuestion.languageTag === \"vi\" ? \"vi\" : \"ko\"",
                        "promptText.lang");
    }
}
