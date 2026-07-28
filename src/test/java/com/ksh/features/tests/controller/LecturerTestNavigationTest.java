package com.ksh.features.tests.controller;

import com.ksh.features.classes.controller.support.ClassDetailModelSupport;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.tests.dto.LecturerTestDtos.ClassOption;
import com.ksh.features.tests.service.ExamMonitorService;
import com.ksh.features.tests.service.ExamQuestionBankPickerService;
import com.ksh.features.tests.service.LecturerExamService;
import com.ksh.features.upload.ExamImageStorageService;
import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LecturerTestNavigationTest {

    private final LecturerExamService examService = mock(LecturerExamService.class);
    private final KshUserDetails user = mock(KshUserDetails.class);
    private LecturerTestController controller;

    @BeforeEach
    void setUp() {
        controller = new LecturerTestController(
                examService,
                mock(ExamMonitorService.class),
                mock(ClassesService.class),
                mock(ClassDetailModelSupport.class),
                mock(ExamImageStorageService.class),
                mock(ExamQuestionBankPickerService.class));
        when(user.getId()).thenReturn(41L);
        when(examService.ledClasses(41L)).thenReturn(List.of(
                new ClassOption(2L, "Lớp 2"),
                new ClassOption(3L, "Lớp 3")));
    }

    @Test
    void createFromOwnedClassKeepsCanonicalClassContext() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.newForm(2L, user, model);

        assertEquals("tests/lecturer-form", view);
        assertEquals(2L, model.get("selectedClassId"));
        assertEquals("/lecturer/classes/2/tests", model.get("testReturnUrl"));
    }

    @Test
    void unknownClassCannotInjectReturnDestination() {
        ExtendedModelMap model = new ExtendedModelMap();

        controller.newForm(999L, user, model);

        assertNull(model.get("selectedClassId"));
        assertEquals("/lecturer/tests", model.get("testReturnUrl"));
    }

    @Test
    void directCreateUsesGlobalTestBankAsReturnDestination() {
        ExtendedModelMap model = new ExtendedModelMap();

        controller.newForm(null, user, model);

        assertNull(model.get("selectedClassId"));
        assertEquals("/lecturer/tests", model.get("testReturnUrl"));
    }
}
