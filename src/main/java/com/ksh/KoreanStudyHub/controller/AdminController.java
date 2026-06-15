package com.ksh.KoreanStudyHub.controller;

import com.ksh.KoreanStudyHub.dto.request.CreateUserRequest;
import com.ksh.KoreanStudyHub.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userAdminService.countTotal());
        model.addAttribute("activeUsers", userAdminService.countActive());
        model.addAttribute("totalStudents", userAdminService.countByRole("STUDENT"));
        model.addAttribute("totalTeachers", userAdminService.countByRole("TEACHER"));
        return "Admin/dashboard";
    }

    @GetMapping("/users")
    public String listUsers(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "") String keyword,
                            Model model) {
        if (keyword != null && !keyword.isBlank()) {
            model.addAttribute("users", userAdminService.searchUsers(keyword));
            model.addAttribute("keyword", keyword);
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
        return "Admin/users/list";
    }

    @PostMapping("/users/create")
    public String createUser(@Valid @ModelAttribute CreateUserRequest request,
                             RedirectAttributes ra) {
        try {
            userAdminService.createUser(request);
            ra.addFlashAttribute("success", "Tạo tài khoản thành công!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userAdminService.toggleStatus(id);
            ra.addFlashAttribute("success", "Cập nhật trạng thái thành công!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userAdminService.resetPassword(id);
            ra.addFlashAttribute("success", "Cấp lại mật khẩu thành công! Mật khẩu mới ngẫu nhiên đã được gửi tới email của người dùng.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userAdminService.deleteUser(id);
            ra.addFlashAttribute("success", "Xóa tài khoản thành công!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/roles")
    public String listRoles(Model model) {
        var roles = userAdminService.getAllRoles();
        Map<String, Long> roleCounts = new HashMap<>();
        for (var r : roles) {
            roleCounts.put(r.getRoleName(), userAdminService.countByRole(r.getRoleName()));
        }
        model.addAttribute("roles", roles);
        model.addAttribute("roleCounts", roleCounts);
        return "Admin/roles/list";
    }

    @GetMapping("/settings")
    public String settings() {
        return "Admin/settings";
    }

    @GetMapping("/audit-log")
    public String auditLog(Model model) {
        return "Admin/audit-log";
    }
}
