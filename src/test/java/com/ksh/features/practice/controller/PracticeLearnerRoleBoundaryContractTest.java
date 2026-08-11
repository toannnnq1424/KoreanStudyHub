package com.ksh.features.practice.controller;

import com.ksh.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeLearnerRoleBoundaryContractTest {

    @Test
    void learnerPagesRequireTheExactStudentRole() {
        assertStudentGate(PracticeController.class);
    }

    @Test
    void learnerSpeakingUploadsRequireTheExactStudentRole() {
        assertStudentGate(PracticeSpeakingMediaController.class);
    }

    private static void assertStudentGate(Class<?> controllerType) {
        PreAuthorize gate = controllerType.getAnnotation(PreAuthorize.class);
        assertThat(gate).isNotNull();
        assertThat(gate.value()).isEqualTo(Roles.PREAUTH_STUDENT);
    }
}
