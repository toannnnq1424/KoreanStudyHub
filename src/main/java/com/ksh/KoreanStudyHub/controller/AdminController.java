package com.ksh.KoreanStudyHub.controller;

import com.ksh.KoreanStudyHub.dto.request.CreateUserRequest;
import com.ksh.KoreanStudyHub.entity.User;
import com.ksh.KoreanStudyHub.service.NotificationAdminService;
import com.ksh.KoreanStudyHub.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserAdminService userAdminService;
    private final NotificationAdminService notificationAdminService;

    // ===== DASHBOARD =====
    @GetMapping
    public String dashboard(Model model) {

        model.addAttribute("totalUsers",
                userAdminService.countTotal());

        model.addAttribute("activeUsers",
                userAdminService.countActive());

        model.addAttribute("totalStudents",
                userAdminService.countByRole("STUDENT"));

        model.addAttribute("totalTeachers",
                userAdminService.countByRole("TEACHER"));

        model.addAttribute("unreadNotifications",
                notificationAdminService.countUnread());

        model.addAttribute("totalNotifications",
                notificationAdminService.countTotal());

        return "Admin/dashboard";
    }

    // ===== USER LIST + SEARCH + FILTER =====
    @GetMapping("/users")
    public String listUsers(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "") String role,
                            Model model) {
        if (keyword != null && !keyword.isBlank()) {
            var list = userAdminService.searchUsers(keyword);
            if (!role.isBlank()) list = list.stream()
                    .filter(u -> u.getRole().getRoleName().equals(role)).toList();
            model.addAttribute("users", list);
            model.addAttribute("keyword", keyword);
        } else if (!role.isBlank()) {
            model.addAttribute("users", userAdminService.getUsersByRole(role));
        } else {
            var pageData = userAdminService.getAllUsers(
                    PageRequest.of(page, 10, Sort.by("createdAt").descending()));
            model.addAttribute("users", pageData.getContent());
            model.addAttribute("totalPages", pageData.getTotalPages());
            model.addAttribute("currentPage", page);
        }
        model.addAttribute("roles", userAdminService.getAllRoles());
        model.addAttribute("createRequest", new CreateUserRequest());
        model.addAttribute("totalUsers", userAdminService.countTotal());
        model.addAttribute("activeUsers", userAdminService.countActive());
        model.addAttribute("selectedRole", role);
        return "Admin/users/list";
    }

    // ===== USER DETAIL (JSON for modal) =====
    @GetMapping("/users/{id}/detail")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> userDetail(@PathVariable Long id) {
        User u = userAdminService.getById(id);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", u.getUserId());
        data.put("fullName", u.getFullName());
        data.put("email", u.getEmail());
        data.put("role", u.getRole().getRoleName());
        data.put("status", u.getStatus());
        data.put("avatar", u.getAvatar());
        data.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "—");
        return ResponseEntity.ok(data);
    }

    // ===== USER CRUD =====
    @PostMapping("/users/create")
    public String createUser(@Valid @ModelAttribute CreateUserRequest request, RedirectAttributes ra) {
        try { userAdminService.createUser(request); ra.addFlashAttribute("success", "Tạo tài khoản thành công!"); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
        try { userAdminService.toggleStatus(id); ra.addFlashAttribute("success", "Cập nhật trạng thái thành công!"); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes ra) {
        try { userAdminService.resetPassword(id); ra.addFlashAttribute("success", "Đặt lại mật khẩu thành công! Mật khẩu mới: 123456"); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
        try { userAdminService.deleteUser(id); ra.addFlashAttribute("success", "Xóa tài khoản thành công!"); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    // ===== ROLES =====
    @GetMapping("/roles")
    public String listRoles(Model model) {
        var roles = userAdminService.getAllRoles();
        Map<String, Long> roleCounts = new HashMap<>();
        for (var r : roles) roleCounts.put(r.getRoleName(), userAdminService.countByRole(r.getRoleName()));
        model.addAttribute("roles", roles);
        model.addAttribute("roleCounts", roleCounts);
        return "Admin/roles/list";
    }

    // ===== NOTIFICATIONS =====
    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("notifications", notificationAdminService.getAll());
        model.addAttribute("users", userAdminService.getAllUsers(PageRequest.of(0, 1000, Sort.unsorted())).getContent());
        model.addAttribute("unreadCount", notificationAdminService.countUnread());
        model.addAttribute("totalCount", notificationAdminService.countTotal());
        return "Admin/notifications";
    }

    @PostMapping("/notifications/send")
    public String sendNotification(@RequestParam Long userId,
                                   @RequestParam String message,
                                   RedirectAttributes ra) {
        try { notificationAdminService.send(userId, message); ra.addFlashAttribute("success", "Gửi thông báo thành công!"); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/notifications";
    }

    @PostMapping("/notifications/send-all")
    public String sendAll(@RequestParam String message, RedirectAttributes ra) {
        try { notificationAdminService.sendToAll(message); ra.addFlashAttribute("success", "Đã gửi thông báo đến tất cả người dùng!"); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/notifications";
    }

    @PostMapping("/notifications/{id}/delete")
    public String deleteNotification(@PathVariable Long id, RedirectAttributes ra) {
        try { notificationAdminService.delete(id); ra.addFlashAttribute("success", "Đã xóa thông báo!"); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/notifications";
    }

    // ===== SETTINGS & AUDIT =====
    @GetMapping("/settings") public String settings() { return "Admin/settings"; }
    @GetMapping("/audit-log") public String auditLog() { return "Admin/audit-log"; }
}
