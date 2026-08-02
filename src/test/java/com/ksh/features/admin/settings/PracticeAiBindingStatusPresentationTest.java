package com.ksh.features.admin.settings;

import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.BindingRow;
import com.ksh.features.admin.settings.dto.PracticeAiSettingsDtos.CapabilityRunRow;
import com.ksh.features.practice.ai.controlplane.PracticeAiPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAiBindingStatusPresentationTest {

    @Test
    void mapsMissingPausedCurrentPassAndStalePassToVietnameseStates() {
        assertThat(row(null, false, 0, List.of()).statusLabel()).isEqualTo("Chưa thiết lập");
        assertThat(row(1L, false, 0, List.of()).statusLabel()).isEqualTo("Tạm tắt");
        assertThat(row(1L, true, 0, List.of()).statusLabel()).isEqualTo("Cần kiểm tra");
        assertThat(row(1L, true, 0, List.of(run(0L, "PASS"))).statusLabel())
                .isEqualTo("Sẵn sàng");
        assertThat(row(1L, true, 1, List.of(run(0L, "PASS"))).statusLabel())
                .isEqualTo("Cần kiểm tra");
        assertThat(row(1L, true, 0, List.of(run(0L, "FAIL"))).statusCode())
                .isEqualTo("check");
    }

    @Test
    void allSixPurposeLabelsAreClearVietnameseWhileCodesRemainStable() {
        assertThat(PracticeAiPurpose.values()).hasSize(6);
        assertThat(List.of(PracticeAiPurpose.values()).stream()
                .map(PracticeAiPurpose::displayName))
                .containsExactly(
                        "Biên soạn từ PDF",
                        "Giải thích Đọc / Nghe",
                        "Chấm bài Viết",
                        "Chấm bài Nói",
                        "Chuyển giọng nói thành văn bản",
                        "Tạo giọng đọc đề bài");
        assertThat(PracticeAiPurpose.PRACTICE_PDF_AUTHORING.name())
                .isEqualTo("PRACTICE_PDF_AUTHORING");
    }

    private static BindingRow row(Long profileId, boolean enabled, long revision,
                                  List<CapabilityRunRow> runs) {
        return new BindingRow(PracticeAiPurpose.PRACTICE_PDF_AUTHORING,
                "Biên soạn từ PDF", "STRICT_JSON_SCHEMA", profileId,
                profileId == null ? null : "PRACTICE_PRIMARY", "model", enabled,
                revision, "PRACTICE_AUTHORING_V1", null, runs);
    }

    private static CapabilityRunRow run(Long revision, String status) {
        return new CapabilityRunRow(1L, revision, status, 1L, null, null);
    }
}
