package com.ksh.features.practice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.dto.PracticeDtos.PracticePdfDraftView;
import com.ksh.features.practice.dto.PracticeDtos.PracticePdfImportResult;
import com.ksh.features.practice.dto.PracticeDtos.PracticeAttemptResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetView;
import com.ksh.features.practice.service.PracticeService;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.entities.User;
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

import com.ksh.entities.PracticeSubmission;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/practice")
@PreAuthorize("isAuthenticated()")
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    private final PracticeService practiceService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository;

    public PracticeController(PracticeService practiceService,
                              UserRepository userRepository,
                              ObjectMapper objectMapper,
                              com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository) {
        this.practiceService = practiceService;
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
        List<PracticeSubmission> submissions = practiceService.getAttempts(setId, user.getId()).stream()
                .filter(s -> !PracticeSubmission.STATUS_IN_PROGRESS.equals(s.getStatus()))
                .toList();
        model.addAttribute("view", view);
        model.addAttribute("submissions", submissions);
        return "practice/set-detail";
    }

    @GetMapping(Routes.TEST_DETAIL)
    public String testDetail(@PathVariable Long setId,
                             @PathVariable Long testId,
                             @AuthenticationPrincipal KshUserDetails user,
                             Model model) {
        PracticeSetView view = practiceService.getPractice(setId);
        List<PracticeSubmission> allAttempts = practiceService.getAttempts(setId, user.getId());
        List<PracticeSubmission> attempts = allAttempts.stream()
                .filter(s -> !PracticeSubmission.STATUS_IN_PROGRESS.equals(s.getStatus()))
                .toList();

        java.util.Optional<PracticeSubmission> inProgressAttempt = allAttempts.stream()
                .filter(s -> PracticeSubmission.STATUS_IN_PROGRESS.equals(s.getStatus()))
                .findFirst();

        if (inProgressAttempt.isPresent()) {
            model.addAttribute("inProgressAttempt", inProgressAttempt.get());
        }

        model.addAttribute("view", view);
        model.addAttribute("testId", testId);
        model.addAttribute("attempts", attempts);

        java.math.BigDecimal bestScore = attempts.stream()
                .map(PracticeSubmission::getScore)
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .orElse(java.math.BigDecimal.ZERO);
        model.addAttribute("bestScore", bestScore);

        return "practice/test-detail";
    }

    @PostMapping("/attempts/{attemptId}/discard")
    public String discardAttempt(@PathVariable Long attemptId,
                                 @RequestParam("setId") Long setId,
                                 @RequestParam("testId") Long testId,
                                 @AuthenticationPrincipal KshUserDetails user,
                                 RedirectAttributes redirectAttributes) {
        try {
            practiceService.discardAttempt(attemptId, user.getId());
            redirectAttributes.addFlashAttribute("success", "Đã hủy lượt làm bài dang dở thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi hủy lượt làm bài: " + e.getMessage());
        }
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
                                @RequestParam(value = "mode", defaultValue = "practice") String mode,
                                @AuthenticationPrincipal KshUserDetails user) {
        Long attemptId = practiceService.startAttempt(setId, user.getId());
        return "redirect:/practice/attempts/" + attemptId + "?mode=" + mode;
    }

    @GetMapping(Routes.ATTEMPT)
    public String attempt(@PathVariable Long attemptId,
                          @RequestParam(value = "mode", defaultValue = "practice") String mode,
                          @RequestParam(value = "sectionIndex", defaultValue = "0") int sectionIndex,
                          @AuthenticationPrincipal KshUserDetails user,
                          Model model) {
        PracticeSubmission submission = practiceService.getPracticeSubmission(attemptId, user.getId());
        if (!PracticeSubmission.STATUS_IN_PROGRESS.equals(submission.getStatus())) {
            log.info("[PracticeController] Attempt id={} is already submitted (status={}). Redirecting to result page.", attemptId, submission.getStatus());
            return "redirect:/practice/attempts/" + attemptId + "/result";
        }
        PracticeSetView view = practiceService.getPractice(submission.getSetId());

        List<com.ksh.entities.PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(submission.getSetId());
        
        List<com.ksh.features.practice.dto.PracticeDtos.PracticeQuestionGroupRow> filteredGroups = view.groups();
        String activeTitle = view.set().title();
        String activeSkill = view.set().skill();
        int activeDurationSeconds = 2400; // 40 mins default

        if (!sections.isEmpty() && sectionIndex < sections.size()) {
            com.ksh.entities.PracticeSection activeSec = sections.get(sectionIndex);
            activeTitle = activeSec.getTitle();
            activeSkill = activeSec.getSkill();
            activeDurationSeconds = activeSec.getDurationMinutes() != null ? activeSec.getDurationMinutes() * 60 : 2400;
            
            // Filter groups belonging to this section (or with null sectionId as fallback)
            filteredGroups = view.groups().stream()
                    .filter(g -> g.sectionId() == null || activeSec.getId().equals(g.sectionId()))
                    .toList();
        }

        // Build filtered SetView
        com.ksh.features.practice.dto.PracticeDtos.PracticeSetView filteredView = 
                new com.ksh.features.practice.dto.PracticeDtos.PracticeSetView(view.set(), filteredGroups);

        model.addAttribute("view", filteredView);
        model.addAttribute("mode", mode);
        model.addAttribute("attemptId", attemptId);
        
        // Pass timer details
        model.addAttribute("activeSectionTitle", activeTitle);
        model.addAttribute("activeSectionSkill", activeSkill);
        model.addAttribute("activeSectionDuration", activeDurationSeconds);
        model.addAttribute("sectionIndex", sectionIndex);
        model.addAttribute("totalSections", sections.size());

        return "practice/player";
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public String submitAttempt(@PathVariable Long attemptId,
                                @RequestParam(value = "mode", defaultValue = "practice") String mode,
                                @RequestParam(value = "sectionIndex", defaultValue = "0") int sectionIndex,
                                @RequestParam Map<String, String> form,
                                @AuthenticationPrincipal KshUserDetails user,
                                RedirectAttributes redirectAttributes) {
        PracticeSubmission submission = practiceService.getPracticeSubmission(attemptId, user.getId());
        List<com.ksh.entities.PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(submission.getSetId());

        if (!sections.isEmpty() && sectionIndex < sections.size() - 1) {
            // Save in-progress answers for this section and advance to rest period
            practiceService.saveInProgressAnswers(attemptId, user.getId(), form);
            return "redirect:/practice/attempts/" + attemptId + "/rest?mode=" + mode + "&nextSectionIndex=" + (sectionIndex + 1);
        } else {
            // Last section: Merge and submit final
            Long submissionId = practiceService.submitAttempt(attemptId, user.getId(), form);
            redirectAttributes.addFlashAttribute("success", "Đã nộp bài luyện tập.");
            return "redirect:/practice/attempts/" + submissionId + "/result";
        }
    }

    @GetMapping("/attempts/{attemptId}/rest")
    public String restPeriod(@PathVariable Long attemptId,
                             @RequestParam(value = "mode", defaultValue = "practice") String mode,
                             @RequestParam("nextSectionIndex") int nextSectionIndex,
                             @AuthenticationPrincipal KshUserDetails user,
                             Model model) {
        PracticeSubmission submission = practiceService.getPracticeSubmission(attemptId, user.getId());
        List<com.ksh.entities.PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(submission.getSetId());

        if (nextSectionIndex < 0 || nextSectionIndex >= sections.size()) {
            return "redirect:/practice/attempts/" + attemptId + "?mode=" + mode;
        }

        com.ksh.entities.PracticeSection nextSec = sections.get(nextSectionIndex);
        model.addAttribute("attemptId", attemptId);
        model.addAttribute("mode", mode);
        model.addAttribute("nextSectionIndex", nextSectionIndex);
        model.addAttribute("nextSectionTitle", nextSec.getTitle());

        // Translate the skill to Korean/Vietnamese
        String KoreanSkill = switch (nextSec.getSkill()) {
            case "READING" -> "읽기 (Đọc)";
            case "LISTENING" -> "듣기 (Nghe)";
            case "WRITING" -> "쓰기 (Viết)";
            case "SPEAKING" -> "말하기 (Nói)";
            default -> nextSec.getSkill();
        };
        model.addAttribute("nextSectionSkill", KoreanSkill);
        model.addAttribute("nextSectionDuration", nextSec.getDurationMinutes());

        return "practice/rest";
    }

    @GetMapping(Routes.RESULT)
    public String attemptResult(@PathVariable Long attemptId,
                                @AuthenticationPrincipal KshUserDetails user,
                                Model model) {
        PracticeAttemptResultView result = practiceService.getAttemptResult(attemptId, user.getId());
        if (result.sections() != null && result.sections().size() == 1) {
            String skill = result.sections().get(0).skill();
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
                return "practice/result";
            }
        }
        model.addAttribute("result", result);
        model.addAttribute("attemptId", attemptId);
        return "practice/result-shell";
    }

    @GetMapping(Routes.RESULT_DETAIL)
    public String attemptResultDetail(@PathVariable Long attemptId,
                                      @AuthenticationPrincipal KshUserDetails user,
                                      Model model) {
        PracticeAttemptResultView result = practiceService.getAttemptResult(attemptId, user.getId());
        if (result.sections() != null && result.sections().size() == 1) {
            String skill = result.sections().get(0).skill();
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
                try {
                    String questionsJson = objectMapper.writeValueAsString(
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
        model.addAttribute("result", result);
        model.addAttribute("attemptId", attemptId);
        try {
            String sectionsJson = objectMapper.writeValueAsString(result.sections());
            model.addAttribute("sectionsJson", sectionsJson);
        } catch (Exception e) {
            model.addAttribute("sectionsJson", "[]");
        }
        return "practice/result-shell-detail";
    }

    @PostMapping("/attempts/{attemptId}/re-evaluate")
    public String reEvaluateAttempt(@PathVariable Long attemptId,
                                    @AuthenticationPrincipal KshUserDetails user,
                                    RedirectAttributes redirectAttributes) {
        Long refreshedSubmissionId = practiceService.reEvaluate(attemptId, user.getId());
        redirectAttributes.addFlashAttribute("success", "Đã chấm lại bài viết bằng Audit Mode.");
        return "redirect:/practice/attempts/" + refreshedSubmissionId + "/result";
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
