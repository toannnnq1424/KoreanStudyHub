package com.ksh.features.practice.manage.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LecturerAssetTitleValidationTest {

    @Test
    void preservesOrdinaryUnicodeAndMarkupCharactersExactly() {
        String title =
                "한국어 · Tiếng Việt & \"ảnh\" <b>không chạy</b> 🐯";

        assertThat(LecturerAssetService.validatedAssetTitle(
                title, null)).isEqualTo(title);
    }

    @Test
    void rejectsControlsAndDatabaseOverflow() {
        assertThatThrownBy(() ->
                LecturerAssetService.validatedAssetTitle(
                        "ảnh\u0000xấu", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("điều khiển");
        assertThatThrownBy(() ->
                LecturerAssetService.validatedAssetTitle(
                        "한".repeat(256), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }
}
