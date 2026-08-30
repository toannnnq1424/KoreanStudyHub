package com.ksh.features.admin.users.imports.controller;

import com.ksh.features.admin.users.imports.service.ActivationResendService;
import com.ksh.security.KshUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('PERM_user.edit')")
public class AdminActivationResendController {
    private final ActivationResendService service;

    public AdminActivationResendController(ActivationResendService service) {
        this.service = service;
    }

    @PostMapping("/{id}/resend-activation")
    public String resend(@PathVariable Long id,
                         @AuthenticationPrincipal KshUserDetails admin,
                         RedirectAttributes redirect) {
        ActivationResendService.Outcome outcome = service.resend(id, admin.getId());
        switch (outcome) {
            case QUEUED -> redirect.addFlashAttribute("flashSuccess",
                    "Đã tạo liên kết mới và xếp hàng email kích hoạt.");
            case NOT_PENDING -> redirect.addFlashAttribute("flashError",
                    "Tài khoản không còn ở trạng thái chờ kích hoạt.");
            case NOT_FOUND -> redirect.addFlashAttribute("flashError",
                    "Không tìm thấy tài khoản.");
        }
        return "redirect:/admin/users";
    }
}
