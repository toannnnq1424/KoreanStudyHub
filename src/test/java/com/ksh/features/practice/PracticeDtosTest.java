package com.ksh.features.practice;

import com.ksh.features.practice.dto.PracticeDtos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PracticeDtosTest {

    @Test
    public void testGetOptionLabelMode_TitleBased() {
        // Option label mode should default to ALPHA if title contains "alpha" or "Đọc hiểu"
        assertEquals("ALPHA", PracticeDtos.getOptionLabelMode("Practice Test Alpha", null));
        assertEquals("ALPHA", PracticeDtos.getOptionLabelMode("Đề Thi Đọc hiểu", null));
        assertEquals("NUMERIC", PracticeDtos.getOptionLabelMode("Nghe TOPIK", null));
    }

    @Test
    public void testGetOptionLabelMode_MetadataJsonBased() {
        // MetadataJson should override title default if present
        assertEquals("ALPHA", PracticeDtos.getOptionLabelMode("Nghe TOPIK", "{\"optionLabelMode\": \"ALPHA\"}"));
        assertEquals("NUMERIC", PracticeDtos.getOptionLabelMode("Practice Test Alpha", "{\"optionLabelMode\": \"NUMERIC\"}"));
    }

    @Test
    public void testGetOptionLabelMode_InvalidOrEmptyMetadata() {
        // Invalid or empty metadata should fall back to title-based deduction
        assertEquals("ALPHA", PracticeDtos.getOptionLabelMode("Practice Test Alpha", "invalid-json"));
        assertEquals("ALPHA", PracticeDtos.getOptionLabelMode("Practice Test Alpha", ""));
        assertEquals("NUMERIC", PracticeDtos.getOptionLabelMode("Nghe TOPIK", "{\"someOtherField\": true}"));
    }
}
