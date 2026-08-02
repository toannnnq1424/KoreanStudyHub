package com.ksh.features.ai.flashcardgen;

import com.ksh.features.admin.settings.repository.AiSystemPromptRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiFlashcardPromptBuilderTest {

    @Test
    void fallback_contract_requires_korean_front_and_explanation_language_back() {
        AiSystemPromptRepository repository = mock(AiSystemPromptRepository.class);
        when(repository.findByNameAndEnabledTrue(AiFlashcardPromptBuilder.PROMPT_NAME))
                .thenReturn(Optional.empty());
        AiFlashcardPromptBuilder builder = new AiFlashcardPromptBuilder(repository);
        var request = new AiFlashcardGenDtos.GenerateRequest(5, "tiếng việt");

        assertThat(builder.systemPrompt())
                .contains("Mặt trước bắt buộc giữ nguyên Hangul")
                .contains("Bỏ qua bìa", "nội quy thi", "hướng dẫn làm bài")
                .contains("đơn vị từ vựng nhỏ nhất vẫn giữ trọn nghĩa")
                .contains("한-베트남 문화센터")
                .contains("không rút thành \"문화센터\"")
                .contains("Không chép nguyên cả câu/mệnh đề");
        assertThat(builder.userMessage(request, "식혜는 전통 음료수입니다."))
                .contains("Mặt trước: từ/cụm từ tiếng Hàn nguyên văn")
                .contains("Mặt sau: giải nghĩa bằng tiếng việt")
                .contains("Giữ trọn tên riêng/danh từ ghép/cụm chuyên biệt")
                .contains("không lấy nguyên cả câu thông thường")
                .contains("Bỏ qua nội quy");
    }
}
