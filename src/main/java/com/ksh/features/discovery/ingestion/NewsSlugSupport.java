package com.ksh.features.discovery.ingestion;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NewsSlugSupport {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");

    private NewsSlugSupport() {
    }

    public static String from(String title, String hash) {
        String value = title == null ? "" : title.replace('đ', 'd').replace('Đ', 'D');
        value = Normalizer.normalize(value, Normalizer.Form.NFD);
        value = MARKS.matcher(value).replaceAll("");
        value = NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        value = EDGE_HYPHENS.matcher(value).replaceAll("");
        if (value.isBlank()) {
            value = "tin-han-quoc";
        }
        if (value.length() > 190) {
            value = EDGE_HYPHENS.matcher(value.substring(0, 190)).replaceAll("");
        }
        return value + "-" + hash.substring(0, 10);
    }
}
