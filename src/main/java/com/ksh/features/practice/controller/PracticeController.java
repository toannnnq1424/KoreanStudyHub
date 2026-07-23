package com.ksh.features.practice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.dto.PracticeDtos.PracticePdfDraftView;
import com.ksh.features.practice.dto.PracticeDtos.PracticePdfImportResult;
import com.ksh.features.practice.dto.PracticeDtos.PracticeProgressPageData;
import com.ksh.features.practice.dto.PracticeDtos.PracticeResultDetailView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetView;
import com.ksh.features.practice.dto.PracticeDtos.PracticeCatalogQuery;
import com.ksh.features.practice.dto.PracticeDtos.PracticeSetTestCard;
import com.ksh.features.practice.dto.PracticeDtos.PracticeTestRow;
import com.ksh.features.practice.service.PracticeAttemptConflictException;
import com.ksh.features.practice.service.PracticeAttemptDiscardService;
import com.ksh.features.practice.service.PracticeCatalogService;
import com.ksh.features.practice.service.PracticeDetailPageService;
import com.ksh.features.practice.service.PracticeLearnerAccessService;
import com.ksh.features.practice.service.PracticeService;
import com.ksh.features.practice.service.PracticeSpeakingMediaService;
import com.ksh.features.practice.result.PracticeResultAssembler;
import com.ksh.features.practice.result.PracticeResultDetailAssembler;
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

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;

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
    private static final String SPEAKING_PREFLIGHT_SESSION_PREFIX = "practice.speaking.preflight.";
    private static final String LISTENING_PREFLIGHT_SESSION_PREFIX = "practice.listening.preflight.";

    private final PracticeService practiceService;
    private final PracticeCatalogService catalogService;
    private final PracticeDetailPageService detailPageService;
    private final PracticeLearnerAccessService learnerAccessService;
    private final PracticeAttemptDiscardService attemptDiscardService;
    private final PracticeSpeakingMediaService speakingMediaService;
    private final PracticeResultAssembler resultAssembler;
    private final PracticeResultDetailAssembler resultDetailAssembler;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository;
    private final boolean speakingMediaUploadEnabled;
    private final boolean speakingMediaPlaybackEnabled;

    public PracticeController(PracticeService practiceService,
                              PracticeCatalogService catalogService,
                              PracticeDetailPageService detailPageService,
                              PracticeLearnerAccessService learnerAccessService,
                              PracticeAttemptDiscardService attemptDiscardService,
                              PracticeSpeakingMediaService speakingMediaService,
                              PracticeResultAssembler resultAssembler,
                              PracticeResultDetailAssembler resultDetailAssembler,
                              UserRepository userRepository,
                              ObjectMapper objectMapper,
                              com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository,
                              @Value("${app.practice.speaking-media.upload-api-enabled:false}")
                              boolean speakingMediaUploadEnabled,
                              @Value("${app.practice.speaking-media.playback-api-enabled:false}")
                              boolean speakingMediaPlaybackEnabled) {
        this.practiceService = practiceService;
        this.catalogService = catalogService;
        this.detailPageService = detailPageService;
        this.learnerAccessService = learnerAccessService;
        this.attemptDiscardService = attemptDiscardService;
        this.speakingMediaService = speakingMediaService;
        this.resultAssembler = resultAssembler;
        this.resultDetailAssembler = resultDetailAssembler;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.sectionRepository = sectionRepository;
        this.speakingMediaUploadEnabled = speakingMediaUploadEnabled;
        this.speakingMediaPlaybackEnabled = speakingMediaPlaybackEnabled;
    }

    @GetMapping({PracticeRoutes.HOME, PracticeRoutes.HOME_SLASH})
    public String index(@AuthenticationPrincipal KshUserDetails user,
                        @RequestParam(value = "q", defaultValue = "") String search,
                        @RequestParam(value = "skill", defaultValue = "ALL") String skill,
                        @RequestParam(value = "classId", required = false) Long classId,
                        Model model) {
        model.addAttribute(
                PracticeModelAttributes.CATALOG,
                catalogService.loadBatch(
                        user.getId(), new PracticeCatalogQuery(search, skill, classId, 0)));
        return PracticeViews.INDEX;
    }

    @GetMapping(PracticeRoutes.CATALOG_BATCH)
    public String catalogBatch(@AuthenticationPrincipal KshUserDetails user,
                               @RequestParam(value = "q", defaultValue = "") String search,
                               @RequestParam(value = "skill", defaultValue = "ALL") String skill,
                               @RequestParam(value = "classId", required = false) Long classId,
                               @RequestParam(value = "batch", defaultValue = "1") int batch,
                               Model model) {
        model.addAttribute(
                PracticeModelAttributes.CATALOG,
                catalogService.loadBatch(
                        user.getId(), new PracticeCatalogQuery(search, skill, classId, batch)));
        return PracticeViews.CATALOG_CARDS;
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
        learnerAccessService.requireVisiblePublishedSet(setId, user.getId());
        PracticeSetView view = practiceService.getPracticeSummary(setId);
        List<PracticeSetTestCard> testCards = detailPageService.buildTestCards(
                setId, view.tests(), user.getId());
        model.addAttribute(PracticeModelAttributes.VIEW, view);
        model.addAttribute(PracticeModelAttributes.TEST_CARDS, testCards);
        model.addAttribute(PracticeModelAttributes.SET_SKILLS,
                detailPageService.collectSetSkills(testCards));
        return PracticeViews.SET_DETAIL;
    }

    @GetMapping(PracticeRoutes.TEST_DETAIL)
    public String testDetail(@PathVariable Long setId,
                             @PathVariable Long testId,
                             @AuthenticationPrincipal KshUserDetails user,
                             Model model) {
        learnerAccessService.requireVisiblePublishedSet(setId, user.getId());
        PracticeSetView view = practiceService.getPracticeSummary(setId);
        List<PracticeTestRow> tests = view.tests();
        int selectedIndex = -1;
        for (int index = 0; index < tests.size(); index++) {
            if (testId.equals(tests.get(index).id())) {
                selectedIndex = index;
                break;
            }
        }
        if (selectedIndex < 0) {
            throw new EntityNotFoundException("Bài kiểm tra không tồn tại trong bộ đề này.");
        }

        PracticeTestRow selectedTest = tests.get(selectedIndex);
        List<PracticeSection> testSections = practiceService.getSectionsForTest(setId, testId);

        model.addAttribute(PracticeModelAttributes.VIEW, view);
        model.addAttribute(PracticeModelAttributes.TEST_ID, testId);
        model.addAttribute(PracticeModelAttributes.SELECTED_TEST, selectedTest);
        model.addAttribute(PracticeModelAttributes.PREVIOUS_TEST,
                selectedIndex > 0 ? tests.get(selectedIndex - 1) : null);
        model.addAttribute(PracticeModelAttributes.NEXT_TEST,
                selectedIndex + 1 < tests.size() ? tests.get(selectedIndex + 1) : null);
        model.addAttribute(PracticeModelAttributes.SKILL_CARDS,
                detailPageService.buildSkillCards(testId, testSections, user.getId()));
        model.addAttribute(PracticeModelAttributes.SPEAKING_MEDIA_UPLOAD_ENABLED,
                speakingMediaUploadEnabled);

        return PracticeViews.TEST_DETAIL;
    }

    @PostMapping(PracticeRoutes.ATTEMPT_DISCARD)
    public String discardAttempt(@PathVariable Long attemptId,
                                 @RequestParam("setId") Long setId,
                                 @RequestParam("testId") Long testId,
                                 @AuthenticationPrincipal KshUserDetails user,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        attemptDiscardService.discardForOwner(attemptId, user.getId());
        clearSpeakingPreflight(session, attemptId);
        clearListeningPreflight(session, attemptId);
        redirectAttributes.addFlashAttribute("success", "Đã hủy lượt làm bài dang dở thành công.");
        return PracticeRoutes.redirectToTestDetail(setId, testId);
    }

    @PostMapping(PracticeRoutes.CREATE_ATTEMPT)
    public String createAttempt(@PathVariable Long setId,
                                @PathVariable Long testId,
                                @RequestParam(PracticeFormFields.SECTION_ID) Long sectionId,
                                @RequestParam(value = PracticeFormFields.MODE, defaultValue = "practice") String mode,
                                @AuthenticationPrincipal KshUserDetails user) {
        learnerAccessService.requireVisiblePublishedSet(setId, user.getId());
        PracticeSection section = requireSection(setId, testId, sectionId);
        if ("SPEAKING".equals(section.getSkill())) {
            return PracticeRoutes.redirectToSpeakingPreflight(setId, testId, sectionId);
        }
        if ("LISTENING".equals(section.getSkill())) {
            return PracticeRoutes.redirectToListeningPreflight(setId, testId, sectionId);
        }
        Long attemptId = practiceService.startAttempt(setId, testId, sectionId, user.getId());
        return PracticeRoutes.redirectToAttempt(attemptId, mode);
    }

    @GetMapping(PracticeRoutes.LISTENING_PREFLIGHT)
    public String listeningPreflight(@PathVariable Long setId,
                                     @PathVariable Long testId,
                                     @PathVariable Long sectionId,
                                     @AuthenticationPrincipal KshUserDetails user,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        learnerAccessService.requireVisiblePublishedSet(setId, user.getId());
        requireListeningSection(setId, testId, sectionId);
        try {
            addListeningPreflightModel(
                    model,
                    practiceService.getListeningPreflightDelivery(setId, testId, sectionId),
                    PracticeRoutes.listeningPreflightPath(setId, testId, sectionId));
            return PracticeViews.LISTENING_PREFLIGHT;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error",
                    "Phần Listening chưa có audio thử loa hợp lệ. Giảng viên cần cập nhật và xuất bản lại.");
            return PracticeRoutes.redirectToTestDetail(setId, testId);
        }
    }

    @PostMapping(PracticeRoutes.LISTENING_PREFLIGHT)
    public String completeListeningPreflight(@PathVariable Long setId,
                                             @PathVariable Long testId,
                                             @PathVariable Long sectionId,
                                             @AuthenticationPrincipal KshUserDetails user,
                                             HttpSession session,
                                             RedirectAttributes redirectAttributes) {
        learnerAccessService.requireVisiblePublishedSet(setId, user.getId());
        requireListeningSection(setId, testId, sectionId);
        Long attemptId = practiceService.startAttempt(setId, testId, sectionId, user.getId());
        try {
            practiceService.getAttemptListeningPreflightDelivery(attemptId, user.getId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return handleInvalidListeningDelivery(
                    attemptId, user.getId(), setId, testId, session, redirectAttributes, exception);
        }
        markListeningPreflightComplete(session, attemptId);
        return PracticeRoutes.redirectToAttempt(attemptId, "practice");
    }

    @GetMapping(PracticeRoutes.SPEAKING_PREFLIGHT)
    public String speakingPreflight(@PathVariable Long setId,
                                    @PathVariable Long testId,
                                    @PathVariable Long sectionId,
                                    @AuthenticationPrincipal KshUserDetails user,
                                    Model model) {
        learnerAccessService.requireVisiblePublishedSet(setId, user.getId());
        PracticeSection section = requireSpeakingSection(setId, testId, sectionId);
        addSpeakingPreflightModel(
                model,
                setId,
                testId,
                sectionId,
                section.getTitle(),
                PracticeRoutes.speakingPreflightPath(setId, testId, sectionId));
        return PracticeViews.SPEAKING_PREFLIGHT;
    }

    @PostMapping(PracticeRoutes.SPEAKING_PREFLIGHT)
    public String completeSpeakingPreflight(@PathVariable Long setId,
                                            @PathVariable Long testId,
                                            @PathVariable Long sectionId,
                                            @AuthenticationPrincipal KshUserDetails user,
                                            HttpSession session,
                                            RedirectAttributes redirectAttributes) {
        learnerAccessService.requireVisiblePublishedSet(setId, user.getId());
        requireSpeakingSection(setId, testId, sectionId);
        requireSpeakingUploadEnabled();
        Long attemptId = practiceService.startAttempt(setId, testId, sectionId, user.getId());
        try {
            practiceService.getSpeakingPlayerDelivery(attemptId, user.getId());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return handleInvalidSpeakingDelivery(
                    attemptId,
                    user.getId(),
                    setId,
                    testId,
                    session,
                    redirectAttributes,
                    exception);
        }
        markSpeakingPreflightComplete(session, attemptId);
        return PracticeRoutes.redirectToAttempt(attemptId, "practice");
    }

    @GetMapping(PracticeRoutes.ATTEMPT_SPEAKING_PREFLIGHT)
    public String attemptSpeakingPreflight(@PathVariable Long attemptId,
                                           @AuthenticationPrincipal KshUserDetails user,
                                           HttpSession session,
                                           RedirectAttributes redirectAttributes,
                                           Model model) {
        PracticeAttempt attempt = requireInProgressSpeakingAttempt(attemptId, user.getId());
        PracticeService.SpeakingPlayerDelivery delivery;
        try {
            delivery = practiceService.getSpeakingPlayerDelivery(attemptId, user.getId());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return handleInvalidSpeakingDelivery(
                    attemptId,
                    user.getId(),
                    attempt.getSetId(),
                    attempt.getTestId(),
                    session,
                    redirectAttributes,
                    exception);
        }
        addSpeakingPreflightModel(
                model,
                attempt.getSetId(),
                attempt.getTestId(),
                attempt.getSectionId(),
                delivery.sectionTitle(),
                PracticeRoutes.attemptSpeakingPreflightPath(attemptId));
        return PracticeViews.SPEAKING_PREFLIGHT;
    }

    @PostMapping(PracticeRoutes.ATTEMPT_SPEAKING_PREFLIGHT)
    public String completeAttemptSpeakingPreflight(@PathVariable Long attemptId,
                                                   @AuthenticationPrincipal KshUserDetails user,
                                                   HttpSession session,
                                                   RedirectAttributes redirectAttributes) {
        PracticeAttempt attempt = requireInProgressSpeakingAttempt(attemptId, user.getId());
        requireSpeakingUploadEnabled();
        try {
            practiceService.getSpeakingPlayerDelivery(attemptId, user.getId());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return handleInvalidSpeakingDelivery(
                    attemptId,
                    user.getId(),
                    attempt.getSetId(),
                    attempt.getTestId(),
                    session,
                    redirectAttributes,
                    exception);
        }
        markSpeakingPreflightComplete(session, attemptId);
        return PracticeRoutes.redirectToAttempt(attemptId, "practice");
    }

    @GetMapping(PracticeRoutes.ATTEMPT_LISTENING_PREFLIGHT)
    public String attemptListeningPreflight(@PathVariable Long attemptId,
                                            @AuthenticationPrincipal KshUserDetails user,
                                            HttpSession session,
                                            RedirectAttributes redirectAttributes,
                                            Model model) {
        PracticeAttempt attempt = requireInProgressListeningAttempt(attemptId, user.getId());
        try {
            addListeningPreflightModel(
                    model,
                    practiceService.getAttemptListeningPreflightDelivery(attemptId, user.getId()),
                    PracticeRoutes.attemptListeningPreflightPath(attemptId));
            return PracticeViews.LISTENING_PREFLIGHT;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return handleInvalidListeningDelivery(
                    attemptId,
                    user.getId(),
                    attempt.getSetId(),
                    attempt.getTestId(),
                    session,
                    redirectAttributes,
                    exception);
        }
    }

    @PostMapping(PracticeRoutes.ATTEMPT_LISTENING_PREFLIGHT)
    public String completeAttemptListeningPreflight(@PathVariable Long attemptId,
                                                    @AuthenticationPrincipal KshUserDetails user,
                                                    HttpSession session,
                                                    RedirectAttributes redirectAttributes) {
        PracticeAttempt attempt = requireInProgressListeningAttempt(attemptId, user.getId());
        try {
            practiceService.getAttemptListeningPreflightDelivery(attemptId, user.getId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return handleInvalidListeningDelivery(
                    attemptId,
                    user.getId(),
                    attempt.getSetId(),
                    attempt.getTestId(),
                    session,
                    redirectAttributes,
                    exception);
        }
        markListeningPreflightComplete(session, attemptId);
        return PracticeRoutes.redirectToAttempt(attemptId, "practice");
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
                          HttpSession session,
                          RedirectAttributes redirectAttributes,
                          Model model) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            log.info("[PracticeController] Attempt id={} is already submitted (status={}). Redirecting to result page.", attemptId, attempt.getStatus());
            return PracticeRoutes.redirectToResult(attemptId);
        }

        if ("SPEAKING".equals(attempt.getSkill())) {
            if (!speakingPreflightComplete(session, attemptId)) {
                return PracticeRoutes.redirectToAttemptSpeakingPreflight(attemptId);
            }
            requireSpeakingUploadEnabled();
            PracticeService.SpeakingPlayerDelivery speakingDelivery;
            try {
                speakingDelivery = practiceService.getSpeakingPlayerDelivery(attemptId, user.getId());
            } catch (IllegalStateException | IllegalArgumentException exception) {
                return handleInvalidSpeakingDelivery(
                        attemptId,
                        user.getId(),
                        attempt.getSetId(),
                        attempt.getTestId(),
                        session,
                        redirectAttributes,
                        exception);
            }
            try {
                model.addAttribute(
                        PracticeModelAttributes.SPEAKING_DELIVERY_JSON,
                        safeInlineJson(speakingDelivery));
            } catch (Exception exception) {
                throw new IllegalStateException("Không thể chuẩn bị dữ liệu Speaking.", exception);
            }
            model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
            model.addAttribute(PracticeModelAttributes.ACTIVE_SECTION_TITLE, speakingDelivery.sectionTitle());
            model.addAttribute(PracticeModelAttributes.SPEAKING_INTERRUPT_ACTION,
                    PracticeRoutes.BASE + "/attempts/" + attemptId + "/interrupt");
            model.addAttribute(PracticeModelAttributes.RETURN_URL,
                    PracticeRoutes.testDetailPath(attempt.getSetId(), attempt.getTestId()));
            model.addAttribute(PracticeModelAttributes.SPEAKING_MEDIA_UPLOAD_ENABLED,
                    speakingMediaUploadEnabled);
            return PracticeViews.PLAYER_SPEAKING;
        }

        if ("LISTENING".equals(attempt.getSkill())
                && !listeningPreflightComplete(session, attemptId)) {
            return PracticeRoutes.redirectToAttemptListeningPreflight(attemptId);
        }

        PracticeService.AttemptPlayerView playerView =
                practiceService.getAttemptPlayerView(attemptId, user.getId());
        PracticeService.AttemptSectionDelivery delivery = playerView.delivery();

        model.addAttribute(PracticeModelAttributes.VIEW, playerView.view());
        model.addAttribute(PracticeModelAttributes.MODE, mode);
        model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
        
        model.addAttribute(PracticeModelAttributes.ACTIVE_SECTION_TITLE, delivery.title());
        model.addAttribute(PracticeModelAttributes.ACTIVE_SECTION_SKILL, delivery.skill());
        model.addAttribute(PracticeModelAttributes.ACTIVE_SECTION_DURATION,
                delivery.durationMinutes() != null ? delivery.durationMinutes() * 60 : 2400);
        model.addAttribute(PracticeModelAttributes.SECTION_INDEX, 0);
        model.addAttribute(PracticeModelAttributes.TOTAL_SECTIONS, 1);
        model.addAttribute(PracticeModelAttributes.RETURN_URL,
                PracticeRoutes.testDetailPath(attempt.getSetId(), attempt.getTestId()));
        return "WRITING".equals(delivery.skill())
                ? PracticeViews.PLAYER_WRITING
                : PracticeViews.PLAYER;
    }

    @PostMapping(PracticeRoutes.ATTEMPT_SUBMIT)
    public String submitAttempt(@PathVariable Long attemptId,
                                @RequestParam(value = PracticeFormFields.MODE, defaultValue = "practice") String mode,
                                @RequestParam Map<String, String> form,
                                @AuthenticationPrincipal KshUserDetails user,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Lượt làm bài đã được nộp hoặc chấm điểm.");
        }
        practiceService.submitAttempt(attemptId, user.getId(), form);
        clearSpeakingPreflight(session, attemptId);
        clearListeningPreflight(session, attemptId);
        redirectAttributes.addFlashAttribute("success", "Đã nộp bài luyện tập.");
        return PracticeRoutes.redirectToResult(attemptId);
    }

    @PostMapping(PracticeRoutes.ATTEMPT_INTERRUPT)
    public org.springframework.http.ResponseEntity<Void> interruptAttempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal KshUserDetails user,
            HttpSession session) {
        requireInProgressSpeakingAttempt(attemptId, user.getId());
        attemptDiscardService.discardForOwner(attemptId, user.getId());
        clearSpeakingPreflight(session, attemptId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping(PracticeRoutes.RESULT)
    public String attemptResult(@PathVariable Long attemptId,
                                @AuthenticationPrincipal KshUserDetails user,
                                Model model) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, user.getId());
        model.addAttribute(PracticeModelAttributes.RESULT,
                resultAssembler.assemble(attemptId, user.getId()));
        model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
        if ("SPEAKING".equals(attempt.getSkill())) {
            addSpeakingMediaModel(model, user.getId(), attempt, false);
        }
        return PracticeViews.RESULT;
    }

    @GetMapping(PracticeRoutes.RESULT_DETAIL)
    public String attemptResultDetail(@PathVariable Long attemptId,
                                      @RequestParam(value = "questionId", required = false) Long questionId,
                                      @AuthenticationPrincipal KshUserDetails user,
                                      Model model) {
        PracticeResultDetailView detail = resultDetailAssembler
                .assemble(attemptId, user.getId(), questionId);
        model.addAttribute(PracticeModelAttributes.RESULT_DETAIL, detail);
        model.addAttribute(PracticeModelAttributes.ATTEMPT_ID, attemptId);
        return switch (detail.screenKind()) {
            case OBJECTIVE_DETAIL -> PracticeViews.RESULT_DETAIL_OBJECTIVE;
            case WRITING_DETAIL -> PracticeViews.RESULT_DETAIL_WRITING;
            case SPEAKING_DETAIL -> PracticeViews.RESULT_DETAIL_SPEAKING;
        };
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

    private PracticeSection requireSection(Long setId, Long testId, Long sectionId) {
        PracticeSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Phần thi không tồn tại."));
        if (!setId.equals(section.getSetId()) || !testId.equals(section.getTestId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Phần thi không thuộc bài luyện tập đã chọn.");
        }
        return section;
    }

    private PracticeSection requireSpeakingSection(Long setId, Long testId, Long sectionId) {
        PracticeSection section = requireSection(setId, testId, sectionId);
        if (!"SPEAKING".equals(section.getSkill())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Kiểm tra thiết bị chỉ áp dụng cho phần Speaking.");
        }
        return section;
    }

    private PracticeSection requireListeningSection(Long setId, Long testId, Long sectionId) {
        PracticeSection section = requireSection(setId, testId, sectionId);
        if (!"LISTENING".equals(section.getSkill())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Kiểm tra loa chỉ áp dụng cho phần Listening.");
        }
        return section;
    }

    private PracticeAttempt requireInProgressSpeakingAttempt(Long attemptId, Long userId) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, userId);
        if (!"SPEAKING".equals(attempt.getSkill())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Lượt làm bài không thuộc kỹ năng Speaking.");
        }
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Lượt Speaking đã kết thúc.");
        }
        return attempt;
    }

    private PracticeAttempt requireInProgressListeningAttempt(Long attemptId, Long userId) {
        PracticeAttempt attempt = practiceService.getPracticeAttempt(attemptId, userId);
        if (!"LISTENING".equals(attempt.getSkill())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Lượt làm bài không thuộc kỹ năng Listening.");
        }
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Lượt Listening đã kết thúc.");
        }
        return attempt;
    }

    private String safeInlineJson(Object value) throws IOException {
        return objectMapper.writeValueAsString(value)
                .replace("&", "\\u0026")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private String handleInvalidSpeakingDelivery(Long attemptId,
                                                 Long userId,
                                                 Long setId,
                                                 Long testId,
                                                 HttpSession session,
                                                 RedirectAttributes redirectAttributes,
                                                 RuntimeException exception) {
        log.warn("[PracticeController] Speaking delivery is not playable attemptId={} reason={}",
                attemptId, exception.getMessage());
        try {
            attemptDiscardService.discardForOwner(attemptId, userId);
        } catch (RuntimeException discardException) {
            log.warn("[PracticeController] Could not discard invalid Speaking attempt id={} reason={}",
                    attemptId, discardException.getMessage());
        }
        clearSpeakingPreflight(session, attemptId);
        redirectAttributes.addFlashAttribute("error",
                "Nội dung Speaking này chưa có audio hoặc thời lượng hợp lệ. Giảng viên cần cập nhật và xuất bản lại trước khi học viên làm bài.");
        return PracticeRoutes.redirectToTestDetail(setId, testId);
    }

    private String handleInvalidListeningDelivery(Long attemptId,
                                                  Long userId,
                                                  Long setId,
                                                  Long testId,
                                                  HttpSession session,
                                                  RedirectAttributes redirectAttributes,
                                                  RuntimeException exception) {
        log.warn("[PracticeController] Listening delivery is not playable attemptId={} reason={}",
                attemptId, exception.getMessage());
        try {
            attemptDiscardService.discardForOwner(attemptId, userId);
        } catch (RuntimeException discardException) {
            log.warn("[PracticeController] Could not discard invalid Listening attempt id={} reason={}",
                    attemptId, discardException.getMessage());
        }
        clearListeningPreflight(session, attemptId);
        redirectAttributes.addFlashAttribute("error",
                "Phần Listening chưa có audio thử loa hợp lệ. Giảng viên cần cập nhật và xuất bản lại.");
        return PracticeRoutes.redirectToTestDetail(setId, testId);
    }

    private void addListeningPreflightModel(
            Model model,
            PracticeService.ListeningPreflightDelivery delivery,
            String action) {
        model.addAttribute(PracticeModelAttributes.SET_ID, delivery.setId());
        model.addAttribute(PracticeModelAttributes.TEST_ID, delivery.testId());
        model.addAttribute(PracticeModelAttributes.SECTION_ID, delivery.sectionId());
        model.addAttribute(PracticeModelAttributes.SECTION_TITLE, delivery.sectionTitle());
        model.addAttribute(PracticeModelAttributes.LISTENING_CHECK_AUDIO_REFERENCE,
                delivery.checkAudioReference());
        model.addAttribute(PracticeModelAttributes.LISTENING_PREFLIGHT_ACTION, action);
        model.addAttribute(PracticeModelAttributes.RETURN_URL,
                PracticeRoutes.testDetailPath(delivery.setId(), delivery.testId()));
    }

    private void addSpeakingPreflightModel(
            Model model,
            Long setId,
            Long testId,
            Long sectionId,
            String sectionTitle,
            String action) {
        model.addAttribute(PracticeModelAttributes.SET_ID, setId);
        model.addAttribute(PracticeModelAttributes.TEST_ID, testId);
        model.addAttribute(PracticeModelAttributes.SECTION_ID, sectionId);
        model.addAttribute(PracticeModelAttributes.SECTION_TITLE, sectionTitle);
        model.addAttribute(PracticeModelAttributes.SPEAKING_PREFLIGHT_ACTION, action);
        model.addAttribute(PracticeModelAttributes.RETURN_URL,
                PracticeRoutes.testDetailPath(setId, testId));
        model.addAttribute(PracticeModelAttributes.SPEAKING_MEDIA_UPLOAD_ENABLED,
                speakingMediaUploadEnabled);
    }

    private void requireSpeakingUploadEnabled() {
        if (!speakingMediaUploadEnabled) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Chức năng thu âm Speaking chưa sẵn sàng.");
        }
    }

    private void markSpeakingPreflightComplete(HttpSession session, Long attemptId) {
        session.setAttribute(SPEAKING_PREFLIGHT_SESSION_PREFIX + attemptId, Boolean.TRUE);
    }

    private boolean speakingPreflightComplete(HttpSession session, Long attemptId) {
        return Boolean.TRUE.equals(
                session.getAttribute(SPEAKING_PREFLIGHT_SESSION_PREFIX + attemptId));
    }

    private void clearSpeakingPreflight(HttpSession session, Long attemptId) {
        session.removeAttribute(SPEAKING_PREFLIGHT_SESSION_PREFIX + attemptId);
    }

    private void markListeningPreflightComplete(HttpSession session, Long attemptId) {
        session.setAttribute(LISTENING_PREFLIGHT_SESSION_PREFIX + attemptId, Boolean.TRUE);
    }

    private boolean listeningPreflightComplete(HttpSession session, Long attemptId) {
        return Boolean.TRUE.equals(
                session.getAttribute(LISTENING_PREFLIGHT_SESSION_PREFIX + attemptId));
    }

    private void clearListeningPreflight(HttpSession session, Long attemptId) {
        session.removeAttribute(LISTENING_PREFLIGHT_SESSION_PREFIX + attemptId);
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

    private String safeReEvaluationError(PracticeAttemptConflictException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("Du lieu phan hoi cu khong ho tro cham lai tung cau")) {
            return "Dữ liệu phản hồi cũ không hỗ trợ chấm lại từng câu. Vui lòng chấm lại toàn bài.";
        }
        return "Bài làm đã thay đổi trong lúc chấm. Vui lòng tải lại và thử lại.";
    }

    @GetMapping(PracticeRoutes.PROFILE)
    @PreAuthorize(Roles.PREAUTH_STUDENT)
    public String profileRedirect() {
        return PracticeRoutes.redirectToProgress();
    }

    @GetMapping(PracticeRoutes.PROGRESS)
    @PreAuthorize(Roles.PREAUTH_STUDENT)
    public String progress(@AuthenticationPrincipal KshUserDetails user,
                           @RequestParam(value = "tab", defaultValue = "overview") String tab,
                           Model model) {
        User userEntity = userRepository.findById(user.getId()).orElse(null);
        String name = userEntity != null ? userEntity.getFullName() : user.getFullName();
        String avatar = userEntity != null ? userEntity.getAvatarUrl() : "";
        if (avatar == null) avatar = "";

        PracticeProgressPageData pageData =
                practiceService.getProgressPageData(user.getId(), name, avatar);
        com.ksh.features.practice.dto.PracticeDtos.LearningProgressOverview overview =
                pageData.overview();
        com.ksh.features.practice.dto.PracticeDtos.PracticeAnalytics analytics =
                pageData.analytics();

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
    @PreAuthorize(Roles.PREAUTH_LECTURER)
    public String uploadFormRedirect() {
        return "redirect:/practice/manage/import";
    }

    @GetMapping(PracticeRoutes.MANAGE_MANUAL)
    @PreAuthorize(Roles.PREAUTH_LECTURER)
    public String manualFormRedirect() {
        return "redirect:/practice/manage/create";
    }
}
