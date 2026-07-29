package com.ksh.features.tests.controller;

import com.ksh.features.classes.controller.support.ClassDetailModelSupport;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.features.tests.dto.TestDtos.ReviewView;
import com.ksh.features.tests.service.ExamMonitorService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LecturerMonitorNavigationTest {

    @Test
    void manageableReviewStillRendersWhenOptionalClassSidebarIsForbidden() {
        ExamMonitorService monitorService = mock(ExamMonitorService.class);
        ClassesService classesService = mock(ClassesService.class);
        ClassDetailModelSupport classDetailSupport = mock(ClassDetailModelSupport.class);
        KshUserDetails user = mock(KshUserDetails.class);
        LecturerMonitorController controller = new LecturerMonitorController(
                monitorService, classesService, classDetailSupport);
        ReviewView review = new ReviewView(
                1L, 2L, 9L, "Bài test",
                1, 1, BigDecimal.TEN, true, "Học viên", List.of());
        when(user.getId()).thenReturn(41L);
        when(user.getRole()).thenReturn(Role.LECTURER);
        when(monitorService.lecturerReview(1L, 9L, 41L)).thenReturn(review);
        when(classesService.getViewable(2L, 41L, Role.LECTURER))
                .thenThrow(new AccessDeniedException("Không có quyền xem lớp"));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.review(1L, 9L, user, model);

        assertEquals("tests/review", view);
        assertEquals(review, model.get("review"));
        assertNull(model.get("clazz"));
        verifyNoInteractions(classDetailSupport);
    }
}
