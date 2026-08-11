package com.ksh.features.tests.controller;

import com.ksh.features.classes.controller.support.ClassDetailModelSupport;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.entities.ClassEntity;
import com.ksh.features.tests.dto.LecturerTestDtos.ClassOption;
import com.ksh.features.tests.dto.LecturerTestDtos.ExamForm;
import com.ksh.features.tests.dto.LecturerTestDtos.ExamHeader;
import com.ksh.features.tests.service.ExamMonitorService;
import com.ksh.features.tests.service.ExamQuestionBankPickerService;
import com.ksh.features.tests.service.LecturerExamService;
import com.ksh.features.upload.ExamImageStorageService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LecturerTestNavigationTest {

    private final LecturerExamService examService = mock(LecturerExamService.class);
    private final ExamMonitorService monitorService = mock(ExamMonitorService.class);
    private final ClassesService classesService = mock(ClassesService.class);
    private final ClassDetailModelSupport classDetailSupport = mock(ClassDetailModelSupport.class);
    private final ExamQuestionBankPickerService questionBankPickerService =
            mock(ExamQuestionBankPickerService.class);
    private final KshUserDetails user = mock(KshUserDetails.class);
    private LecturerTestController controller;

    @BeforeEach
    void setUp() {
        controller = new LecturerTestController(
                examService,
                monitorService,
                classesService,
                classDetailSupport,
                mock(ExamImageStorageService.class),
                questionBankPickerService);
        when(user.getId()).thenReturn(41L);
        when(user.getRole()).thenReturn(Role.LECTURER);
        when(examService.ledClasses(41L)).thenReturn(List.of(
                new ClassOption(2L, "Lớp 2", 10L),
                new ClassOption(3L, "Lớp 3", 11L)));
    }

    @Test
    void createFromOwnedClassKeepsCanonicalClassContext() {
        ClassEntity clazz = mock(ClassEntity.class);
        when(classesService.getViewable(2L, 41L, Role.LECTURER)).thenReturn(clazz);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.newForm(2L, user, model);

        assertEquals("tests/lecturer-form", view);
        assertEquals(2L, model.get("selectedClassId"));
        assertEquals("/lecturer/classes/2/tests", model.get("testReturnUrl"));
        verify(classDetailSupport).populateDetail(model, clazz, "info", 41L, Role.LECTURER);
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

    @Test
    void manageableExamStillRendersWhenOptionalClassSidebarIsForbidden() {
        ExamForm form = new ExamForm(
                1L, "Bài test", null, 10L, 2L, "MOCK", "DRAFT", "FIXED_WINDOW",
                60, null, null, BigDecimal.valueOf(5),
                false, false, null, null, List.of(), false);
        when(examService.getForEdit(1L, 41L)).thenReturn(form);
        when(monitorService.header(1L, 41L))
                .thenReturn(new ExamHeader(1L, "Bài test", "DRAFT", "FIXED_WINDOW",
                        null, 0));
        when(classesService.getViewable(2L, 41L, Role.LECTURER))
                .thenThrow(new AccessDeniedException("Không có quyền xem lớp"));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.editForm(1L, "info", null, 0, user, model);

        assertEquals("tests/lecturer-form", view);
        assertEquals(form, model.get("examForm"));
        assertNull(model.get("clazz"));
        verifyNoInteractions(classDetailSupport);
    }

    @Test
    void editingIndependentTestBankItemNeverLoadsAnArbitraryClassSidebar() {
        ExamForm form = new ExamForm(
                9L, "Đề độc lập", null, 10L, null, "MOCK", "DRAFT", "INDIVIDUAL",
                30, null, null, null,
                false, false, null, null, List.of(), false);
        when(examService.getForEdit(9L, 41L)).thenReturn(form);
        when(monitorService.header(9L, 41L))
                .thenReturn(new ExamHeader(9L, "Đề độc lập", "DRAFT", "INDIVIDUAL",
                        null, 0));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.editForm(9L, "info", null, 0, user, model);

        assertEquals("tests/lecturer-form", view);
        assertNull(model.get("clazz"));
        assertEquals(10L, model.get("selectedSubjectId"));
        assertNull(model.get("selectedClassId"));
        verifyNoInteractions(classesService, classDetailSupport);
    }
}
