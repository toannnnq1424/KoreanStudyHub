package com.ksh.features.ai.client;

/**
 * Raised when an AI chat call cannot be completed.
 *
 * <p>Two situations produce this exception, deliberately funnelled into
 * one type so callers only need a single catch block:
 * <ul>
 *   <li>no provider is configured, or every configured provider is disabled;</li>
 *   <li>every enabled provider was tried and each failed — the message then
 *       lists one {@code "<name>: <reason>"} line per provider so an admin can see which
 *       endpoint broke and why.</li>
 * </ul>
 */
public class AiClientException extends RuntimeException {

    /**
     * Creates an exception carrying a message that is safe to show to an admin.
     *
     * @param message human-readable explanation, already aggregated across providers
     *                when the whole fallback chain failed
     */
    public AiClientException(String message) {
        super(message);
    }

    /**
     * Creates an exception preserving the underlying transport failure.
     *
     * @param message human-readable explanation
     * @param cause   the originating exception
     */
    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
