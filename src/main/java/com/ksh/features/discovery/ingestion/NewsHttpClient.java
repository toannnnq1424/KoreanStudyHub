package com.ksh.features.discovery.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class NewsHttpClient {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "world.kbs.co.kr",
            "vietnamese.korea.net",
            "www.studyinkorea.go.kr",
            "krdict.korean.go.kr"
    );

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private final RestClient restClient;

    public NewsHttpClient(
            RestClient.Builder builder,
            @Value("${app.news.http.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.news.http.read-timeout:20s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new NoRedirectRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = builder
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "KSH-KoreaDiscovery/1.0 (+metadata-only aggregator)")
                .build();
    }

    public String get(String rawUrl, MediaType accept) {
        URI uri = validate(rawUrl);
        String cookieHeader = null;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            RawResponse response = request(uri, accept, cookieHeader);
            cookieHeader = mergeCookies(cookieHeader, response.setCookies());
            if (response.status() >= 300 && response.status() < 400) {
                if (response.location() == null || redirect == MAX_REDIRECTS) {
                    throw new IllegalStateException("Nguồn chuyển hướng không hợp lệ");
                }
                uri = validate(uri.resolve(response.location()).toString());
                continue;
            }
            if (response.status() < 200 || response.status() >= 300) {
                throw new IllegalStateException("Nguồn trả về HTTP " + response.status());
            }
            Charset charset = response.charset() == null
                    ? StandardCharsets.UTF_8
                    : response.charset();
            return new String(response.body(), charset);
        }
        throw new IllegalStateException("Nguồn chuyển hướng quá nhiều lần");
    }

    private RawResponse request(URI uri, MediaType accept, String cookieHeader) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri(uri)
                .accept(accept);
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            request.header(HttpHeaders.COOKIE, cookieHeader);
        }
        return request.exchange((httpRequest, response) -> {
            byte[] body = readBounded(response.getBody());
            MediaType contentType = response.getHeaders().getContentType();
            return new RawResponse(
                    response.getStatusCode().value(),
                    response.getHeaders().getFirst(HttpHeaders.LOCATION),
                    response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE),
                    contentType == null ? null : contentType.getCharset(),
                    body
            );
        });
    }

    private static URI validate(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ALLOWED_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Nguồn tin không nằm trong allowlist HTTPS");
        }
        return uri;
    }

    private static byte[] readBounded(java.io.InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new IllegalStateException("Phản hồi nguồn tin vượt quá 2 MB");
        }
        return bytes;
    }

    static String mergeCookies(String existing, List<String> setCookieHeaders) {
        List<String> cookies = new ArrayList<>();
        if (existing != null && !existing.isBlank()) {
            for (String cookie : existing.split(";\\s*")) {
                if (!cookie.isBlank()) {
                    cookies.add(cookie);
                }
            }
        }
        for (String setCookie : setCookieHeaders) {
            String cookie = setCookie.split(";", 2)[0].trim();
            String name = cookie.split("=", 2)[0];
            cookies.removeIf(value -> value.startsWith(name + "="));
            if (!cookie.isBlank()) {
                cookies.add(cookie);
            }
        }
        return String.join("; ", cookies);
    }

    private record RawResponse(
            int status,
            String location,
            List<String> setCookies,
            Charset charset,
            byte[] body
    ) {
    }

    private static final class NoRedirectRequestFactory
            extends SimpleClientHttpRequestFactory {

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
