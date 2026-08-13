package com.ksh.features.questionbank.controller;

import com.ksh.entities.Department;
import com.ksh.features.leader.dto.LeaderDtos.DepartmentSummary;
import com.ksh.features.leader.service.LeaderDepartmentResolver;
import com.ksh.features.questionbank.service.QuestionBankItemService;
import com.ksh.features.questionbank.service.QuestionBankReviewService;
import com.ksh.features.questionbank.service.QuestionBankValidationException;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.dao.OptimisticLockingFailureException;
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

/** Subject-wide Question Bank review inbox for a subject leader. */
@Controller
@RequestMapping(BASE_LEADER_QUESTION_BANK)
@PreAuthorize("hasRole('" + Roles.LEADER + "')")
public class LeaderQuestionBankController {

    private static final String TAB_QUESTION_BANK = "question-bank";
    private static final String MSG_REVIEW_CONFLICT =
            "Câu hỏi vừa được người khác cập nhật. Danh sách đã được tải lại; vui lòng kiểm tra trạng thái mới.";

    private final QuestionBankItemService itemService;
    private final QuestionBankReviewService reviewService;
    private final LeaderDepartmentResolver subjectResolver;

    public LeaderQuestionBankController(QuestionBankItemService itemService,
                                        QuestionBankReviewService reviewService,
                                        LeaderDepartmentResolver subjectResolver) {
        this.itemService = itemService;
        this.reviewService = reviewService;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping
    public String manage(@RequestParam(name = "subjectId", required = false) Long subjectId,
                         @RequestParam(name = "status", required = false) String status,
                         @RequestParam(name = "contributorId", required = false) Long contributorId,
                         @RequestParam(name = "q", required = false) String q,
                         @AuthenticationPrincipal KshUserDetails user,
                         Model model) {
        model.addAttribute(ATTR_ACTIVE_TAB, TAB_QUESTION_BANK);
        addSubjectChrome(user, model);
        boolean empty = !itemService.hasSubject(user.getId(), user.getRole());
        model.addAttribute(ATTR_QB_EMPTY_DEPARTMENT, empty);
        if (!empty) {
            var subjects = itemService.subjectOptions(user.getId(), user.getRole());
            Long selectedSubjectId = subjectId != null ? subjectId
                    : subjects.stream().findFirst().map(subject -> subject.id()).orElse(null);
            String effectiveStatus = status == null ? "REVIEW" : status;
            model.addAttribute("subjectOptions", subjects);
            model.addAttribute("selectedSubjectId", selectedSubjectId);
            model.addAttribute("subjectReview",
                    itemService.reviewView(user.getId(), user.getRole(), selectedSubjectId,
                            effectiveStatus, contributorId, q));
            status = effectiveStatus;
        }
        model.addAttribute(ATTR_QB_SELECTED_STATUS, status);
        model.addAttribute(ATTR_QB_SELECTED_CONTRIBUTOR_ID, contributorId);
        model.addAttribute(ATTR_QB_QUERY, q);
        return VIEW_QB_SUBJECT_REVIEW;
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, ReviewFilters filters,
                          @AuthenticationPrincipal KshUserDetails user,
                          RedirectAttributes redirect) {
        return executeReviewAction(filters, redirect,
                () -> reviewService.approve(user.getId(), id), MSG_QB_APPROVED);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(name = "note", required = false) String note,
                         ReviewFilters filters,
                         @AuthenticationPrincipal KshUserDetails user,
                         RedirectAttributes redirect) {
        return executeReviewAction(filters, redirect,
                () -> reviewService.reject(user.getId(), id, note), MSG_QB_REJECTED);
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          @RequestParam(name = "note", required = false) String note,
                          ReviewFilters filters,
                          @AuthenticationPrincipal KshUserDetails user,
                          RedirectAttributes redirect) {
        return executeReviewAction(filters, redirect,
                () -> reviewService.archive(user.getId(), id, note), MSG_QB_ARCHIVED);
    }

    @PostMapping("/{id}/unarchive")
    public String unarchive(@PathVariable Long id, ReviewFilters filters,
                            @AuthenticationPrincipal KshUserDetails user,
                            RedirectAttributes redirect) {
        return executeReviewAction(filters, redirect,
                () -> reviewService.unarchive(user.getId(), id), MSG_QB_UNARCHIVED);
    }

    private static String executeReviewAction(ReviewFilters filters,
                                              RedirectAttributes redirect,
                                              Runnable action,
                                              String successMessage) {
        try {
            action.run();
            redirect.addFlashAttribute("flashSuccess", successMessage);
        } catch (QuestionBankValidationException ex) {
            // A decision submitted from a stale review screen is a normal UX
            // conflict, not an internal-server error. Reload the filtered inbox.
            redirect.addFlashAttribute("flashError", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            // Covers the narrower race where two transactions overlap at flush.
            redirect.addFlashAttribute("flashError", MSG_REVIEW_CONFLICT);
        }
        return redirectReview(filters, redirect);
    }

    private void addSubjectChrome(KshUserDetails user, Model model) {
        Department subject = subjectResolver.resolve(user.getId()).orElse(null);
        model.addAttribute(ATTR_LEADER_DEPARTMENT, subject == null ? null
                : new DepartmentSummary(subject.getId(), subject.getCode(), subject.getName()));
    }

    public record ReviewFilters(Long subjectId, String status, Long contributorId, String q) {
        public ReviewFilters(String status, Long contributorId, String q) {
            this(null, status, contributorId, q);
        }
    }

    static String redirectReview(ReviewFilters filters, RedirectAttributes redirect) {
        if (filters.subjectId() != null) {
            redirect.addAttribute("subjectId", filters.subjectId());
        }
        if (filters.status() != null && !filters.status().isBlank()) {
            redirect.addAttribute("status", filters.status());
        }
        if (filters.contributorId() != null) {
            redirect.addAttribute("contributorId", filters.contributorId());
        }
        if (filters.q() != null && !filters.q().isBlank()) {
            redirect.addAttribute("q", filters.q());
        }
        return "redirect:" + URL_LEADER_QUESTION_BANK_MANAGE;
    }
}
