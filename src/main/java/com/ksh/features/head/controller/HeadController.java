package com.ksh.features.head.controller;

import com.ksh.features.head.dto.HeadDtos.AssignView;
import com.ksh.features.head.dto.HeadDtos.DashboardView;
import com.ksh.features.head.dto.HeadDtos.ReportView;
import com.ksh.features.head.service.HeadDashboardService;
import com.ksh.features.head.service.HeadLecturerAssignmentService;
import com.ksh.features.head.service.HeadReportService;
import com.ksh.security.Roles;
import com.ksh.security.KshUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.ksh.common.IConstant.*;

/**
 * HEAD product shell: dashboard, lecturer assignment, and department report.
 */
@Controller
@RequestMapping(BASE_HEAD)
@PreAuthorize("hasRole('" + Roles.HEAD + "')")
public class HeadController {

    private final HeadDashboardService dashboardService;
    private final HeadLecturerAssignmentService assignmentService;
    private final HeadReportService reportService;

    public HeadController(HeadDashboardService dashboardService,
                          HeadLecturerAssignmentService assignmentService,
                          HeadReportService reportService) {
        this.dashboardService = dashboardService;
        this.assignmentService = assignmentService;
        this.reportService = reportService;
    }

    @GetMapping({"", "/"})
    public String dashboard(@AuthenticationPrincipal KshUserDetails user, Model model) {
        DashboardView view = dashboardService.load(user.getId());
        model.addAttribute(ATTR_HEAD_DEPARTMENT, view.department());
        model.addAttribute(ATTR_HEAD_KPIS, view.kpis());
        model.addAttribute(ATTR_HEAD_RECENT, view.recentClasses());
        model.addAttribute(ATTR_HEAD_EMPTY, view.emptyDepartment());
        model.addAttribute(ATTR_ACTIVE_TAB, "dashboard");
        return VIEW_HEAD_DASHBOARD;
    }

    @GetMapping("/assign")
    public String assign(@AuthenticationPrincipal KshUserDetails user, Model model) {
        AssignView view = assignmentService.load(user.getId());
        model.addAttribute(ATTR_HEAD_DEPARTMENT, view.department());
        model.addAttribute(ATTR_HEAD_CLASS_ROWS, view.classRows());
        model.addAttribute(ATTR_HEAD_LECTURERS, view.lecturers());
        model.addAttribute(ATTR_HEAD_EMPTY, view.emptyDepartment());
        model.addAttribute(ATTR_ACTIVE_TAB, "assign");
        return VIEW_HEAD_ASSIGN;
    }

    @PostMapping("/assign/{classId}")
    public String reassign(@PathVariable Long classId,
                           @RequestParam Long lecturerId,
                           @AuthenticationPrincipal KshUserDetails user,
                           RedirectAttributes ra) {
        try {
            String className = assignmentService.reassign(user.getId(), classId, lecturerId);
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_HEAD_REASSIGNED + className);
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        }
        // AccessDeniedException / EntityNotFoundException bubble to global handler (403/404).
        return "redirect:" + URL_HEAD_ASSIGN;
    }

    @GetMapping("/report")
    public String report(@AuthenticationPrincipal KshUserDetails user, Model model) {
        ReportView view = reportService.load(user.getId());
        model.addAttribute(ATTR_HEAD_DEPARTMENT, view.department());
        model.addAttribute(ATTR_HEAD_REPORT_ROWS, view.rows());
        model.addAttribute(ATTR_HEAD_EMPTY, view.emptyDepartment());
        model.addAttribute(ATTR_ACTIVE_TAB, "report");
        return VIEW_HEAD_REPORT;
    }
}
