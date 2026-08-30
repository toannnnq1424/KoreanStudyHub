package com.ksh.features.library.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LessonTemplateFormValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void video_summary_accepts_1000_characters_and_rejects_1001() {
        LessonTemplateForm form = validForm();
        form.setVideoSummary("가".repeat(1000));

        assertThat(validator.validate(form)).isEmpty();

        form.setVideoSummary("가".repeat(1001));
        assertThat(validator.validate(form))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("videoSummary");
                    assertThat(violation.getMessage())
                            .isEqualTo("Tóm tắt video tối đa 1000 ký tự");
                });
    }

    private static LessonTemplateForm validForm() {
        LessonTemplateForm form = new LessonTemplateForm();
        form.setChapterTitle("Nền tảng giao tiếp");
        form.setTitle("Chào hỏi trong lớp học");
        form.setContentType("RICHTEXT");
        return form;
    }
}
