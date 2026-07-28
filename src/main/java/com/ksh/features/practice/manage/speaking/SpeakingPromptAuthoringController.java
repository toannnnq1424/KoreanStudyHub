package com.ksh.features.practice.manage.speaking;

import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(
        "/practice/manage/drafts/{draftId}/questions/{clientId}/speaking-prompt")
@PreAuthorize(Roles.PREAUTH_LECTURER)
public class SpeakingPromptAuthoringController {

    private static final CacheControl NO_STORE = CacheControl
            .maxAge(0, TimeUnit.SECONDS)
            .noStore()
            .cachePrivate()
            .mustRevalidate();

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            ".mp3", ".wav", ".m4a", ".ogg", ".webm");

    private final SpeakingPromptAuthoringService authoringService;
    private final SpeakingPromptOriginalAudioUploadCoordinator uploadCoordinator;
    private final SpeakingPromptAuthoringStateService stateService;
    private final SpeakingPromptTranscriptService transcriptService;

    public SpeakingPromptAuthoringController(
            SpeakingPromptAuthoringService authoringService,
            SpeakingPromptOriginalAudioUploadCoordinator uploadCoordinator,
            SpeakingPromptAuthoringStateService stateService,
            SpeakingPromptTranscriptService transcriptService) {
        this.authoringService = authoringService;
        this.uploadCoordinator = uploadCoordinator;
        this.stateService = stateService;
        this.transcriptService = transcriptService;
    }

    @GetMapping
    public SpeakingPromptAuthoringStateService.EditorState get(
            @PathVariable Long draftId,
            @PathVariable String clientId,
            @AuthenticationPrincipal KshUserDetails user) {
        return stateService.load(draftId, clientId, user.getId());
    }

    @GetMapping("/media/{origin}")
    public ResponseEntity<org.springframework.core.io.Resource> media(
            @PathVariable Long draftId,
            @PathVariable String clientId,
            @PathVariable String origin,
            @AuthenticationPrincipal KshUserDetails user) {
        SpeakingPromptAssetService.MediaResource media =
                stateService.loadMedia(
                        draftId, clientId, user.getId(), origin);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(media.mimeType());
        } catch (IllegalArgumentException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .contentType(contentType)
                .contentLength(media.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.inline()
                                .filename(
                                        media.filename(),
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(media.resource());
    }

    @PutMapping
    public SpeakingPromptAuthoringStateService.EditorState save(
            @PathVariable Long draftId,
            @PathVariable String clientId,
            @Valid @RequestBody SaveRequest request,
            @AuthenticationPrincipal KshUserDetails user) {
        if (SpeakingPromptSource.INPUT_AUDIO_UPLOAD.equals(request.inputType())) {
            authoringService.selectAudioMode(
                    new SpeakingPromptAuthoringService.SourceCommand(
                            draftId,
                            clientId,
                            user.getId(),
                            request.expectedSourceRevision(),
                            request.expectedDraftVersion()));
        } else if (SpeakingPromptSource.INPUT_MANUAL_TEXT.equals(
                request.inputType())) {
            authoringService.saveManualPrompt(
                    new SpeakingPromptAuthoringService.SaveManualPrompt(
                            draftId,
                            clientId,
                            user.getId(),
                            request.expectedSourceRevision(),
                            request.expectedDraftVersion(),
                            request.manualText(),
                            request.ttsEnabled(),
                            request.voiceCode(),
                            request.speed(),
                            request.outputFormat()));
        } else {
            throw new IllegalArgumentException(
                    "Chế độ nhập đề bài Nói không hợp lệ.");
        }
        return stateService.load(draftId, clientId, user.getId());
    }

    @PostMapping(path = "/audio", consumes = "multipart/form-data")
    public ResponseEntity<SpeakingPromptAuthoringStateService.EditorState> upload(
            @PathVariable Long draftId,
            @PathVariable String clientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("expectedSourceRevision") long expectedSourceRevision,
            @RequestParam("expectedDraftVersion") long expectedDraftVersion,
            @AuthenticationPrincipal KshUserDetails user) {
        validateAudioFilename(file);
        uploadCoordinator.uploadAndEnqueueStt(
                new SpeakingPromptAuthoringService.UploadOriginalAudio(
                        draftId,
                        clientId,
                        user.getId(),
                        expectedSourceRevision,
                        expectedDraftVersion,
                        file));
        return ResponseEntity.accepted().body(
                stateService.load(draftId, clientId, user.getId()));
    }

    @PostMapping("/audio/excel-staging")
    public ResponseEntity<SpeakingPromptAuthoringStateService.EditorState>
            adoptExcelStaging(
                    @PathVariable Long draftId,
                    @PathVariable String clientId,
                    @Valid @RequestBody RevisionRequest request,
                    @AuthenticationPrincipal KshUserDetails user) {
        uploadCoordinator.adoptExcelStagingAndEnqueueStt(
                new SpeakingPromptAuthoringService.SourceCommand(
                        draftId,
                        clientId,
                        user.getId(),
                        request.expectedSourceRevision(),
                        request.expectedDraftVersion()));
        return ResponseEntity.accepted().body(
                stateService.load(draftId, clientId, user.getId()));
    }

    @PostMapping("/transcription/retry")
    public ResponseEntity<?> retryTranscription(
            @PathVariable Long draftId,
            @PathVariable String clientId,
            @Valid @RequestBody RevisionRequest request,
            @AuthenticationPrincipal KshUserDetails user) {
        SpeakingPromptAuthoringService.RetryResult result =
                authoringService.retryCurrentOperation(
                        new SpeakingPromptAuthoringService.RetryCommand(
                                draftId,
                                clientId,
                                user.getId(),
                                request.expectedSourceRevision(),
                                request.expectedDraftVersion(),
                                SpeakingPromptAiContract.Operation.STT));
        return retryResponse(result, draftId, clientId, user.getId());
    }

    @PutMapping("/transcription")
    public SpeakingPromptAuthoringStateService.EditorState reviseTranscript(
            @PathVariable Long draftId,
            @PathVariable String clientId,
            @Valid @RequestBody TranscriptRequest request,
            @AuthenticationPrincipal KshUserDetails user) {
        if (!request.confirmed()) {
            throw new IllegalArgumentException(
                    "Giảng viên cần xác nhận ngữ cảnh trước khi lưu.");
        }
        transcriptService.revise(
                new SpeakingPromptTranscriptService.ReviseTranscript(
                        draftId,
                        clientId,
                        user.getId(),
                        request.expectedSourceRevision(),
                        request.expectedDraftVersion(),
                        null,
                        request.lecturerContext(),
                        request.confirmed()));
        return stateService.load(draftId, clientId, user.getId());
    }

    @PostMapping("/tts")
    public ResponseEntity<?> generateTts(
            @PathVariable Long draftId,
            @PathVariable String clientId,
            @Valid @RequestBody TtsRequest request,
            @AuthenticationPrincipal KshUserDetails user) {
        SpeakingPromptAuthoringService.EnqueueResult result =
                authoringService.requestTts(
                        new SpeakingPromptAuthoringService.GenerateTts(
                                draftId,
                                clientId,
                                user.getId(),
                                request.expectedSourceRevision(),
                                request.expectedDraftVersion(),
                                request.voiceCode(),
                                request.speed(),
                                request.outputFormat()));
        SpeakingPromptAuthoringStateService.EditorState state =
                stateService.load(draftId, clientId, user.getId());
        if (result.reusedReady()) {
            return ResponseEntity.ok(state);
        }
        if (SpeakingPromptSource.STATUS_FAILED_RETRYABLE.equals(result.status())) {
            throw new SpeakingPromptAiContract.ProviderFailure(
                    SpeakingPromptAiContract.PublicErrorCategory.TRANSPORT,
                    true,
                    null,
                    null);
        }
        if (SpeakingPromptSource.STATUS_FAILED_FINAL.equals(result.status())) {
            throw new IllegalArgumentException(
                    "Nội dung hiện tại không thể tạo audio.");
        }
        return ResponseEntity.accepted().body(state);
    }

    @DeleteMapping("/audio")
    public SpeakingPromptAuthoringStateService.EditorState unlinkAudio(
            @PathVariable Long draftId,
            @PathVariable String clientId,
            @RequestParam("expectedSourceRevision") long expectedSourceRevision,
            @RequestParam("expectedDraftVersion") long expectedDraftVersion,
            @AuthenticationPrincipal KshUserDetails user) {
        authoringService.unlinkCurrentOriginalAudio(
                new SpeakingPromptAuthoringService.SourceCommand(
                        draftId,
                        clientId,
                        user.getId(),
                        expectedSourceRevision,
                        expectedDraftVersion));
        return stateService.load(draftId, clientId, user.getId());
    }

    private ResponseEntity<?> retryResponse(
            SpeakingPromptAuthoringService.RetryResult result,
            Long draftId,
            String clientId,
            Long actorId) {
        if ("queued".equals(result.status())
                || "already_active".equals(result.status())) {
            return ResponseEntity.accepted().body(
                    stateService.load(draftId, clientId, actorId));
        }
        if ("needs_review".equals(result.status())) {
            return ResponseEntity.ok(
                    stateService.load(draftId, clientId, actorId));
        }
        if ("cooldown".equals(result.status())
                || "quota".equals(result.status())) {
            long retryAfter = Math.max(1L, result.retryAfterSeconds());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                    .body(new ApiFailure(
                            "RETRY_LIMIT",
                            "Bạn đang thao tác quá nhanh. Vui lòng thử lại sau.",
                            retryAfter));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiFailure(
                        "NOT_RETRYABLE",
                        "Nguồn hiện tại không thể thử lại. Hãy kiểm tra bản chép lời hoặc thay file audio.",
                        null));
    }

    private static void validateAudioFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp audio tải lên rỗng.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Tên tệp audio không hợp lệ.");
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0
                ? ""
                : filename.substring(dot).toLowerCase(Locale.ROOT);
        if (!AUDIO_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Định dạng tệp audio không được hỗ trợ.");
        }
    }

    public record SaveRequest(
            @NotBlank String inputType,
            @NotNull Long expectedSourceRevision,
            @NotNull Long expectedDraftVersion,
            String manualText,
            boolean ttsEnabled,
            String voiceCode,
            BigDecimal speed,
            String outputFormat) {
        @Override
        public String toString() {
            return "SaveRequest{inputType='" + inputType
                    + "', expectedSourceRevision=" + expectedSourceRevision
                    + ", manualTextLength="
                    + (manualText == null ? 0 : manualText.length())
                    + ", ttsEnabled=" + ttsEnabled + '}';
        }
    }

    public record RevisionRequest(
            @NotNull Long expectedSourceRevision,
            @NotNull Long expectedDraftVersion) {
    }

    public record TranscriptRequest(
            @NotNull Long expectedSourceRevision,
            @NotNull Long expectedDraftVersion,
            @NotBlank String lecturerContext,
            boolean confirmed) {
        @Override
        public String toString() {
            return "TranscriptRequest{expectedSourceRevision="
                    + expectedSourceRevision
                    + ", lecturerContextLength="
                    + (lecturerContext == null ? 0 : lecturerContext.length())
                    + ", confirmed=" + confirmed + '}';
        }
    }

    public record TtsRequest(
            @NotNull Long expectedSourceRevision,
            @NotNull Long expectedDraftVersion,
            @NotNull String voiceCode,
            @NotNull BigDecimal speed,
            @NotNull String outputFormat) {
    }

    public record ApiFailure(
            String code,
            String message,
            Long retryAfterSeconds) {
    }
}
