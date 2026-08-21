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

import static com.ksh.common.IConstant.ATTR_FLASH_ERROR;
import static com.ksh.common.IConstant.ATTR_FLASH_SUCCESS;
import static com.ksh.common.IConstant.MSG_ACTIVATION_RESENT;
import static com.ksh.common.IConstant.MSG_ACTIVATION_RESEND_NOT_PENDING;
import static com.ksh.common.IConstant.MSG_ACTIVATION_RESEND_USER_MISSING;
import static com.ksh.common.IConstant.PATH_RESEND_ACTIVATION;
import static com.ksh.common.IConstant.URL_ADMIN_USERS;

/**
 * Admin action that re-sends an account's activation email.
 *
 * <p>Offered only for accounts that have not yet activated; the service
 * refuses anything else, and the users list hides the menu item for accounts
 * whose status is not {@code PENDING}. Hiding the item is UX only — the
 * refusal in the service is what actually enforces it.
 *
 * <p>Sits alongside the roster import because both share the token-issuing and
 * mail-queueing path. Authorization matches the rest of {@code /admin/users}:
 * the {@code /admin/**} matcher plus the {@code user.edit} permission, which
 * belongs to the USER_MANAGE group the permission guard refuses to detach
 * from ADMIN.
 */
@Controller
@RequestMapping(URL_ADMIN_USERS)
@PreAuthorize("hasAuthority('PERM_user.edit')")
public class AdminActivationResendController {

    private static final String REDIRECT_BASE = "redirect:" + URL_ADMIN_USERS;

    private final ActivationResendService resendService;

    public AdminActivationResendController(ActivationResendService resendService) {
        this.resendService = resendService;
    }

    /** Issues a fresh activation token and queues its email for one account. */
    @PostMapping("/{id}" + PATH_RESEND_ACTIVATION)
    public String resend(@PathVariable Long id,
                         @AuthenticationPrincipal KshUserDetails admin,
                         RedirectAttributes ra) {
        ActivationResendService.Outcome outcome = resendService.resend(id, admin.getId());
        switch (outcome) {
            case QUEUED -> ra.addFlashAttribute(ATTR_FLASH_SUCCESS, MSG_ACTIVATION_RESENT);
            case ALREADY_ACTIVATED ->
                    ra.addFlashAttribute(ATTR_FLASH_ERROR, MSG_ACTIVATION_RESEND_NOT_PENDING);
            // NOT_FOUND covers a deleted or never-existing id; report it the
            // same way rather than 404-ing out of the list screen.
            case NOT_FOUND ->
                    ra.addFlashAttribute(ATTR_FLASH_ERROR, MSG_ACTIVATION_RESEND_USER_MISSING);
        }
        return REDIRECT_BASE;
    }
}