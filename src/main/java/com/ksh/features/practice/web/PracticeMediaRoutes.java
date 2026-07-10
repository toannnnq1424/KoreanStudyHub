package com.ksh.features.practice.web;

/**
 * Route constants for private learner speaking media.
 */
public final class PracticeMediaRoutes {
    public static final String SPEAKING_MEDIA =
            "/attempts/{attemptId}/questions/{questionId}/speaking-media";
    public static final String SPEAKING_MEDIA_ITEM =
            "/attempts/{attemptId}/questions/{questionId}/speaking-media/{mediaId}";
    public static final String SPEAKING_MEDIA_CONTENT =
            "/attempts/{attemptId}/questions/{questionId}/speaking-media/{mediaId}/content";

    private PracticeMediaRoutes() {
    }

    public static String playbackPath(Long attemptId, Long questionId, Long mediaId) {
        return PracticeRoutes.BASE + "/attempts/" + attemptId
                + "/questions/" + questionId
                + "/speaking-media/" + mediaId
                + "/content";
    }
}
