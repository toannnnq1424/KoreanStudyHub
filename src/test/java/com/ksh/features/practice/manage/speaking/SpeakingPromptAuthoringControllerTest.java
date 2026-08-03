package com.ksh.features.practice.manage.speaking;

import com.ksh.security.KshUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakingPromptAuthoringControllerTest {

    @Test
    void authoringPreviewMediaIsNeverCachedAcrossReplacement() {
        Fixture fixture = fixture();
        when(fixture.state.loadMedia(
                91L, "speaking-a", 81L, "original"))
                .thenReturn(new SpeakingPromptAssetService.MediaResource(
                        new ByteArrayResource(new byte[]{1, 2, 3}),
                        "de-bai.wav",
                        "audio/wav",
                        3L));

        ResponseEntity<org.springframework.core.io.Resource> response =
                fixture.controller.media(
                        91L, "speaking-a", "original", fixture.user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store", "must-revalidate");
        assertThat(response.getHeaders().getFirst(HttpHeaders.PRAGMA))
                .isEqualTo("no-cache");
        assertThat(response.getHeaders().getFirst(HttpHeaders.EXPIRES))
                .isEqualTo("0");
    }

    @Test
    void everyJsonMutationRequiresBothConcurrencyTokens() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/ksh/features/practice/manage/speaking/"
                        + "SpeakingPromptAuthoringController.java"));

        assertThat(controller).contains(
                "public record SaveRequest(\n"
                        + "            @NotBlank String inputType,\n"
                        + "            @NotNull Long expectedSourceRevision,\n"
                        + "            @NotNull Long expectedDraftVersion",
                "public record RevisionRequest(\n"
                        + "            @NotNull Long expectedSourceRevision,\n"
                        + "            @NotNull Long expectedDraftVersion",
                "public record TranscriptRequest(\n"
                        + "            @NotNull Long expectedSourceRevision,\n"
                        + "            @NotNull Long expectedDraftVersion",
                "public record TtsRequest(\n"
                        + "            @NotNull Long expectedSourceRevision,\n"
                        + "            @NotNull Long expectedDraftVersion");
    }

    @Test
    void saveToggleUsesNoGenerateOrRetryCommand() {
        Fixture fixture = fixture();

        fixture.controller.save(
                91L,
                "speaking-a",
                new SpeakingPromptAuthoringController.SaveRequest(
                        SpeakingPromptSource.INPUT_MANUAL_TEXT,
                        7L,
                        3L,
                        "다음 주제에 대해 말하세요.",
                        true,
                        "default",
                        BigDecimal.ONE,
                        "mp3"),
                fixture.user);

        org.mockito.ArgumentCaptor<SpeakingPromptAuthoringService.SaveManualPrompt>
                command = forClass(
                        SpeakingPromptAuthoringService.SaveManualPrompt.class);
        verify(fixture.authoring).saveManualPrompt(command.capture());
        assertThat(command.getValue().expectedDraftVersion()).isEqualTo(3L);
        verify(fixture.authoring, never()).requestTts(any());
        verify(fixture.authoring, never()).retryCurrentOperation(any());
        verify(fixture.state).load(91L, "speaking-a", 81L);
        assertThat(new SpeakingPromptAuthoringController.SaveRequest(
                "manual_text",
                7L,
                3L,
                "비공개 원문",
                true,
                "default",
                BigDecimal.ONE,
                "mp3").toString()).doesNotContain("비공개 원문");
    }

    @Test
    void uploadAlwaysReturnsAcceptedAndNeverCallsTts() {
        Fixture fixture = fixture();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("de-bai.mp3");

        ResponseEntity<?> response = fixture.controller.upload(
                91L, "speaking-a", file, 7L, 3L, fixture.user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        org.mockito.ArgumentCaptor<SpeakingPromptAuthoringService.UploadOriginalAudio>
                command = forClass(
                        SpeakingPromptAuthoringService.UploadOriginalAudio.class);
        verify(fixture.uploadCoordinator).uploadAndEnqueueStt(command.capture());
        assertThat(command.getValue().expectedDraftVersion()).isEqualTo(3L);
        verify(fixture.authoring, never()).requestTts(any());
    }

    @Test
    void explicitTtsDistinguishesReadyReuseFromQueuedWork() {
        Fixture fixture = fixture();
        SpeakingPromptAuthoringController.TtsRequest request =
                new SpeakingPromptAuthoringController.TtsRequest(
                        7L, 3L, "default", BigDecimal.ONE, "mp3");
        when(fixture.authoring.requestTts(any()))
                .thenReturn(new SpeakingPromptAuthoringService.EnqueueResult(
                        1L, 7L, 2L, null, "ready", true));

        ResponseEntity<?> ready = fixture.controller.generateTts(
                91L, "speaking-a", request, fixture.user);

        assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);

        when(fixture.authoring.requestTts(any()))
                .thenReturn(new SpeakingPromptAuthoringService.EnqueueResult(
                        1L, 7L, 2L, 3L, "queued", false));
        ResponseEntity<?> queued = fixture.controller.generateTts(
                91L, "speaking-a", request, fixture.user);

        assertThat(queued.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        org.mockito.ArgumentCaptor<SpeakingPromptAuthoringService.GenerateTts>
                command = forClass(
                        SpeakingPromptAuthoringService.GenerateTts.class);
        verify(fixture.authoring, org.mockito.Mockito.times(2))
                .requestTts(command.capture());
        assertThat(command.getAllValues())
                .allMatch(value -> value.expectedDraftVersion() == 3L);
    }

    @Test
    void retryCooldownAndQuotaMapTo429WithRetryAfter() {
        Fixture fixture = fixture();
        when(fixture.authoring.retryCurrentOperation(any()))
                .thenReturn(new SpeakingPromptAuthoringService.RetryResult(
                        false, 23L, "cooldown"));

        ResponseEntity<?> response = fixture.controller.retryTranscription(
                91L,
                "speaking-a",
                new SpeakingPromptAuthoringController.RevisionRequest(7L, 3L),
                fixture.user);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After"))
                .isEqualTo("23");
        org.mockito.ArgumentCaptor<SpeakingPromptAuthoringService.RetryCommand>
                command = forClass(
                        SpeakingPromptAuthoringService.RetryCommand.class);
        verify(fixture.authoring).retryCurrentOperation(command.capture());
        assertThat(command.getValue().expectedDraftVersion()).isEqualTo(3L);
    }

    @Test
    void transcriptAndUnlinkForwardTheExactWholeDraftVersion() {
        Fixture fixture = fixture();

        fixture.controller.reviseTranscript(
                91L,
                "speaking-a",
                new SpeakingPromptAuthoringController.TranscriptRequest(
                        7L, 3L, "Ngữ cảnh đã kiểm tra.", true),
                fixture.user);
        fixture.controller.unlinkAudio(
                91L, "speaking-a", 8L, 4L, fixture.user);

        org.mockito.ArgumentCaptor<SpeakingPromptTranscriptService.ReviseTranscript>
                transcript = forClass(
                        SpeakingPromptTranscriptService.ReviseTranscript.class);
        verify(fixture.transcript).revise(transcript.capture());
        assertThat(transcript.getValue().expectedDraftVersion()).isEqualTo(3L);
        org.mockito.ArgumentCaptor<SpeakingPromptAuthoringService.SourceCommand>
                unlink = forClass(
                        SpeakingPromptAuthoringService.SourceCommand.class);
        verify(fixture.authoring)
                .unlinkCurrentOriginalAudio(unlink.capture());
        assertThat(unlink.getValue().expectedDraftVersion()).isEqualTo(4L);
    }

    @Test
    void needsReviewRetryReusesCurrentProjectionWithoutQueueingProviderWork() {
        Fixture fixture = fixture();
        when(fixture.authoring.retryCurrentOperation(any()))
                .thenReturn(new SpeakingPromptAuthoringService.RetryResult(
                        false, 0L, "needs_review"));

        ResponseEntity<?> response = fixture.controller.retryTranscription(
                91L,
                "speaking-a",
                new SpeakingPromptAuthoringController.RevisionRequest(7L, 3L),
                fixture.user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(fixture.state).load(91L, "speaking-a", 81L);
    }

    @Test
    void adviceMapsRevisionInputRateAndAvailabilityFailures() {
        SpeakingPromptAuthoringControllerAdvice advice =
                new SpeakingPromptAuthoringControllerAdvice();

        assertThat(advice.conflict(
                new SpeakingPromptAuthoringConflictException("private"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(advice.invalid(
                new IllegalArgumentException("Tệp audio không hợp lệ."))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(advice.provider(new SpeakingPromptAiContract.ProviderFailure(
                SpeakingPromptAiContract.PublicErrorCategory.RATE_LIMIT,
                true,
                "private-provider-reference",
                null)).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(advice.provider(new SpeakingPromptAiContract.ProviderFailure(
                SpeakingPromptAiContract.PublicErrorCategory.CONFIGURATION,
                false,
                null,
                null)).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ResponseEntity<?> invalid = advice.invalid(
                new IllegalArgumentException(
                        "Unsupported audio container or internal setting."));
        SpeakingPromptAuthoringController.ApiFailure publicFailure =
                (SpeakingPromptAuthoringController.ApiFailure) invalid.getBody();
        assertThat(publicFailure.message())
                .isEqualTo("Tệp hoặc nội dung đề bài Nói không hợp lệ.")
                .doesNotContain("Unsupported", "internal");
    }

    private static Fixture fixture() {
        SpeakingPromptAuthoringService authoring =
                mock(SpeakingPromptAuthoringService.class);
        SpeakingPromptAuthoringStateService state =
                mock(SpeakingPromptAuthoringStateService.class);
        SpeakingPromptOriginalAudioUploadCoordinator uploadCoordinator =
                mock(SpeakingPromptOriginalAudioUploadCoordinator.class);
        SpeakingPromptTranscriptService transcript =
                mock(SpeakingPromptTranscriptService.class);
        KshUserDetails user = mock(KshUserDetails.class);
        when(user.getId()).thenReturn(81L);
        return new Fixture(
                authoring,
                uploadCoordinator,
                state,
                transcript,
                user,
                new SpeakingPromptAuthoringController(
                        authoring, uploadCoordinator, state, transcript));
    }

    private record Fixture(
            SpeakingPromptAuthoringService authoring,
            SpeakingPromptOriginalAudioUploadCoordinator uploadCoordinator,
            SpeakingPromptAuthoringStateService state,
            SpeakingPromptTranscriptService transcript,
            KshUserDetails user,
            SpeakingPromptAuthoringController controller) {
    }
}
