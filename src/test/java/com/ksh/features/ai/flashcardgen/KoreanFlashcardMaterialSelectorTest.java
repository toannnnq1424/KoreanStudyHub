package com.ksh.features.ai.flashcardgen;

import com.ksh.features.ai.questiongen.DocumentTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KoreanFlashcardMaterialSelectorTest {

    private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    private final KoreanFlashcardMaterialSelector selector =
            new KoreanFlashcardMaterialSelector(extractor);

    @Test
    void topik_booklet_starts_at_reading_section_not_cover_rules_or_listening() {
        MockMultipartFile file =
                new MockMultipartFile("file", "topik.pdf", "application/pdf", new byte[]{1});
        when(extractor.extract(file)).thenReturn("""
                한 국 어 능 력 시 험
                유의 사항
                시험 시작 지시가 있을 때까지 문제를 풀지 마십시오.
                TOPIK I 듣기 (1번 ～ 30번)
                우산이에요.
                TOPIK I 읽기 (31번 ～ 70번)
                제35회 한국어능력시험I B형(듣기, 읽기)
                불고기를 먹습니다. 맛있습니다.
                식혜는 한국의 전통 음료수입니다.
                12
                """);

        String selected = selector.select(file, null);

        assertThat(selected)
                .startsWith("TOPIK I 읽기")
                .contains("불고기를 먹습니다", "식혜는 한국의 전통 음료수입니다")
                .doesNotContain("유의 사항", "우산이에요", "제35회", "\n12\n");
    }

    @Test
    void ordinary_learning_material_is_not_discarded() {
        when(extractor.normalizePastedText("한복은 한국의 전통 옷입니다."))
                .thenReturn("한복은 한국의 전통 옷입니다.");

        assertThat(selector.select(null, "한복은 한국의 전통 옷입니다."))
                .isEqualTo("한복은 한국의 전통 옷입니다.");
    }
}
