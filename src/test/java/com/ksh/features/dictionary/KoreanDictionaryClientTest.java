package com.ksh.features.dictionary;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KoreanDictionaryClientTest {

    @Test
    void lookupUsesOfficialVietnameseTranslationRequest() {
        KoreanDictionaryHttpClient httpClient = mock(KoreanDictionaryHttpClient.class);
        when(httpClient.get(anyString(), eq(MediaType.APPLICATION_XML))).thenReturn("""
                <?xml version="1.0" encoding="UTF-8"?>
                <channel><item><target_code>343267</target_code><word>문화</word><pos>명사</pos>
                <link>https://krdict.korean.go.kr/vie/dicSearch/SearchView?ParaWordNo=343267</link>
                <sense><translation><trans_word>văn hóa</trans_word><trans_dfn>Giá trị do con người tạo ra.</trans_dfn>
                </translation></sense></item></channel>
                """);
        KoreanDictionaryClient client = new KoreanDictionaryClient(
                httpClient, "0123456789abcdef0123456789abcdef", "https://krdict.korean.go.kr/api/search");

        Optional<DictionaryEntry> result = client.lookupVietnamese("문화");

        assertThat(result).isPresent();
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(url.capture(), eq(MediaType.APPLICATION_XML));
        assertThat(url.getValue())
                .startsWith("https://krdict.korean.go.kr/api/search?")
                .contains("key=0123456789abcdef0123456789abcdef")
                .contains("q=%EB%AC%B8%ED%99%94")
                .contains("part=word")
                .contains("method=exact")
                .contains("translated=y")
                .contains("trans_lang=7")
                .contains("num=10");
    }

    @Test
    void parsesVietnameseTranslationFromOfficialXmlShape() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <channel><item><target_code>343267</target_code><word>문화</word><pronunciation>문화</pronunciation>
                <word_grade>초급</word_grade><pos>명사</pos>
                <sense><translation><trans_word>văn hóa</trans_word>
                <trans_dfn>Giá trị do con người tạo ra.</trans_dfn></translation></sense></item></channel>
                """;
        KoreanDictionaryClient client = new KoreanDictionaryClient(null, "", "ignored");

        Optional<DictionaryEntry> result = client.parse(xml, "문화");

        assertThat(result).hasValueSatisfying(entry -> {
            assertThat(entry.targetCode()).isEqualTo("343267");
            assertThat(entry.word()).isEqualTo("문화");
            assertThat(entry.meaningVi()).contains("văn hóa").contains("Giá trị");
            assertThat(entry.partOfSpeech()).isEqualTo("명사");
        });
    }

    @Test
    void rejectsEndpointOutsideTheOfficialDictionaryHost() {
        KoreanDictionaryHttpClient httpClient = new KoreanDictionaryHttpClient(
                RestClient.builder(), Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThatThrownBy(() -> httpClient.get("https://example.test/api", MediaType.APPLICATION_XML))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("krdict.korean.go.kr");
    }
}
