package com.ksh.features.discovery.ingestion;

import org.jsoup.Jsoup;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NewsTextSupport {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private NewsTextSupport() {
    }

    public static String plainText(String html, int maxLength) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = WHITESPACE.matcher(Jsoup.parse(html).text()).replaceAll(" ").trim();
        if (text.length() <= maxLength) {
            return text;
        }
        int boundary = text.lastIndexOf(' ', maxLength - 1);
        int end = boundary >= maxLength / 2 ? boundary : maxLength - 1;
        return text.substring(0, end).stripTrailing() + "…";
    }

    public static String normalizeForPolicy(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        String noLatinMarks = COMBINING_MARKS.matcher(normalized).replaceAll("");
        return Normalizer.normalize(noLatinMarks, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd');
    }

    public static LocalDateTime parseFeedDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now(VIETNAM_ZONE);
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .atZoneSameInstant(VIETNAM_ZONE)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
                        .atTime(LocalTime.NOON);
            } catch (DateTimeParseException ignoredIsoDate) {
                return LocalDateTime.now(VIETNAM_ZONE);
            }
        }
    }
}
