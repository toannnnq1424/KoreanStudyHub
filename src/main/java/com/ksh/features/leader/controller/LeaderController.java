package com.ksh.features.leader.controller;

import com.ksh.features.leader.dto.LeaderDtos.AssignView;
import com.ksh.features.leader.dto.LeaderDtos.DashboardView;
import com.ksh.features.leader.dto.LeaderDtos.ReportView;
import com.ksh.features.leader.dto.LeaderDtos.ApprovalQueueView;
import com.ksh.features.leader.service.LeaderClassApprovalService;
import com.ksh.features.leader.service.LeaderDashboardService;
import com.ksh.features.leader.service.LeaderLecturerAssignmentService;
import com.ksh.features.leader.service.LeaderReportService;
import com.ksh.security.Roles;
import com.ksh.security.KshUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.ksh.common.IConstant.*;

/**
 * LEADER product shell: dashboard, lecturer assignment, and department report.
 */
@Controller
@RequestMapping(BASE_LEADER)
@PreAuthorize("hasRole('" + Roles.LEADER + "')")
public class LeaderController {

    private final LeaderDashboardService dashboardService;
    private final LeaderLecturerAssignmentService assignmentService;
    private final LeaderReportService reportService;
    private final LeaderClassApprovalService approvalService;

    public LeaderController(LeaderDashboardService dashboardService,
                          LeaderLecturerAssignmentService assignmentService,
                          LeaderReportService reportService,
                          LeaderClassApprovalService approvalService) {
        this.dashboardService = dashboardService;
        this.assignmentService = assignmentService;
        this.reportService = reportService;
        this.approvalService = approvalService;
    }

    @GetMapping("/approvals")
    public String approvals(@AuthenticationPrincipal KshUserDetails user, Model model) {
        ApprovalQueueView view = approvalService.load(user.getId());
        model.addAttribute(ATTR_LEADER_DEPARTMENT, view.department());
        model.addAttribute("pendingClasses", view.pendingClasses());
        model.addAttribute(ATTR_LEADER_EMPTY, view.emptyDepartment());
        return "leader/approvals";
    }

    @PostMapping("/approvals/{classId}/approve")
    public String approveClass(@PathVariable Long classId,
                               @AuthenticationPrincipal KshUserDetails user,
                               RedirectAttributes ra) {
        try {
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS,
                    "Đã duyệt lớp " + approvalService.approve(user.getId(), classId));
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        }
        return "redirect:/leader/approvals";
    }

    @PostMapping("/approvals/{classId}/reject")
    public String rejectClass(@PathVariable Long classId,
                              @RequestParam(required = false) String note,
                              @AuthenticationPrincipal KshUserDetails user,
                              RedirectAttributes ra) {
        try {
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS,
                    "Đã từ chối lớp " + approvalService.reject(user.getId(), classId, note));
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        }
        return "redirect:/leader/approvals";
    }

    @GetMapping({"", "/"})
    public String dashboard(@AuthenticationPrincipal KshUserDetails user, Model model) {
        DashboardView view = dashboardService.load(user.getId());
        model.addAttribute(ATTR_LEADER_DEPARTMENT, view.department());
        model.addAttribute(ATTR_LEADER_KPIS, view.kpis());
        model.addAttribute(ATTR_LEADER_RECENT, view.recentClasses());
        model.addAttribute(ATTR_LEADER_EMPTY, view.emptyDepartment());
        model.addAttribute(ATTR_ACTIVE_TAB, "dashboard");
        return VIEW_LEADER_DASHBOARD;
    }

    @GetMapping("/assign")
    public String assign(@AuthenticationPrincipal KshUserDetails user, Model model) {
        AssignView view = assignmentService.load(user.getId());
        model.addAttribute(ATTR_LEADER_DEPARTMENT, view.department());
        model.addAttribute(ATTR_LEADER_CLASS_ROWS, view.classRows());
        model.addAttribute(ATTR_LEADER_LECTURERS, view.lecturers());
        model.addAttribute(ATTR_LEADER_EMPTY, view.emptyDepartment());
        model.addAttribute(ATTR_ACTIVE_TAB, "assign");
        return VIEW_LEADER_ASSIGN;
    }

    @PostMapping("/assign/{classId}")
    public String reassign(@PathVariable Long classId,
                           @RequestParam(name = "lecturerIds", required = false)
                           java.util.List<Long> lecturerIds,
                           @RequestParam(name = "lecturerId", required = false)
                           Long legacyLecturerId,
                           @AuthenticationPrincipal KshUserDetails user,
                           RedirectAttributes ra) {
        try {
            java.util.List<Long> selected = lecturerIds;
            if ((selected == null || selected.isEmpty()) && legacyLecturerId != null) {
                selected = java.util.List.of(legacyLecturerId);
            }
            String className = assignmentService.updateCoLecturers(
                    user.getId(), classId, selected);
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS,
                    "Đã cập nhật nhóm đồng giảng cho lớp " + className);
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        }
        // AccessDeniedException / EntityNotFoundException bubble to global handler (403/404).
        return "redirect:" + URL_LEADER_ASSIGN;
    }

    @GetMapping("/report")
    public String report(@AuthenticationPrincipal KshUserDetails user, Model model) {
        ReportView view = reportService.load(user.getId());
        model.addAttribute(ATTR_LEADER_DEPARTMENT, view.department());
        model.addAttribute(ATTR_LEADER_REPORT_ROWS, view.rows());
        model.addAttribute(ATTR_LEADER_EMPTY, view.emptyDepartment());
        model.addAttribute(ATTR_ACTIVE_TAB, "report");
        return VIEW_LEADER_REPORT;
    }

    // The department question bank management screen is served by
    // LeaderQuestionBankController at /leader/question-bank; no handler here.

}
