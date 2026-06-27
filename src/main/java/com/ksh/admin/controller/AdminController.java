package com.ksh.admin.controller;

import com.ksh.admin.dto.AdminDashboardDtos.DashboardStats;
import com.ksh.admin.dto.AdminDashboardDtos.RecentClass;
import com.ksh.admin.dto.AdminDashboardDtos.UserRoleCount;
import com.ksh.admin.service.AdminDashboardService;
import com.ksh.auth.Roles;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Controller for system administration dashboard (Admin).
 * Restricted to ADMIN only — can be extended to HEAD in a future sprint
 * when department-specific dashboards are implemented.
 *
 * <p>URL pattern: {@code /admin/{tab}} — 5 sidebar items:
 * <ul>
 *   <li>{@code /dashboard} — stats + charts + recent activity (Sprint 2 wireframe).</li>
 *   <li>{@code /settings}  — Settings index (links to Email/General settings/etc.).</li>
 *   <li>{@code /users}, {@code /departments}, {@code /classes}
 *       — placeholders, to be populated with real data in Sprint 6.</li>
 * </ul>
 *
 * <p>Sub-tab {@code /admin/settings/email} is handled by
 * {@link com.ksh.admin.settings.controller.EmailSettingsController}.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('" + Roles.ADMIN + "')")
public class AdminController {

    private final AdminDashboardService dashboardService;

    public AdminController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public String root() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        DashboardStats stats = dashboardService.stats();
        List<UserRoleCount> rolesBreakdown = dashboardService.usersByRole();
        List<RecentClass> recentClasses = dashboardService.recentClasses(5);

        model.addAttribute("stats", stats);
        model.addAttribute("rolesBreakdown", rolesBreakdown);
        model.addAttribute("recentClasses", recentClasses);
        populateSidebar(model, "dashboard");
        return "admin/dashboard";
    }

    @GetMapping("/settings")
    public String settingsIndex(Model model) {
        populateSidebar(model, "settings");
        return "admin/settings";
    }

    @GetMapping({"/users", "/departments", "/classes"})
    public String placeholder(HttpServletRequest request, Model model) {
        String path = request.getRequestURI();
        String tab = path.substring(path.lastIndexOf('/') + 1);
        populateSidebar(model, tab);
        model.addAttribute("placeholderTab", tab);
        model.addAttribute("placeholderLabel", labelFor(tab));
        return "admin/placeholder";
    }

    private void populateSidebar(Model model, String activeTab) {
        model.addAttribute("activeTab", activeTab);
    }

    private static String labelFor(String tab) {
        return switch (tab) {
            case "users" -> "Tài khoản";
            case "departments" -> "Bộ môn";
            case "classes" -> "Lớp học";
            default -> tab;
        };
    }
}