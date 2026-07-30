package com.ksh.features.discovery.ingestion;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NewsUrlSupport {

    private static final Set<String> ALLOWED_ARTICLE_HOSTS = Set.of(
            "world.kbs.co.kr",
            "vietnamese.korea.net",
            "www.korea.net",
            "www.studyinkorea.go.kr"
    );
    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "gclid"
    );

    private NewsUrlSupport() {
    }

    public static String canonicalize(String rawUrl) {
        URI parsed = URI.create(rawUrl.trim());
        String host = parsed.getHost() == null
                ? ""
                : parsed.getHost().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ARTICLE_HOSTS.contains(host)) {
            throw new IllegalArgumentException("URL bài gốc không thuộc nguồn đã duyệt");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(host)
                .path(parsed.getPath());

        if (parsed.getRawQuery() != null && !parsed.getRawQuery().isBlank()) {
            List<String[]> parameters = new ArrayList<>();
            for (String part : parsed.getRawQuery().split("&")) {
                String[] pair = part.split("=", 2);
                String name = pair[0].toLowerCase(Locale.ROOT);
                if (!TRACKING_PARAMETERS.contains(name)) {
                    parameters.add(pair);
                }
            }
            parameters.sort(Comparator.comparing(pair -> pair[0]));
            for (String[] pair : parameters) {
                builder.queryParam(pair[0], pair.length == 2 ? pair[1] : "");
            }
        }
        return builder.build(true).toUriString();
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng", exception);
        }
    }
}
