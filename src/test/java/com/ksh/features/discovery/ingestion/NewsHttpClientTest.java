package com.ksh.features.discovery.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsHttpClientTest {

    @Test
    void carriesAndReplacesRedirectCookiesWithoutAttributes() {
        String first = NewsHttpClient.mergeCookies(
                null,
                List.of("kinx-sign-id=abc; path=/; HttpOnly")
        );
        String second = NewsHttpClient.mergeCookies(
                first,
                List.of(
                        "kinx-sign-id=def; path=/; HttpOnly",
                        "WMONID=session; path=/"
                )
        );

        assertThat(first).isEqualTo("kinx-sign-id=abc");
        assertThat(second).isEqualTo("kinx-sign-id=def; WMONID=session");
    }
}
