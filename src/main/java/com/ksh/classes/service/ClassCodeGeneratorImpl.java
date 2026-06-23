package com.ksh.classes.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ClassCodeGeneratorImpl implements ClassCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 5;
    static final int RANDOM_PART_LENGTH = 4;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        int tsIdx = Math.floorMod(System.currentTimeMillis(), ALPHABET.length());
        sb.append(ALPHABET.charAt(tsIdx));
        return sb.toString();
    }
}
