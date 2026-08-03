package com.ksh.features.flashcards.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates opaque tokens exclusively for flashcard public links. */
@Component
public class FlashcardPublicTokenGenerator {
    public static final String TOKEN_REGEX = "^[A-Za-z0-9_-]{32}$";
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
