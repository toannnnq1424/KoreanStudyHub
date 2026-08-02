package com.ksh.features.practice.manage.authoringcandidate;

public class PracticeAuthoringCandidateException extends RuntimeException {

    private final String code;

    public PracticeAuthoringCandidateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
