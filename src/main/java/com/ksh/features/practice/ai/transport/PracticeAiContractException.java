package com.ksh.features.practice.ai.transport;

public class PracticeAiContractException extends RuntimeException {
    private final String category;
    private final boolean retryable;

    public PracticeAiContractException(String category, boolean retryable) {
        this(category, retryable, null);
    }

    public PracticeAiContractException(
            String category,
            boolean retryable,
            Throwable cause) {
        super(category, cause);
        this.category = category;
        this.retryable = retryable;
    }

    public String category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }
}
