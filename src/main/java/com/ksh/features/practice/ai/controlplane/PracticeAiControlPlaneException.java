package com.ksh.features.practice.ai.controlplane;

public class PracticeAiControlPlaneException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public PracticeAiControlPlaneException(String errorCode, boolean retryable) {
        super(errorCode);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public PracticeAiControlPlaneException(
            String errorCode,
            boolean retryable,
            Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
