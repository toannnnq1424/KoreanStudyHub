package com.ksh.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassEntityApprovalTest {
    @Test
    void newClassWaitsForApprovalAndCanBeApprovedOnce() {
        ClassEntity clazz = new ClassEntity("Lớp mới", 7L, 7L,
                null, null, null, 30);
        LocalDateTime reviewedAt = LocalDateTime.of(2026, 7, 31, 0, 30);

        assertThat(clazz.getStatus()).isEqualTo(ClassEntity.STATUS_DRAFT);
        clazz.approve(11L, reviewedAt);

        assertThat(clazz.getStatus()).isEqualTo(ClassEntity.STATUS_UPCOMING);
        assertThat(clazz.getApprovedBy()).isEqualTo(11L);
        assertThat(clazz.getApprovedAt()).isEqualTo(reviewedAt);
        assertThatThrownBy(() -> clazz.approve(11L, reviewedAt))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectionStoresTrimmedOptionalNoteAndIsTerminal() {
        ClassEntity clazz = new ClassEntity("Lớp mới", 7L, 7L,
                null, null, null, 30);

        clazz.reject(11L, "  Thiếu thông tin  ", LocalDateTime.now());

        assertThat(clazz.getStatus()).isEqualTo(ClassEntity.STATUS_REJECTED);
        assertThat(clazz.getRejectionNote()).isEqualTo("Thiếu thông tin");
        assertThatThrownBy(() -> clazz.reject(11L, null, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
