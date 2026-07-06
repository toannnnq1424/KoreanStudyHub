package com.ksh.features.practice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.dto.PracticeDtos.PracticePdfDraftView;
import com.ksh.features.practice.dto.PracticeDtos.PracticePdfImportResult;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetView;
import com.ksh.features.practice.service.PracticeAttemptConflictException;
import com.ksh.features.practice.service.PracticeAttemptDiscardService;
import com.ksh.features.practice.service.PracticeService;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.entities.User;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/practice")
@PreAuthorize("isAuthenticated()")
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    private final PracticeService practiceService;
    private final PracticeAttemptDiscardService attemptDiscardService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository;

    public PracticeController(PracticeService practiceService,
                              PracticeAttemptDiscardService attemptDiscardService,
                              UserRepository userRepository,
                              ObjectMapper objectMapper,
                              com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository) {
        this.practiceService = practiceService;
        this.attemptDiscardService = attemptDiscardService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.sectionRepository = sectionRepository;
    }

    public static final class Routes {
        public static final String HOME = "/practice";
        public static final String SET_DETAIL = "/sets/{setId}";
        public static final String TEST_DETAIL = "/sets/{setId}/tests/{testId}";
        public static final String TEST_MODE = "/sets/{setId}/tests/{testId}/mode";
        public static final String ATTEMPT = "/attempts/{attemptId}";
        public static final String RESULT = "/attempts/{attemptId}/result";
        public static final String RESULT_DETAIL = "/attempts/{attemptId}/result/detail";
        public static final String PROGRESS = "/progress";
    }

    @GetMapping({"", "/"})
    public String index(Model model) {
        model.addAttribute("sets", practiceService.listPublished());
        return "practice/index";
    }

    // --- Legacy Redirects ---
    @GetMapping("/{setId}")
    public String legacyDetail(@PathVariable Long setId) {
        return "redirect:/practice/sets/" + setId;
    }

    @GetMapping("/{setId}/detail")
    public String legacyDetailView(@PathVariable Long setId) {
        return "redirect:/practice/sets/" + setId;
    }

    @GetMapping("/{setId}/mode")
    public String legacyMode(@PathVariable Long setId) {
        return "redirect:/practice/sets/" + setId + "/tests/" + setId + "/mode";
    }

    @GetMapping("/{setId}/room")
    public String legacyPlayer(@PathVariable Long setId) {
        return "redirect:/practice/sets/" + setId + "/tests/" + setId;
    }

    @PostMapping("/{setId}/submit")
    public String legacySubmit(@PathVariable Long setId) {
        return "redirect:/practice/sets/" + setId;
    }

    @GetMapping("/submissions/{submissionId}")
    public String legacyResult(@PathVariable Long submissionId) {
        return "redirect:/practice/attempts/" + submissionId + "/result";
    }

    @PostMapping("/submissions/{submissionId}/re-evaluate")
    public String legacyReEvaluate(@PathVariable Long submissionId) {
        return "redirect:/practice/attempts/" + submissionId + "/result";
    }

    // --- New Flow Routing ---
    @GetMapping(Routes.SET_DETAIL)
    public String setDetail(@PathVariable Long setId,
                            @AuthenticationPrincipal KshUserDetails user,
                            Model model) {
        PracticeSetView view = practiceService.getPractice(setId);
        model.addAttribute("view", view);
        model.addAttribute("submissions", practiceService.getSetAttemptHistory(setId, user.getId()));
        return "practice/set-detail";
    }

    @GetMapping(Routes.TEST_DETAIL)
    public String testDetail(@PathVariable Long setId,
                             @PathVariable Long testId,
                             @AuthenticationPrincipal KshUserDetails user,
                             Model model) {
        PracticeSetView view = practiceService.getPractice(setId);
        List<PracticeSection> testSections = practiceService.getSectionsForTest(setId, testId);
        Map<Long, PracticeAttempt> inProgressAttempts = practiceService.getInProgressAttemptsBySection(testId, user.getId());

        model.addAttribute("view", view);
        model.addAttribute("testId", testId);
        model.addAttribute("sections", testSections);
        model.addAttribute("inProgressAttempts", inProgressAttempts);

        return "practice/test-detail";
    }

    @PostMapping("/attempts/{attemptId}/discard")
    public String discardAttempt(@PathVariable Long attemptId,
                                 @RequestParam("setId") Long setId,
                                 @RequestParam("testId") Long testId,
                                 @AuthenticationPrincipal KshUserDetails user,
                                 RedirectAttributes redirectAttributes) {
        attemptDiscardService.discardForOwner(attemptId, user.getId());
        redirectAttributes.addFlashAttribute("success", "Đã hủy lượt làm bài dang dở thành công.");
        return "redirect:/practice/sets/" + setId + "/tests/" + testId;
    }

    @GetMapping(Routes.TEST_MODE)
    public String testMode(@PathVariable Long setId,
                           @PathVariable Long testId,
                           Model model) {
        PracticeSetView view = practiceService.getPractice(setId);
        model.addAttribute("view", view);
        model.addAttribute("testId", testId);
        return "practice/mode";
    }

    @PostMapping("/sets/{setId}/tests/{testId}/attempts")
    public String createAttempt(@PathVariable Long setId,
                                @PathVariable Long testId,
                                @RequestParam("sectionId") Long sectionId,
                                @RequestParam(value = "mode", defaultValue = "practice") String mode,
                                @AuthenticationPrincipal KshUserDetails user) {
        Long attemptId = practiceService.startAttempt(setId, testId, sectionId, user.getId());
        return "redirect:/practice/attempts/" + attemptId + "?mode=" + mode;
    }

    @GetMapping("/sets/{setId}/tests/{testId}/attempts")
    public String attemptsGetFallback(@PathVariable Long setId,
                                      @PathVariable Long testId) {
        return "redirect:/practice/sets/" + setId + "/tests/" + testId;
    }

    @GetMapping(Routes.ATTEMPT)
    public String attempt(@PathVariable Long attemptId,
                          @RequestParam(value = "mode", defaultValue = "practice") String mode,
                          @AuthenticationPrincipal KshUserDetails user,
                          Model model) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            log.info("[PracticeController] Attempt id={} is already submitted (status={}). Redirecting to result page.", attemptId, attempt.getStatus());
            return "redirect:/practice/attempts/" + attemptId + "/result";
        }
        
        PracticeSection section = practiceService.getSection(attempt.getSectionId());
        if (!attempt.getSetId().equals(section.getSetId()) ||
            !attempt.getTestId().equals(section.getTestId()) ||
            !attempt.getSkill().equals(section.getSkill())) {
            throw new IllegalArgumentException("Section metadata mismatch with attempt");
        }

        PracticeSetView view = practiceService.getPractice(attempt.getSetId());
        List<com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionGroupRow> filteredGroups =
                practiceService.getQuestionGroupsForSection(attempt.getSetId(), section.getId());

        com.ksh.features.practice.dto.PracticeDtos.PracticeSetView filteredView = 
                new com.ksh.features.practice.dto.PracticeDtos.PracticeSetView(view.set(), filteredGroups);

        model.addAttribute("view", filteredView);
        model.addAttribute("mode", mode);
        model.addAttribute("attemptId", attemptId);
        
        model.addAttribute("activeSectionTitle", section.getTitle());
        model.addAttribute("activeSectionSkill", section.getSkill());
        model.addAttribute("activeSectionDuration", section.getDurationMinutes() != null ? section.getDurationMinutes() * 60 : 2400);
        model.addAttribute("sectionIndex", 0);
        model.addAttribute("totalSections", 1);

        return "practice/player";
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public String submitAttempt(@PathVariable Long attemptId,
                                @RequestParam(value = "mode", defaultValue = "practice") String mode,
                                @RequestParam Map<String, String> form,
                                @AuthenticationPrincipal KshUserDetails user,
                                RedirectAttributes redirectAttributes) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Lượt làm bài đã được nộp hoặc chấm điểm.");
        }
        practiceService.submitAttempt(attemptId, user.getId(), form);
        redirectAttributes.addFlashAttribute("success", "Đã nộp bài luyện tập.");
        return "redirect:/practice/attempts/" + attemptId + "/result";
    }

    @GetMapping(Routes.RESULT)
    public String attemptResult(@PathVariable Long attemptId,
                                @AuthenticationPrincipal KshUserDetails user,
                                Model model) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        PracticeSection section = practiceService.getSection(attempt.getSectionId());
        String skill = section.getSkill();

        if ("READING".equals(skill) || "LISTENING".equals(skill)) {
            com.ksh.features.practice.dto.PracticeDtos.ReadingListeningResultView rlResult =
                    practiceService.getReadingListeningResult(attemptId, user.getId());
            model.addAttribute("result", rlResult);
            model.addAttribute("attemptId", attemptId);
            return "practice/rl-result";
        } else {
            PracticeResultView standardResult = practiceService.getResult(attemptId, user.getId());
            model.addAttribute("result", standardResult);
            model.addAttribute("attemptId", attemptId);
            try {
                String questionsJson = "SPEAKING".equals(skill)
                        ? objectMapper.writeValueAsString(standardResult.speakingQuestionFeedbacks().isEmpty()
                                ? standardResult.answerReviews()
                                : standardResult.speakingQuestionFeedbacks())
                        : objectMapper.writeValueAsString(standardResult.questionFeedbacks());
                model.addAttribute("questionsJson", questionsJson);
            } catch (Exception e) {
                model.addAttribute("questionsJson", "[]");
            }
            return "practice/result";
        }
    }

    @GetMapping(Routes.RESULT_DETAIL)
    public String attemptResultDetail(@PathVariable Long attemptId,
                                      @RequestParam(value = "questionId", required = false) Long questionId,
                                      @AuthenticationPrincipal KshUserDetails user,
                                      Model model) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        PracticeSection section = practiceService.getSection(attempt.getSectionId());
        String skill = section.getSkill();

        if ("READING".equals(skill) || "LISTENING".equals(skill)) {
            com.ksh.features.practice.dto.PracticeDtos.ReadingListeningResultView rlResult =
                    practiceService.getReadingListeningResult(attemptId, user.getId());
            model.addAttribute("result", rlResult);
            model.addAttribute("attemptId", attemptId);
            try {
                String groupsJson = objectMapper.writeValueAsString(rlResult.groups());
                model.addAttribute("groupsJson", groupsJson);
            } catch (Exception e) {
                model.addAttribute("groupsJson", "[]");
            }
            return "practice/rl-result-detail";
        } else {
            PracticeResultView standardResult = practiceService.getResult(attemptId, user.getId());
            model.addAttribute("result", standardResult);
            model.addAttribute("attemptId", attemptId);
            Long activeQuestionId = activeWritingQuestionId(skill, standardResult, questionId);
            model.addAttribute("activeQuestionId", activeQuestionId);
            try {
                String questionsJson = "WRITING".equals(skill)
                        ? objectMapper.writeValueAsString(standardResult.questionFeedbacks())
                        : "SPEAKING".equals(skill) && !standardResult.speakingQuestionFeedbacks().isEmpty()
                        ? objectMapper.writeValueAsString(standardResult.speakingQuestionFeedbacks())
                        : objectMapper.writeValueAsString(
                            standardResult.answerReviews().stream()
                                .map(q -> {
                                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                                    m.put("questionId", q.questionId());
                                    m.put("questionNo", q.questionNo());
                                    m.put("questionType", q.questionType());
                                    m.put("prompt", q.prompt());
                                    m.put("learnerAnswer", q.learnerAnswer() == null ? "" : q.learnerAnswer());
                                    return m;
                                }).toList()
                        );
                model.addAttribute("questionsJson", questionsJson);
            } catch (Exception e) {
                model.addAttribute("questionsJson", "[]");
            }
            return "practice/result-detail";
        }
    }

    @PostMapping("/attempts/{attemptId}/re-evaluate")
    public String reEvaluateAttempt(@PathVariable Long attemptId,
                                    @RequestParam(value = "questionId", required = false) Long questionId,
                                    @AuthenticationPrincipal KshUserDetails user,
                                    RedirectAttributes redirectAttributes) {
        if (questionId == null) {
            Long refreshedSubmissionId = practiceService.reEvaluate(attemptId, user.getId());
            redirectAttributes.addFlashAttribute("success", "Đã chấm lại bài viết bằng Audit Mode.");
            return "redirect:/practice/attempts/" + refreshedSubmissionId + "/result";
        }

        try {
            Long refreshedSubmissionId = practiceService.reEvaluateQuestion(attemptId, questionId, user.getId());
            redirectAttributes.addFlashAttribute("success", "Đã chấm lại câu đã chọn.");
            return redirectToResultDetail(refreshedSubmissionId, questionId);
        } catch (PracticeAttemptConflictException ex) {
            redirectAttributes.addFlashAttribute("error", safeReEvaluationError(ex));
            return redirectToResultDetail(attemptId, questionId);
        }
    }

    private String redirectToResultDetail(Long attemptId, Long questionId) {
        String path = UriComponentsBuilder
                .fromPath("/practice/attempts/{attemptId}/result/detail")
                .queryParam("questionId", questionId)
                .buildAndExpand(attemptId)
                .toUriString();
        return "redirect:" + path;
    }

    private Long activeWritingQuestionId(String skill, PracticeResultView result, Long requestedQuestionId) {
        if (!"WRITING".equals(skill) || requestedQuestionId == null || result.questionFeedbacks() == null) {
            return null;
        }
        return result.questionFeedbacks().stream()
                .filter(row -> requestedQuestionId.equals(row.questionId()))
                .filter(row -> "ESSAY".equals(row.questionType()))
                .findFirst()
                .map(row -> requestedQuestionId)
                .orElse(null);
    }

    private String safeReEvaluationError(PracticeAttemptConflictException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("Du lieu phan hoi cu khong ho tro cham lai tung cau")) {
            return "Dữ liệu phản hồi cũ không hỗ trợ chấm lại từng câu. Vui lòng chấm lại toàn bài.";
        }
        return "Bài làm đã thay đổi trong lúc chấm. Vui lòng tải lại và thử lại.";
    }


    /** Backward-compat redirect: /practice/profile → /practice/progress */
    @GetMapping("/profile")
    public String profileRedirect() {
        return "redirect:/practice/progress";
    }

    @GetMapping(Routes.PROGRESS)
    public String progress(@AuthenticationPrincipal KshUserDetails user,
                           @RequestParam(value = "tab", defaultValue = "overview") String tab,
                           Model model) {
        User userEntity = userRepository.findById(user.getId()).orElse(null);
        String name = userEntity != null ? userEntity.getFullName() : user.getFullName();
        String avatar = userEntity != null ? userEntity.getAvatarUrl() : "";
        if (avatar == null) avatar = "";

        com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview overview =
                practiceService.getLearningProgressOverview(user.getId(), name, avatar);
        com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics analytics =
                practiceService.getPracticeAnalytics(user.getId());

        model.addAttribute("tab", tab);
        model.addAttribute("overview", overview);
        model.addAttribute("analytics", analytics);

        try {
            model.addAttribute("overviewJson", objectMapper.writeValueAsString(overview));
            model.addAttribute("analyticsJson", objectMapper.writeValueAsString(analytics));
        } catch (Exception e) {
            model.addAttribute("overviewJson", "{}");
            model.addAttribute("analyticsJson", "{}");
        }

        return "practice/progress";
    }

    @GetMapping("/manage/upload")
    @PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
    public String uploadFormRedirect() {
        return "redirect:/practice/manage/import";
    }

    @GetMapping("/manage/manual")
    @PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
    public String manualFormRedirect() {
        return "redirect:/practice/manage/create";
    }
}
