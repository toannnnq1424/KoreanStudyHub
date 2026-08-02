package com.ksh.features.flashcards.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiFlashcardUploadUiContractTest {

    private static final Path SCRIPT =
            Path.of("src/main/resources/static/js/flashcard-ai-generate.js");
    private static final Path TEMPLATE =
            Path.of("src/main/resources/templates/flashcards/deck-form.html");

    @Test
    void keeps_an_in_memory_file_copy_for_retry_after_provider_failure() throws IOException {
        String javascript = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertThat(javascript)
                .contains("var selectedFile = null;")
                .contains("file.arrayBuffer()")
                .contains("selectedFile = new File([bytes], file.name")
                .contains("formData.append('file', file, file.name)")
                .contains("File vẫn được giữ; bạn có thể bấm Tạo bản nháp để thử lại.");
    }

    @Test
    void does_not_trust_a_stale_native_filename_without_a_file_object() throws IOException {
        String javascript = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertThat(javascript)
                .contains("if (!selectedFile && fileInput.value)")
                .contains("Bạn đang nhắc đến file nhưng chưa có PDF/DOCX nào được đính kèm.");
    }

    @Test
    void renders_one_explicit_selected_file_status() throws IOException {
        String template = Files.readString(TEMPLATE, StandardCharsets.UTF_8);

        assertThat(template)
                .contains("id=\"fcAiFileName\"")
                .contains("File đã chọn sẽ được giữ để bạn thử lại nếu AI gặp lỗi.");
    }
}
