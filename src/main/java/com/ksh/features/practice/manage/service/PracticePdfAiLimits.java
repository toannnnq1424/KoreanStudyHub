package com.ksh.features.practice.manage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PracticePdfAiLimits {

    private final int maxSelectedPages;
    private final int maxRegions;
    private final int maxTextCharacters;
    private final long maxImageBytes;
    private final long maxTotalImageBytes;
    private final long maxRenderedPagePixels;
    private final Duration generationLeaseDuration;

    public PracticePdfAiLimits(
            @Value("${app.practice.pdf-ai.max-selected-pages:50}")
            int maxSelectedPages,
            @Value("${app.practice.pdf-ai.max-regions:100}")
            int maxRegions,
            @Value("${app.practice.pdf-ai.max-text-characters:1000000}")
            int maxTextCharacters,
            @Value("${app.practice.pdf-ai.max-image-bytes:5242880}")
            long maxImageBytes,
            @Value("${app.practice.pdf-ai.max-total-image-bytes:20971520}")
            long maxTotalImageBytes,
            @Value("${app.practice.pdf-ai.max-rendered-page-pixels:40000000}")
            long maxRenderedPagePixels,
            @Value("${app.practice.pdf-ai.generation-lease-duration:PT10M}")
            Duration generationLeaseDuration) {
        this.maxSelectedPages = bounded(maxSelectedPages, 1, 200);
        this.maxRegions = bounded(maxRegions, 1, 500);
        this.maxTextCharacters = bounded(maxTextCharacters, 10_000, 2_000_000);
        this.maxImageBytes = bounded(maxImageBytes, 64 * 1024L, 20 * 1024 * 1024L);
        this.maxTotalImageBytes = bounded(
                maxTotalImageBytes, this.maxImageBytes, 100 * 1024 * 1024L);
        this.maxRenderedPagePixels = bounded(
                maxRenderedPagePixels, 1_000_000L, 100_000_000L);
        this.generationLeaseDuration = bounded(
                generationLeaseDuration,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ofMinutes(10));
    }

    public int maxSelectedPages() {
        return maxSelectedPages;
    }

    public int maxRegions() {
        return maxRegions;
    }

    public int maxTextCharacters() {
        return maxTextCharacters;
    }

    public long maxImageBytes() {
        return maxImageBytes;
    }

    public long maxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public long maxRenderedPagePixels() {
        return maxRenderedPagePixels;
    }

    public Duration generationLeaseDuration() {
        return generationLeaseDuration;
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static long bounded(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static Duration bounded(
            Duration value,
            Duration minimum,
            Duration maximum,
            Duration fallback) {
        Duration candidate = value == null ? fallback : value;
        if (candidate.compareTo(minimum) < 0) {
            return minimum;
        }
        if (candidate.compareTo(maximum) > 0) {
            return maximum;
        }
        return candidate;
    }
}
