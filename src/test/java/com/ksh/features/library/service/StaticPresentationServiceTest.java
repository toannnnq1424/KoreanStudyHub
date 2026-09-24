package com.ksh.features.library.service;

import com.ksh.features.storage.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class StaticPresentationServiceTest {
    @Test void real_pptx_converts_to_pdf_when_converter_is_available() throws Exception {
        String executable = System.getenv("KSH_TEST_SOFFICE");
        org.junit.jupiter.api.Assumptions.assumeTrue(executable != null);
        byte[] input;
        try (var deck = new org.apache.poi.xslf.usermodel.XMLSlideShow();
             var output = new ByteArrayOutputStream()) {
            deck.createSlide().createTextBox().setText("Korean lesson — 안녕하세요");
            deck.write(output);
            input = output.toByteArray();
        }
        var storage = mock(ObjectStorage.class);
        when(storage.open("deck")).thenReturn(new StoredObject(new ByteArrayInputStream(input), input.length, "application/octet-stream"));
        byte[] pdf = new StaticPresentationService(storage, executable).pdf("deck", "lesson.pptx");
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }
    @Test void pdf_passes_through_without_external_process() throws Exception {
        var storage = mock(ObjectStorage.class);
        byte[] bytes = "%PDF-1.7 test".getBytes();
        when(storage.open("file.pdf")).thenReturn(new StoredObject(new ByteArrayInputStream(bytes), bytes.length, "application/pdf"));
        assertThat(new StaticPresentationService(storage, "missing-converter").pdf("file.pdf", "file.pdf")).isEqualTo(bytes);
    }
    @Test void unsupported_extensions_are_rejected_before_storage_access() {
        var storage = mock(ObjectStorage.class);
        assertThatThrownBy(() -> new StaticPresentationService(storage, "soffice").pdf("x", "macro.pptm")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }
}
