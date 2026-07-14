package com.ksh.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PracticeQuestionVersionTest {

    @Test
    void immutableVersionCopiesCanonicalSpeakingDeliveryJson() {
        String deliveryJson = """
                {"schemaVersion":"question-content-v1","options":[],"blanks":[],"speakingDelivery":{"promptAudioReference":"/practice/materials/9/content","promptPlayLimit":2,"preparationSeconds":30,"responseSeconds":60}}
                """.trim();
        PracticeQuestion question = new PracticeQuestion(
                1L, 1, PracticeQuestion.TYPE_SPEAKING, "말해 보십시오.",
                "[]", "", "", BigDecimal.valueOf(100), 0);
        question.setQuestionContentJson(deliveryJson);

        PracticeQuestionVersion version = new PracticeQuestionVersion(7L, 8L, 9L, question);

        assertEquals(deliveryJson, version.getQuestionContentJson());
    }
}
