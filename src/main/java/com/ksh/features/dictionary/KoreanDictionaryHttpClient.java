package com.ksh.features.dictionary;

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
import java.util.Locale;

/** HTTP boundary for the single allowlisted Korean dictionary provider. */
@Component
public class KoreanDictionaryHttpClient {

    private static final String ALLOWED_HOST = "krdict.korean.go.kr";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final RestClient restClient;

    public KoreanDictionaryHttpClient(
            RestClient.Builder builder,
            @Value("${app.dictionary.http.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.dictionary.http.read-timeout:20s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new NoRedirectRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = builder
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "KSH-KoreanDictionary/1.0")
                .build();
    }

    public String get(String rawUrl, MediaType accept) {
        URI uri = validate(rawUrl);
        return restClient.get()
                .uri(uri)
                .accept(accept)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("Korean Basic Dictionary trả về HTTP " + status);
                    }
                    try {
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) {
                            throw new IllegalStateException("Phản hồi Korean Basic Dictionary vượt quá 2 MB");
                        }
                        MediaType contentType = response.getHeaders().getContentType();
                        Charset charset = contentType == null || contentType.getCharset() == null
                                ? StandardCharsets.UTF_8
                                : contentType.getCharset();
                        return new String(body, charset);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Không đọc được phản hồi Korean Basic Dictionary", exception);
                    }
                });
    }

    private static URI validate(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ALLOWED_HOST.equals(host)) {
            throw new IllegalArgumentException("Dictionary endpoint phải thuộc krdict.korean.go.kr qua HTTPS");
        }
        return uri;
    }

    private static final class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
