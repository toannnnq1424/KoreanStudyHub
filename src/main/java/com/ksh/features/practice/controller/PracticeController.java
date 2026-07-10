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
import com.ksh.features.practice.service.PracticeSpeakingMediaService;
import com.ksh.features.practice.web.PracticeFormFields;
import com.ksh.features.practice.web.PracticeModelAttributes;
import com.ksh.features.practice.web.PracticeRoutes;
import com.ksh.features.practice.web.PracticeViews;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping(PracticeRoutes.BASE)
@PreAuthorize("isAuthenticated()")
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    private final PracticeService practiceService;
    private final PracticeAttemptDiscardService attemptDiscardService;
    private final PracticeSpeakingMediaService speakingMediaService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository;
    private final boolean speakingMediaUploadEnabled;
    private final boolean speakingMediaPlaybackEnabled;

    public PracticeController(PracticeService practiceService,
                              PracticeAttemptDiscardService attemptDiscardService,
                              PracticeSpeakingMediaService speakingMediaService,
                              UserRepository userRepository,
                              ObjectMapper objectMapper,
                              com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository,
                              @Value("${app.practice.speaking-media.upload-api-enabled:false}")
                              boolean speakingMediaUploadEnabled,
                              @Value("${app.practice.speaking-media.playback-api-enabled:false}")
                              boolean speakingMediaPlaybackEnabled) {
        this.practiceService = practiceService;
        this.attemptDiscardService = attemptDiscardService;
        this.speakingMediaService = speakingMediaService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.sectionRepository = sectionRepository;
        this.speakingMediaUploadEnabled = speakingMediaUploadEnabled;
        this.speakingMediaPlaybackEnabled = speakingMediaPlaybackEnabled;
    }

    @GetMapping({PracticeRoutes.HOME, PracticeRoutes.HOME_SLASH})
    public String index(Model model) {
        model.addAttribute(PracticeModelAttributes.SETS, practiceService.listPublished());
        return PracticeViews.INDEX;
    }

    // --- Legacy Redirects ---
    @GetMapping(PracticeRoutes.LEGACY_SET)
    public String legacyDetail(@PathVariable Long setId) {
        return PracticeRoutes.redirectToSetDetail(setId);
    }

    @GetMapping(PracticeRoutes.LEGACY_SET_DETAIL)
    public String legacyDetailView(@PathVariable Long setId) {
        return PracticeRoutes.redirectToSetDetail(setId);
    }

    @GetMapping(PracticeRoutes.LEGACY_MODE)
    public String legacyMode(@PathVariable Long setId) {
        return PracticeRoutes.redirectToSetDetail(setId);
    }

    @GetMapping(PracticeRoutes.LEGACY_ROOM)
    public String legacyPlayer(@PathVariable Long setId) {
        return PracticeRoutes.redirectToSetDetail(setId);
    }

    @PostMapping(PracticeRoutes.LEGACY_SUBMIT)
    public String legacySubmit(@PathVariable Long setId) {
        return PracticeRoutes.redirectToSetDetail(setId);
    }

    @GetMapping(PracticeRoutes.LEGACY_SUBMISSION_RESULT)
    public String legacyResult(@PathVariable Long submissionId) {
        return PracticeRoutes.redirectToResult(submissionId);
    }

    @PostMapping(PracticeRoutes.LEGACY_SUBMISSION_RE_EVALUATE)
    public String legacyReEvaluate(@PathVariable Long submissionId) {
        return PracticeRoutes.redirectToResult(submissionId);
    }

    // --- New Flow Routing ---
    @GetMapping(PracticeRoutes.SET_DETAIL)
    public String setDetail(@PathVariable Long setId,
                            @AuthenticationPrincipal KshUserDetails user,
                            Model model) {
        PracticeSetView view = practiceService.getPractice(setId);
        model.addAttribute(PracticeModelAttributes.VIEW, view);
        model.addAttribute(PracticeModelAttributes.SUBMISSIONS, practiceService.getSetAttemptHistory(setId, user.getId()));
        return PracticeViews.SET_DETAIL;
    }

    @GetMapping(PracticeRoutes.TEST_DETAIL)
    public String testDetail(@PathVariable Long setId,
                             @PathVariable Long testId,
                             @AuthenticationPrincipal KshUserDetails user,
                             Model model) {
        PracticeSetView view = practiceService.getPractice(setId);
        List<PracticeSection> testSections = practiceService.getSectionsForTest(setId, testId);
        Map<Long, PracticeAttempt> inProgressAttempts = practiceService.getInProgressAttemptsBySection(testId, user.getId());

        model.addAttribute(PracticeModelAttributes.VIEW, view);
        model.addAttribute(PracticeModelAttributes.TEST_ID, testId);
        model.addAttribute(PracticeModelAttributes.SECTIONS, testSections);
        model.addAttribute(PracticeModelAttributes.IN_PROGRESS_ATTEMPTS, inProgressAttempts);

        return PracticeViews.TEST_DETAIL;
    }

    @PostMapping(PracticeRoutes.ATTEMPT_DISCARD)
    public String discardAttempt(@PathVariable Long attemptId,
                                 @RequestParam("setId") Long setId,
                                 @RequestParam("testId") Long testId,
                                 @AuthenticationPrincipal KshUserDetails user,
                                 RedirectAttributes redirectAttributes) {
        attemptDiscardService.discardForOwner(attemptId, user.getId());
        redirectAttributes.addFlashAttribute("success", "Đã hủy lượt làm bài dang dở thành công.");
        return PracticeRoutes.redirectToTestDetail(setId, testId);
    }

    @GetMapping(PracticeRoutes.TEST_MODE)
    public String testMode(@PathVariable Long setId,
                           @PathVariable Long testId,
                           Model model) {
        PracticeSetView view = practiceService.getPractice(setId);
        model.addAttribute(PracticeModelAttributes.VIEW, view);
        model.addAttribute(PracticeModelAttributes.TEST_ID, testId);
        model.addAttribute(PracticeModelAttributes.SECTIONS, practiceService.getSectionsForTest(setId, testId));
        return PracticeViews.MODE;
    }

    @PostMapping(PracticeRoutes.CREATE_ATTEMPT)
    public String createAttempt(@PathVariable Long setId,
                                @PathVariable Long testId,
                                @RequestParam(PracticeFormFields.SECTION_ID) Long sectionId,
                                @RequestParam(value = PracticeFormFields.MODE, defaultValue = "practice") String mode,
                                @AuthenticationPrincipal KshUserDetails user) {
        Long attemptId = practiceService.startAttempt(setId, testId, sectionId, user.getId());
        return PracticeRoutes.redirectToAttempt(attemptId, mode);
    }

    @GetMapping(PracticeRoutes.CREATE_ATTEMPT)
    public String attemptsGetFallback(@PathVariable Long setId,
                                      @PathVariable Long testId) {
        return PracticeRoutes.redirectToTestDetail(setId, testId);
    }

    @GetMapping(PracticeRoutes.ATTEMPT)
    public String attempt(@PathVariable Long attemptId,
                          @RequestParam(value = PracticeFormFields.MODE, defaultValue = "practice") String mode,
                          @AuthenticationPrincipal KshUserDetails user,
                          Model model) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            log.info("[PracticeController] Attempt id={} is already submitted (status={}). Redirecting to result page.", attemptId, attempt.getStatus());
            return PracticeRoutes.redirectToResult(attemptId);
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

        model.addAttribute(PracticeModelAttributes.VIEW, filteredView);
        model.addAttribute(PracticeModelAttributes.MODE, mode);
        model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
        
        model.addAttribute(PracticeModelAttributes.ACTIVE_SECTION_TITLE, section.getTitle());
        model.addAttribute(PracticeModelAttributes.ACTIVE_SECTION_SKILL, section.getSkill());
        model.addAttribute(PracticeModelAttributes.ACTIVE_SECTION_DURATION, section.getDurationMinutes() != null ? section.getDurationMinutes() * 60 : 2400);
        model.addAttribute(PracticeModelAttributes.SECTION_INDEX, 0);
        model.addAttribute(PracticeModelAttributes.TOTAL_SECTIONS, 1);
        addSpeakingMediaModel(model, user.getId(), attempt, true);

        return PracticeViews.PLAYER;
    }

    @PostMapping(PracticeRoutes.ATTEMPT_SUBMIT)
    public String submitAttempt(@PathVariable Long attemptId,
                                @RequestParam(value = PracticeFormFields.MODE, defaultValue = "practice") String mode,
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
        return PracticeRoutes.redirectToResult(attemptId);
    }

    @GetMapping(PracticeRoutes.RESULT)
    public String attemptResult(@PathVariable Long attemptId,
                                @AuthenticationPrincipal KshUserDetails user,
                                Model model) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        PracticeSection section = practiceService.getSection(attempt.getSectionId());
        String skill = section.getSkill();

        if ("READING".equals(skill) || "LISTENING".equals(skill)) {
            com.ksh.features.practice.dto.PracticeDtos.ReadingListeningResultView rlResult =
                    practiceService.getReadingListeningResult(attemptId, user.getId());
            model.addAttribute(PracticeModelAttributes.RESULT, rlResult);
            model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
            addSpeakingMediaModel(model, user.getId(), attempt, false);
            return PracticeViews.READING_LISTENING_RESULT;
        } else {
            PracticeResultView standardResult = practiceService.getResult(attemptId, user.getId());
            model.addAttribute(PracticeModelAttributes.RESULT, standardResult);
            model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
            addSpeakingMediaModel(model, user.getId(), attempt, false);
            try {
                String questionsJson = "SPEAKING".equals(skill)
                        ? objectMapper.writeValueAsString(standardResult.speakingQuestionFeedbacks().isEmpty()
                                ? standardResult.answerReviews()
                                : standardResult.speakingQuestionFeedbacks())
                        : objectMapper.writeValueAsString(standardResult.questionFeedbacks());
                model.addAttribute(PracticeModelAttributes.QUESTIONS_JSON, questionsJson);
            } catch (Exception e) {
                model.addAttribute(PracticeModelAttributes.QUESTIONS_JSON, "[]");
            }
            return PracticeViews.RESULT;
        }
    }

    @GetMapping(PracticeRoutes.RESULT_DETAIL)
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
            model.addAttribute(PracticeModelAttributes.RESULT, rlResult);
            model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
            try {
                String groupsJson = objectMapper.writeValueAsString(rlResult.groups());
                model.addAttribute(PracticeModelAttributes.GROUPS_JSON, groupsJson);
            } catch (Exception e) {
                model.addAttribute(PracticeModelAttributes.GROUPS_JSON, "[]");
            }
            return PracticeViews.READING_LISTENING_RESULT_DETAIL;
        } else {
            PracticeResultView standardResult = practiceService.getResult(attemptId, user.getId());
            model.addAttribute(PracticeModelAttributes.RESULT, standardResult);
            model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
            addSpeakingMediaModel(model, user.getId(), attempt, false);
            Long activeQuestionId = activeWritingQuestionId(skill, standardResult, questionId);
            model.addAttribute(PracticeModelAttributes.ACTIVE_QUESTION_ID, activeQuestionId);
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
                model.addAttribute(PracticeModelAttributes.QUESTIONS_JSON, questionsJson);
            } catch (Exception e) {
                model.addAttribute(PracticeModelAttributes.QUESTIONS_JSON, "[]");
            }
            return PracticeViews.RESULT_DETAIL;
        }
    }

    @PostMapping(PracticeRoutes.ATTEMPT_RE_EVALUATE)
    public String reEvaluateAttempt(@PathVariable Long attemptId,
                                    @RequestParam(value = "questionId", required = false) Long questionId,
                                    @AuthenticationPrincipal KshUserDetails user,
                                    RedirectAttributes redirectAttributes) {
        if (questionId == null) {
            Long refreshedSubmissionId = practiceService.reEvaluate(attemptId, user.getId());
            redirectAttributes.addFlashAttribute("success", "Đã chấm lại bài viết bằng Audit Mode.");
            return PracticeRoutes.redirectToResult(refreshedSubmissionId);
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
        return PracticeRoutes.redirectToResultDetail(attemptId, questionId);
    }

    private void addSpeakingMediaModel(
            Model model, Long userId, PracticeAttempt attempt, boolean includeUploadGate) {
        boolean speaking = "SPEAKING".equals(attempt.getSkill());
        var media = speaking
                ? speakingMediaService.findReadyMediaViewsForOwner(userId, attempt.getId())
                : List.<com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaView>of();
        model.addAttribute(PracticeModelAttributes.SPEAKING_MEDIA, media);
        model.addAttribute(PracticeModelAttributes.SPEAKING_MEDIA_BY_QUESTION_ID, media.stream().collect(Collectors.toMap(
                com.ksh.features.practice.dto.PracticeDtos.SpeakingMediaView::questionId,
                Function.identity())));
        model.addAttribute(PracticeModelAttributes.SPEAKING_MEDIA_PLAYBACK_ENABLED, speakingMediaPlaybackEnabled);
        if (includeUploadGate) {
            model.addAttribute(PracticeModelAttributes.SPEAKING_MEDIA_UPLOAD_ENABLED, speakingMediaUploadEnabled);
        }
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

    @GetMapping(PracticeRoutes.PROFILE)
    public String profileRedirect() {
        return PracticeRoutes.redirectToProgress();
    }

    @GetMapping(PracticeRoutes.PROGRESS)
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

        model.addAttribute(PracticeModelAttributes.TAB, tab);
        model.addAttribute(PracticeModelAttributes.OVERVIEW, overview);
        model.addAttribute(PracticeModelAttributes.ANALYTICS, analytics);

        try {
            model.addAttribute(PracticeModelAttributes.OVERVIEW_JSON, objectMapper.writeValueAsString(overview));
            model.addAttribute(PracticeModelAttributes.ANALYTICS_JSON, objectMapper.writeValueAsString(analytics));
        } catch (Exception e) {
            model.addAttribute(PracticeModelAttributes.OVERVIEW_JSON, "{}");
            model.addAttribute(PracticeModelAttributes.ANALYTICS_JSON, "{}");
        }

        return PracticeViews.PROGRESS;
    }

    @GetMapping(PracticeRoutes.MANAGE_UPLOAD)
    @PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
    public String uploadFormRedirect() {
        return "redirect:/practice/manage/import";
    }

    @GetMapping(PracticeRoutes.MANAGE_MANUAL)
    @PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
    public String manualFormRedirect() {
        return "redirect:/practice/manage/create";
    }
}
