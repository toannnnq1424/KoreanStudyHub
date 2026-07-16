package com.ksh.features.tests.controller;

import com.ksh.features.tests.dto.TestDtos.ResultView;
import com.ksh.features.tests.dto.TestDtos.ReviewView;
import com.ksh.features.tests.dto.TestDtos.StudentExamList;
import com.ksh.features.tests.dto.TestDtos.TakeView;
import com.ksh.features.tests.service.TestAttemptService;
import com.ksh.features.tests.service.TestCatalogService;
import com.ksh.security.KshUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static com.ksh.common.IConstant.ATTR_EXAMS_PAGE;
import static com.ksh.common.IConstant.ATTR_RESULT;
import static com.ksh.common.IConstant.ATTR_REVIEW;
import static com.ksh.common.IConstant.ATTR_TAKE;
import static com.ksh.common.IConstant.BASE_MY_TESTS;
import static com.ksh.common.IConstant.VIEW_TEST_LIST;
import static com.ksh.common.IConstant.VIEW_TEST_RESULT;
import static com.ksh.common.IConstant.VIEW_TEST_REVIEW;
import static com.ksh.common.IConstant.VIEW_TEST_TAKE;

/**
 * Student-facing SSR controller for online exams under {@code /my/tests}: list,
 * take, result and review. Any authenticated user reaches these; per-exam access
 * is enforced in the service via {@code TestAccessResolver} (404 no-leak,
 * per-user attempt state).
 */
@Controller
@RequestMapping(BASE_MY_TESTS)
@PreAuthorize("isAuthenticated()")
public class StudentTestController {

    private final TestCatalogService catalogService;
    private final TestAttemptService attemptService;

    public StudentTestController(TestCatalogService catalogService,
                                 TestAttemptService attemptService) {
        this.catalogService = catalogService;
        this.attemptService = attemptService;
    }

    /** Lists the student's accessible exams (SSR numbered pager). */
    @GetMapping
    public String list(@RequestParam(name = "page", defaultValue = "0") int page,
                       @AuthenticationPrincipal KshUserDetails user, Model model) {
        StudentExamList list = catalogService.listForStudent(user.getId(), page);
        model.addAttribute(ATTR_EXAMS_PAGE, list.exams());
        return VIEW_TEST_LIST;
    }

    /** Starts or resumes an attempt and renders the taking screen. */
    @GetMapping("/{id}/take")
    public String take(@PathVariable Long id,
                       @AuthenticationPrincipal KshUserDetails user, Model model) {
        TakeView take = attemptService.startOrResume(id, user.getId());
        model.addAttribute(ATTR_TAKE, take);
        return VIEW_TEST_TAKE;
    }

    /** Owner-only result summary for a submitted attempt (else 404). */
    @GetMapping("/{id}/result/{attemptId}")
    public String result(@PathVariable Long id, @PathVariable Long attemptId,
                         @AuthenticationPrincipal KshUserDetails user, Model model) {
        ResultView result = attemptService.result(id, attemptId, user.getId());
        model.addAttribute(ATTR_RESULT, result);
        return VIEW_TEST_RESULT;
    }

    /** Owner-only per-question review for a submitted attempt (else 404). */
    @GetMapping("/{id}/review/{attemptId}")
    public String review(@PathVariable Long id, @PathVariable Long attemptId,
                         @AuthenticationPrincipal KshUserDetails user, Model model) {
        ReviewView review = attemptService.review(id, attemptId, user.getId());
        model.addAttribute(ATTR_REVIEW, review);
        return VIEW_TEST_REVIEW;
    }
}
