package com.ksh.features.practice.manage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeExcelRetirementStaticContractTest {

    @Test
    void routeTemplateAndServiceExposeOnlyQuickExcelWriteSurface()
            throws Exception {
        String controller = read("src/main/java/com/ksh/features/practice/manage/"
                + "controller/PracticeAssessmentExcelController.java");
        String service = read("src/main/java/com/ksh/features/practice/manage/"
                + "service/PracticeAssessmentExcelService.java");
        String template = read("src/main/resources/templates/practice/manage/"
                + "excel-import.html");

        assertThat(controller)
                .contains("@GetMapping(\"/template/quick-v1\")")
                .contains("@PostMapping(value = \"/preview\"")
                .contains("@PostMapping(value = \"/import\"")
                .doesNotContain("@GetMapping(\"/template\")", "mediaOverrides");
        assertThat(service)
                .contains("ADVANCED_EXCEL_V2_RETIRED")
                .contains("LEGACY_EXCEL_V1_RETIRED")
                .contains("SourceKind.QUICK_EXCEL")
                .doesNotContain(
                        "PracticeAssessmentExcelV2Codec",
                        "LecturerAssetService",
                        "SPEAKING_PROMPT_EXCEL_STAGING",
                        "adaptExactTargetGroups",
                        "applyMediaOverrides");
        assertThat(template)
                .contains("Quick Excel v1 · text-only")
                .contains("/practice/manage/excel/template/quick-v1")
                .contains("download=\"ksh-practice-quick-v1.xlsx\"")
                .contains("thư mục Downloads")
                .contains("overscroll-behavior:contain")
                .contains("window.location.assign(result.reviewUrl)")
                .doesNotContain(
                        "Advanced v2",
                        "Legacy v1",
                        "excel-media-files",
                        "mediaOverrides",
                        "uploadPendingMedia",
                        "/practice/manage/excel/template\"");
    }

    @Test
    void canonicalEditorOwnsAdvancedMediaAndSpeakingReplacement()
            throws Exception {
        String editor = read("src/main/resources/templates/practice/manage/"
                + "editor.html");
        String speaking = read("src/main/resources/static/js/practice/"
                + "manage-speaking-prompt-authoring.js");
        String preview = read("src/main/resources/static/js/practice/"
                + "manage-draft-preview.js");

        assertThat(editor).contains(
                "id=\"group-audio-area\"",
                "id=\"group-image-dropzone\"",
                "id=\"question-image-dropzone\"",
                "id=\"question-audio-area\"",
                "id=\"question-audio-dropzone\"",
                "id=\"matching-area\"",
                "id=\"fill-prompt-composer\"",
                "id=\"speaking-mode-audio\"",
                "id=\"speaking-mode-manual\"");
        assertThat(editor + speaking)
                .doesNotContain(
                        "speaking-excel-staging",
                        "excelStagingAudioAvailable",
                        "adoptExcelStaging");
        assertThat(preview).contains(
                "audioUrl: content.audioReference || ''",
                "appendAudio(card, question.audioUrl)",
                "question.questionType === 'MATCHING'",
                "['FILL_BLANK', 'GAP_FILL'].includes(question.questionType)");
    }

    @Test
    void excelPathCannotInvokeStorageOrAiProviders() throws Exception {
        String excel = read("src/main/java/com/ksh/features/practice/manage/"
                + "service/PracticeAssessmentExcelService.java")
                + read("src/main/java/com/ksh/features/practice/manage/"
                + "service/PracticeAssessmentQuickExcelCodec.java")
                + read("src/main/resources/templates/practice/manage/"
                + "excel-import.html");

        assertThat(excel).doesNotContain(
                "OpenAi",
                "SpeakingPrompt",
                "R2Client",
                "S3Client",
                "upload-audio",
                "upload-image");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
