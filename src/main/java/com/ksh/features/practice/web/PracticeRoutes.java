package com.ksh.features.practice.web;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Canonical route constants and path builders for learner-facing practice pages.
 *
 * <p>This class intentionally covers only high-risk route strings shared across
 * controllers, redirects, templates/tests, and media response DTOs. It is not a
 * general UI-copy or business-constant bucket.</p>
 */
public final class PracticeRoutes {
    public static final String BASE = "/practice";
    public static final String REDIRECT_PREFIX = "redirect:";

    public static final String HOME = "";
    public static final String HOME_SLASH = "/";
    public static final String CATALOG_BATCH = "/catalog";
    public static final String SET_DETAIL = "/sets/{setId}";
    public static final String TEST_DETAIL = "/sets/{setId}/tests/{testId}";
    public static final String CREATE_ATTEMPT = "/sets/{setId}/tests/{testId}/attempts";
    public static final String SPEAKING_PREFLIGHT =
            "/sets/{setId}/tests/{testId}/sections/{sectionId}/speaking-check";
    public static final String LISTENING_PREFLIGHT =
            "/sets/{setId}/tests/{testId}/sections/{sectionId}/listening-check";
    public static final String ATTEMPT = "/attempts/{attemptId}";
    public static final String ATTEMPT_SPEAKING_PREFLIGHT = "/attempts/{attemptId}/speaking-check";
    public static final String ATTEMPT_LISTENING_PREFLIGHT = "/attempts/{attemptId}/listening-check";
    public static final String ATTEMPT_SUBMIT = "/attempts/{attemptId}/submit";
    public static final String ATTEMPT_DISCARD = "/attempts/{attemptId}/discard";
    public static final String ATTEMPT_INTERRUPT = "/attempts/{attemptId}/interrupt";
    public static final String ATTEMPT_RE_EVALUATE = "/attempts/{attemptId}/re-evaluate";
    public static final String RESULT = "/attempts/{attemptId}/result";
    public static final String RESULT_DETAIL = "/attempts/{attemptId}/result/detail";
    public static final String PROGRESS = "/progress";

    public static final String LEGACY_SET = "/{setId}";
    public static final String LEGACY_SET_DETAIL = "/{setId}/detail";
    public static final String LEGACY_MODE = "/{setId}/mode";
    public static final String LEGACY_ROOM = "/{setId}/room";
    public static final String LEGACY_SUBMIT = "/{setId}/submit";
    public static final String LEGACY_SUBMISSION_RESULT = "/submissions/{submissionId}";
    public static final String LEGACY_SUBMISSION_RE_EVALUATE = "/submissions/{submissionId}/re-evaluate";
    public static final String PROFILE = "/profile";
    public static final String MANAGE_UPLOAD = "/manage/upload";
    public static final String MANAGE_MANUAL = "/manage/manual";

    private PracticeRoutes() {
    }

    public static String setDetailPath(Long setId) {
        return BASE + "/sets/" + setId;
    }

    public static String testDetailPath(Long setId, Long testId) {
        return setDetailPath(setId) + "/tests/" + testId;
    }

    public static String attemptPath(Long attemptId) {
        return BASE + "/attempts/" + attemptId;
    }

    public static String attemptPath(Long attemptId, String mode) {
        return UriComponentsBuilder
                .fromPath(attemptPath(attemptId))
                .queryParam("mode", mode)
                .toUriString();
    }

    public static String speakingPreflightPath(Long setId, Long testId, Long sectionId) {
        return testDetailPath(setId, testId) + "/sections/" + sectionId + "/speaking-check";
    }

    public static String attemptSpeakingPreflightPath(Long attemptId) {
        return attemptPath(attemptId) + "/speaking-check";
    }

    public static String listeningPreflightPath(Long setId, Long testId, Long sectionId) {
        return testDetailPath(setId, testId) + "/sections/" + sectionId + "/listening-check";
    }

    public static String attemptListeningPreflightPath(Long attemptId) {
        return attemptPath(attemptId) + "/listening-check";
    }

    public static String resultPath(Long attemptId) {
        return attemptPath(attemptId) + "/result";
    }

    public static String resultDetailPath(Long attemptId, Long questionId) {
        return UriComponentsBuilder
                .fromPath(resultPath(attemptId) + "/detail")
                .queryParam("questionId", questionId)
                .toUriString();
    }

    public static String redirect(String path) {
        return REDIRECT_PREFIX + path;
    }

    public static String redirectToSetDetail(Long setId) {
        return redirect(setDetailPath(setId));
    }

    public static String redirectToTestDetail(Long setId, Long testId) {
        return redirect(testDetailPath(setId, testId));
    }

    public static String redirectToAttempt(Long attemptId, String mode) {
        return redirect(attemptPath(attemptId, mode));
    }

    public static String redirectToSpeakingPreflight(Long setId, Long testId, Long sectionId) {
        return redirect(speakingPreflightPath(setId, testId, sectionId));
    }

    public static String redirectToAttemptSpeakingPreflight(Long attemptId) {
        return redirect(attemptSpeakingPreflightPath(attemptId));
    }

    public static String redirectToListeningPreflight(Long setId, Long testId, Long sectionId) {
        return redirect(listeningPreflightPath(setId, testId, sectionId));
    }

    public static String redirectToAttemptListeningPreflight(Long attemptId) {
        return redirect(attemptListeningPreflightPath(attemptId));
    }

    public static String redirectToResult(Long attemptId) {
        return redirect(resultPath(attemptId));
    }

    public static String redirectToResultDetail(Long attemptId, Long questionId) {
        return redirect(resultDetailPath(attemptId, questionId));
    }

    public static String redirectToProgress() {
        return redirect(BASE + PROGRESS);
    }
}
