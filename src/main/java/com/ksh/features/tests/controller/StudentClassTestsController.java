package com.ksh.features.tests.controller;

import com.ksh.features.tests.dto.TestDtos.ClassTestsView;
import com.ksh.features.tests.dto.TestDtos.ResultView;
import com.ksh.features.tests.dto.TestDtos.ReviewView;
import com.ksh.features.tests.dto.TestDtos.StudentTestDetail;
import com.ksh.features.tests.dto.TestDtos.TakeView;
import com.ksh.features.tests.service.TestCatalogService;
import com.ksh.features.tests.service.TestAttemptService;
import com.ksh.features.tests.service.TestAttemptUnavailableException;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.ksh.common.IConstant.ATTR_PAGER_PARAMS;
import static com.ksh.common.IConstant.ATTR_VIEW;
import static com.ksh.common.IConstant.ATTR_DETAIL;
import static com.ksh.common.IConstant.ATTR_TAKE;
import static com.ksh.common.IConstant.ATTR_RESULT;
import static com.ksh.common.IConstant.ATTR_REVIEW;
import static com.ksh.common.IConstant.ATTR_FLASH_ERROR;
import static com.ksh.common.IConstant.VIEW_STUDENT_CLASS_TESTS;
import static com.ksh.common.IConstant.VIEW_TEST_DETAIL;
import static com.ksh.common.IConstant.VIEW_TEST_TAKE;
import static com.ksh.common.IConstant.VIEW_TEST_RESULT;
import static com.ksh.common.IConstant.VIEW_TEST_REVIEW;

/**
 * Student-facing SSR controller for a single class's PUBLISHED exams under
 * {@code /my/classes/{classId}/tests}. Mirrors the class lessons shell: the
 * shared class sidebar on the left, a searchable + paginated list of the
 * class's tests on the right.
 *
 * <p>Access is gated in {@link TestCatalogService#listClassTests} — the caller
 * must be ACTIVE-enrolled in the class, else 404 (existence never leaked).
 */
@Controller
@RequestMapping("/my/classes/{classId}/tests")
@PreAuthorize(Roles.PREAUTH_STUDENT)
public class StudentClassTestsController {

    private final TestCatalogService catalogService;
    private final TestAttemptService attemptService;

    public StudentClassTestsController(TestCatalogService catalogService,
                                       TestAttemptService attemptService) {
        this.catalogService = catalogService;
        this.attemptService = attemptService;
    }

    /** Lists the class's PUBLISHED exams, title-filtered by {@code ?q} and paged. */
    @GetMapping
    public String list(@PathVariable Long classId,
                       @RequestParam(name = "q", required = false) String q,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @AuthenticationPrincipal KshUserDetails user, Model model) {
        ClassTestsView view = catalogService.listClassTests(classId, user.getId(), q, page);
        model.addAttribute(ATTR_VIEW, view);
        // Preserve the search term across pager links (null-safe: PageWindow skips blanks).
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", view.query());
        model.addAttribute(ATTR_PAGER_PARAMS, params);
        return VIEW_STUDENT_CLASS_TESTS;
    }

    @GetMapping("/{testId}")
    public String detail(@PathVariable Long classId, @PathVariable Long testId,
                         @AuthenticationPrincipal KshUserDetails user, Model model) {
        StudentTestDetail detail = catalogService.detailForStudent(testId, user.getId());
        requireClassScope(classId, detail.classId());
        model.addAttribute(ATTR_DETAIL, detail);
        model.addAttribute("classScopeId", classId);
        return VIEW_TEST_DETAIL;
    }

    @PostMapping("/{testId}/start")
    public String start(@PathVariable Long classId, @PathVariable Long testId,
                        @AuthenticationPrincipal KshUserDetails user,
                        RedirectAttributes ra) {
        try {
            attemptService.startOrResumeInClass(testId, classId, user.getId());
            return "redirect:/my/classes/" + classId + "/tests/" + testId + "/take";
        } catch (TestAttemptUnavailableException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
            return "redirect:/my/classes/" + classId + "/tests/" + testId;
        }
    }

    @GetMapping("/{testId}/take")
    public String take(@PathVariable Long classId, @PathVariable Long testId,
                       @AuthenticationPrincipal KshUserDetails user, Model model,
                       RedirectAttributes ra) {
        try {
            TakeView take = attemptService.resumeForTake(testId, user.getId());
            requireClassScope(classId, take.classId());
            model.addAttribute(ATTR_TAKE, take);
            model.addAttribute("classScopeId", classId);
            return VIEW_TEST_TAKE;
        } catch (TestAttemptUnavailableException ex) {
            ra.addFlashAttribute(ATTR_FLASH_ERROR, ex.getMessage());
            return "redirect:/my/classes/" + classId + "/tests/" + testId;
        }
    }

    @GetMapping("/{testId}/result/{attemptId}")
    public String result(@PathVariable Long classId, @PathVariable Long testId, @PathVariable Long attemptId,
                         @AuthenticationPrincipal KshUserDetails user, Model model) {
        ResultView result = attemptService.result(testId, attemptId, user.getId());
        requireClassScope(classId, result.classId());
        model.addAttribute(ATTR_RESULT, result);
        model.addAttribute("classScopeId", classId);
        return VIEW_TEST_RESULT;
    }

    @GetMapping("/{testId}/review/{attemptId}")
    public String review(@PathVariable Long classId, @PathVariable Long testId, @PathVariable Long attemptId,
                         @AuthenticationPrincipal KshUserDetails user, Model model) {
        ReviewView review = attemptService.review(testId, attemptId, user.getId());
        requireClassScope(classId, review.classId());
        model.addAttribute(ATTR_REVIEW, review);
        model.addAttribute("classScopeId", classId);
        return VIEW_TEST_REVIEW;
    }

    private static void requireClassScope(Long expectedClassId, Long actualClassId) {
        if (!expectedClassId.equals(actualClassId)) {
            throw new jakarta.persistence.EntityNotFoundException("Không tìm thấy bài test trong lớp này.");
        }
    }
}
