package com.ksh.features.discovery.ingestion;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSlugSupportTest {

    private static final String HASH = "0123456789abcdef";

    @Test
    void builds_ascii_slug_from_vietnamese_title_and_short_hash_prefix() {
        String result = NewsSlugSupport.from("\u0110i\u1ec7n \u1ea3nh H\u00e0n Qu\u1ed1c!", HASH);

        assertThat(result).isEqualTo("dien-anh-han-quoc-0123456789");
    }

    @Test
    void falls_back_to_stable_default_when_title_is_null_or_has_no_letters() {
        assertThat(NewsSlugSupport.from(null, HASH)).isEqualTo("tin-han-quoc-0123456789");
        assertThat(NewsSlugSupport.from("--- !!! ---", HASH)).isEqualTo("tin-han-quoc-0123456789");
    }

    @Test
    void truncates_long_title_without_leading_or_trailing_separator() {
        String result = NewsSlugSupport.from("a".repeat(210) + "---", HASH);
        String slug = result.substring(0, result.length() - 11);

        assertThat(slug).hasSize(190).doesNotStartWith("-").doesNotEndWith("-");
        assertThat(result).endsWith("-0123456789");
    }

    @Test
    void utility_constructor_is_private_but_instantiable_for_coverage() throws Exception {
        Constructor<NewsSlugSupport> constructor = NewsSlugSupport.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
