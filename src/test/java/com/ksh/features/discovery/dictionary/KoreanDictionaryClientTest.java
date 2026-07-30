package com.ksh.features.discovery.dictionary;

import com.ksh.features.discovery.ingestion.NewsHttpClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KoreanDictionaryClientTest {

    @Test
    void lookupUsesOfficialVietnameseTranslationRequest() {
        NewsHttpClient httpClient = mock(NewsHttpClient.class);
        when(httpClient.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(MediaType.APPLICATION_XML)))
                .thenReturn("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <channel>
                          <item>
                            <target_code>343267</target_code>
                            <word>문화</word>
                            <pos>명사</pos>
                            <link>https://krdict.korean.go.kr/vie/dicSearch/SearchView?ParaWordNo=343267</link>
                            <sense>
                              <translation>
                                <trans_lang>베트남어</trans_lang>
                                <trans_word>văn hóa</trans_word>
                                <trans_dfn>Tổng thể những giá trị do con người tạo ra.</trans_dfn>
                              </translation>
                            </sense>
                          </item>
                        </channel>
                        """);
        KoreanDictionaryClient client = new KoreanDictionaryClient(
                httpClient,
                "0123456789abcdef0123456789abcdef",
                "https://krdict.korean.go.kr/api/search"
        );

        Optional<DictionaryEntry> result = client.lookupVietnamese("문화");

        assertThat(result).isPresent();
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(httpClient).get(url.capture(), org.mockito.ArgumentMatchers.eq(MediaType.APPLICATION_XML));
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
                <channel>
                  <item>
                    <target_code>343267</target_code>
                    <word>문화</word>
                    <pronunciation>문화</pronunciation>
                    <word_grade>초급</word_grade>
                    <pos>명사</pos>
                    <link>https://krdict.korean.go.kr/vie/dicSearch/SearchView?ParaWordNo=343267</link>
                    <sense>
                      <translation>
                        <trans_word>văn hóa</trans_word>
                        <trans_dfn>Tổng thể những giá trị do con người tạo ra.</trans_dfn>
                      </translation>
                    </sense>
                  </item>
                </channel>
                """;
        KoreanDictionaryClient client = new KoreanDictionaryClient(null, "", "ignored");

        Optional<DictionaryEntry> result = client.parse(xml, "문화");

        assertThat(result).hasValueSatisfying(entry -> {
            assertThat(entry.targetCode()).isEqualTo("343267");
            assertThat(entry.word()).isEqualTo("문화");
            assertThat(entry.meaningVi())
                    .contains("văn hóa")
                    .contains("Tổng thể những giá trị");
            assertThat(entry.partOfSpeech()).isEqualTo("명사");
        });
    }
}
