package com.ksh.features.practice.ai.media;

import com.ksh.features.practice.manage.service.PracticeMaterialAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiQuestionImageResolverTest {

    private final PracticeMaterialAccessService materialAccessService =
            mock(PracticeMaterialAccessService.class);
    private final AiQuestionImageResolver resolver =
            new AiQuestionImageResolver(materialAccessService);

    @Test
    void resolvesAuthorizedInternalImageToDataUrlAndHash() throws IOException {
        byte[] bytes = "png".getBytes(StandardCharsets.UTF_8);
        when(materialAccessService.load(7L, 42L)).thenReturn(
                new PracticeMaterialAccessService.MaterialContent(
                        new ByteArrayResource(bytes), "image/png; charset=binary", "question.png", 3L));

        AiImageEvidence evidence = resolver.resolve(
                "/practice/materials/7/content", 42L).orElseThrow();

        assertThat(evidence.assetId()).isEqualTo(7L);
        assertThat(evidence.mimeType()).isEqualTo("image/png");
        assertThat(evidence.dataUrl()).isEqualTo("data:image/png;base64,cG5n");
        assertThat(evidence.sha256()).hasSize(64);
        assertThat(evidence.toString()).doesNotContain("data:image", "cG5n");
    }

    @Test
    void resolvesWorkerImageOnlyThroughItsImmutablePublishedVersion() throws IOException {
        byte[] bytes = "png".getBytes(StandardCharsets.UTF_8);
        when(materialAccessService.loadForPublishedVersion(7L, 55L)).thenReturn(
                new PracticeMaterialAccessService.MaterialContent(
                        new ByteArrayResource(bytes), "image/png", "question.png", 3L));

        AiImageEvidence evidence = resolver.resolvePublishedVersion(
                "/practice/materials/7/content", 55L).orElseThrow();

        assertThat(evidence.assetId()).isEqualTo(7L);
        assertThat(evidence.sha256()).hasSize(64);
    }

    @Test
    void rejectsExternalOrMalformedReferencesBeforeStorageAccess() {
        assertThat(resolver.resolve("https://example.com/question.png", 42L)).isEmpty();
        assertThat(resolver.resolve("/practice/materials/0/content", 42L)).isEmpty();
        assertThat(resolver.resolve("/practice/materials/7/content?download=1", 42L)).isEmpty();
        assertThat(resolver.resolve("/practice/materials/7/content", null)).isEmpty();
        assertThat(resolver.resolvePublishedVersion(
                "/practice/materials/7/content", null)).isEmpty();

        verifyNoInteractions(materialAccessService);
    }

    @Test
    void rejectsUnsupportedOrOversizedMaterial() throws IOException {
        when(materialAccessService.load(8L, 42L)).thenReturn(
                new PracticeMaterialAccessService.MaterialContent(
                        new ByteArrayResource(new byte[]{1}), "image/gif", "question.gif", 1L));
        when(materialAccessService.load(9L, 42L)).thenReturn(
                new PracticeMaterialAccessService.MaterialContent(
                        new ByteArrayResource(new byte[]{1}), "image/png", "question.png",
                        8L * 1024 * 1024 + 1));

        assertThat(resolver.resolve("/practice/materials/8/content", 42L)).isEmpty();
        assertThat(resolver.resolve("/practice/materials/9/content", 42L)).isEmpty();
    }
}
