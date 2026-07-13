package com.ksh.features.practice.ai.writing;

import com.ksh.entities.WritingTaskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WritingTaskResolverTest {

    private final WritingTaskResolver resolver = new WritingTaskResolver();

    @Test
    void explicitMetadataPreservesExactTaskIdentity() {
        assertEquals("Q51", resolver.resolve(WritingTaskType.Q51, "Bài 54"));
        assertEquals("Q52", resolver.resolve(WritingTaskType.Q52, "Bài 53"));
        assertEquals("Q53", resolver.resolve(WritingTaskType.Q53, "Bài 51"));
        assertEquals("Q54", resolver.resolve(WritingTaskType.Q54, "Bài viết chung"));
    }

    @Test
    void nullMetadataDelegatesToLegacyPromptDetector() {
        assertEquals("Q51_52", resolver.resolve(null, "Bài 51 điền vào chỗ trống"));
        assertEquals("Q51_52", resolver.resolve(null, "Bài 52 điền (ㄱ) (ㄴ)"));
        assertEquals("Q53", resolver.resolve(null, "Bài 53 viết 200-300자"));
        assertEquals("Q53", resolver.resolve(null, "Viết 200~300 ký tự"));
        assertEquals("Q54", resolver.resolve(null, "Bài 54 nghị luận 600-700자"));
        assertEquals("Q54", resolver.resolve(null, "Viết 600~700 ký tự"));
        assertEquals("GENERAL", resolver.resolve(null, "Bài luyện tập chung"));
    }

    @Test
    void nullMetadataKeepsLegacyDetectorPrecedence() {
        assertEquals("Q51_52", resolver.resolve(null, "Câu 51 và câu 54 cùng xuất hiện"));
        assertEquals("Q53", resolver.resolve(null, "Câu 53 và 600-700 cùng xuất hiện"));
    }
}
