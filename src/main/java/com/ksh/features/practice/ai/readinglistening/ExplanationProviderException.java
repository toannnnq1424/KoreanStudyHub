package com.ksh.features.practice.ai.readinglistening;

public class ExplanationProviderException extends RuntimeException {

    private final String category;
    private final boolean retryable;

    public ExplanationProviderException(String category, String message, boolean retryable) {
        super(message);
        this.category = category;
        this.retryable = retryable;
    }

    public ExplanationProviderException(
            String category,
            String message,
            boolean retryable,
            Throwable cause) {
        super(message, cause);
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
