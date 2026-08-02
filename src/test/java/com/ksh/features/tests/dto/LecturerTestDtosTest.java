package com.ksh.features.tests.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LecturerTestDtosTest {

    @Test
    void exam_filter_accepts_missing_optional_parameters() {
        LecturerTestDtos.ExamFilter filter =
                LecturerTestDtos.ExamFilter.of(null, null, null, null, List.of());

        assertThat(filter.keyword()).isEmpty();
        assertThat(filter.status()).isNull();
        assertThat(filter.type()).isNull();
        assertThat(filter.classId()).isNull();
        assertThat(filter.isEmpty()).isTrue();
    }
}
