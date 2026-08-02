package com.ksh.features.leader.controller;

import com.ksh.features.leader.dto.LeaderDtos.ApprovalQueueView;
import com.ksh.features.leader.dto.LeaderDtos.DashboardView;
import com.ksh.features.leader.dto.LeaderDtos.ReportView;
import com.ksh.features.leader.service.LeaderClassApprovalService;
import com.ksh.features.leader.service.LeaderDashboardService;
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
 * LEADER product shell: dashboard, class approval queue, and department report.
 */
@Controller
@RequestMapping(BASE_LEADER)
@PreAuthorize("hasRole('" + Roles.LEADER + "')")
public class LeaderController {

    private final LeaderDashboardService dashboardService;
    private final LeaderClassApprovalService approvalService;
    private final LeaderReportService reportService;

    public LeaderController(LeaderDashboardService dashboardService,
                            LeaderClassApprovalService approvalService,
                            LeaderReportService reportService) {
        this.dashboardService = dashboardService;
        this.approvalService = approvalService;
        this.reportService = reportService;
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

    @GetMapping("/approvals")
    public String approvals(@AuthenticationPrincipal KshUserDetails user, Model model) {
        ApprovalQueueView view = approvalService.load(user.getId());
        model.addAttribute(ATTR_LEADER_DEPARTMENT, view.department());
        model.addAttribute(ATTR_LEADER_PENDING_CLASSES, view.pendingClasses());
        model.addAttribute(ATTR_LEADER_EMPTY, view.emptyDepartment());
        model.addAttribute(ATTR_ACTIVE_TAB, "approvals");
        return VIEW_LEADER_APPROVALS;
    }

    @PostMapping("/approvals/{classId}/approve")
    public String approveClass(@PathVariable Long classId,
                               @AuthenticationPrincipal KshUserDetails user,
                               RedirectAttributes ra) {
        try {
            String className = approvalService.approve(user.getId(), classId);
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_LEADER_CLASS_APPROVED + className);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        }
        // AccessDeniedException / EntityNotFoundException bubble to global handler (403/404).
        return "redirect:" + URL_LEADER_APPROVALS;
    }

    @PostMapping("/approvals/{classId}/reject")
    public String rejectClass(@PathVariable Long classId,
                              @RequestParam(required = false) String note,
                              @AuthenticationPrincipal KshUserDetails user,
                              RedirectAttributes ra) {
        try {
            String className = approvalService.reject(user.getId(), classId, note);
            ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_LEADER_CLASS_REJECTED + className);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
        }
        return "redirect:" + URL_LEADER_APPROVALS;
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
    // LEADERQuestionBankController at /LEADER/question-bank; no handler here.

}