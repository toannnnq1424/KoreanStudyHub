package com.ksh.features.practice.manage.speaking;

import com.ksh.features.practice.governance.PracticeAction;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SpeakingPromptTranscriptService {

    private final SpeakingPromptDraftAuthority draftAuthority;
    private final SpeakingPromptSourceRepository sourceRepository;
    private final SpeakingPromptAiArtifactRepository artifactRepository;
    private final SpeakingPromptTranscriptRevisionRepository revisionRepository;
    private final SpeakingPromptFingerprintService fingerprintService;

    public SpeakingPromptTranscriptService(
            SpeakingPromptDraftAuthority draftAuthority,
            SpeakingPromptSourceRepository sourceRepository,
            SpeakingPromptAiArtifactRepository artifactRepository,
            SpeakingPromptTranscriptRevisionRepository revisionRepository,
            SpeakingPromptFingerprintService fingerprintService) {
        this.draftAuthority = draftAuthority;
        this.sourceRepository = sourceRepository;
        this.artifactRepository = artifactRepository;
        this.revisionRepository = revisionRepository;
        this.fingerprintService = fingerprintService;
    }

    @Transactional
    public RevisionResult revise(ReviseTranscript command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.EDIT);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = sourceRepository
                .findByDraftAndClientForUpdate(
                        command.draftId(), command.questionClientId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Nguồn đề Nói chưa tồn tại."));
        source.requireExpectedRevision(command.expectedSourceRevision());
        Long artifactId = command.artifactId() == null
                ? source.getCurrentSttArtifactId()
                : command.artifactId();
        if (!Objects.equals(
                    source.getOwnerLecturerId(), authorized.ownerId())
                || !Objects.equals(
                    source.getCurrentSttArtifactId(), artifactId)) {
            throw new AccessDeniedException(
                    "Bản chép lời không thuộc nguồn đề Nói hiện tại.");
        }
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findByIdForUpdate(artifactId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Bản chép lời không tồn tại."));
        if (!Objects.equals(
                    artifact.getOwnerLecturerId(), authorized.ownerId())
                || !SpeakingPromptAiContract.Operation.STT.code().equals(
                        artifact.getOperation())
                || !source.currentForArtifact(artifact)
                || source.getCurrentTranscriptRevisionId() == null) {
            throw new AccessDeniedException(
                    "Bản chép lời không thuộc chủ sở hữu bản nháp.");
        }
        String corrected = command.correctedContextText();
        if (corrected == null
                || corrected.isBlank()
                || corrected.length()
                    > SpeakingPromptAiContract.MAX_PROMPT_TRANSCRIPT_CHARS) {
            throw new IllegalArgumentException(
                    "Bản chép lời đã chỉnh sửa không hợp lệ.");
        }
        String contextHash = fingerprintService.exactTextSha256(corrected);
        int revisionNumber =
                revisionRepository.findMaximumRevisionNumber(artifact.getId()) + 1;
        LocalDateTime now = LocalDateTime.now();
        SpeakingPromptTranscriptRevision revision = revisionRepository.saveAndFlush(
                SpeakingPromptTranscriptRevision.lecturerEdit(
                    artifact,
                    revisionNumber,
                    corrected,
                    contextHash,
                    command.actorId(),
                    command.confirmed() ? now : null));
        source.recordTranscriptEdit(
                revision.getId(),
                command.actorId(),
                command.confirmed(),
                now);
        sourceRepository.save(source);
        return new RevisionResult(
                source.getId(),
                source.getSourceRevision(),
                artifact.getId(),
                revisionNumber,
                command.confirmed());
    }

    @Transactional
    public RevisionResult confirm(ConfirmTranscript command) {
        SpeakingPromptDraftAuthority.AuthorizedDraft authorized =
                draftAuthority.authorizeAndLock(
                        command.draftId(),
                        command.questionClientId(),
                        command.actorId(),
                        PracticeAction.EDIT);
        draftAuthority.requireExpectedVersion(
                authorized, command.expectedDraftVersion());
        SpeakingPromptSource source = sourceRepository
                .findByDraftAndClientForUpdate(
                        command.draftId(), command.questionClientId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Nguồn đề Nói chưa tồn tại."));
        source.requireExpectedRevision(command.expectedSourceRevision());
        Long artifactId = command.artifactId() == null
                ? source.getCurrentSttArtifactId()
                : command.artifactId();
        if (!Objects.equals(source.getOwnerLecturerId(), authorized.ownerId())
                || !Objects.equals(
                        source.getCurrentSttArtifactId(), artifactId)) {
            throw new AccessDeniedException(
                    "Bản chép lời không thuộc nguồn đề Nói hiện tại.");
        }
        SpeakingPromptAiArtifact artifact = artifactRepository
                .findByIdForUpdate(artifactId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Bản chép lời không tồn tại."));
        if (!Objects.equals(
                    artifact.getOwnerLecturerId(), authorized.ownerId())
                || !source.currentForArtifact(artifact)
                || source.getCurrentTranscriptRevisionId() == null) {
            throw new AccessDeniedException(
                    "Bản chép lời không thuộc chủ sở hữu bản nháp.");
        }
        SpeakingPromptTranscriptRevision currentRevision = revisionRepository
                .findById(source.getCurrentTranscriptRevisionId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Bản sửa lời chép hiện tại không tồn tại."));
        if (!Objects.equals(currentRevision.getArtifactId(), artifact.getId())
                || !Objects.equals(
                        currentRevision.getOwnerLecturerId(),
                        authorized.ownerId())) {
            throw new AccessDeniedException(
                    "Bản sửa lời chép không thuộc nguồn hiện tại.");
        }
        LocalDateTime now = LocalDateTime.now();
        int revisionNumber =
                revisionRepository.findMaximumRevisionNumber(artifact.getId()) + 1;
        SpeakingPromptTranscriptRevision confirmed =
                revisionRepository.saveAndFlush(
                        SpeakingPromptTranscriptRevision.lecturerEdit(
                                artifact,
                                revisionNumber,
                                currentRevision.getContextText(),
                                currentRevision.getContextSha256(),
                                command.actorId(),
                                now));
        source.recordTranscriptEdit(
                confirmed.getId(), command.actorId(), true, now);
        sourceRepository.save(source);
        return new RevisionResult(
                source.getId(),
                source.getSourceRevision(),
                artifact.getId(),
                revisionNumber,
                true);
    }

    public record ReviseTranscript(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion,
            Long artifactId,
            String correctedContextText,
            boolean confirmed) {
        @Override
        public String toString() {
            return "ReviseTranscript{draftId=" + draftId
                    + ", questionClientId='" + questionClientId + '\''
                    + ", artifactId=" + artifactId
                    + ", correctedContextTextLength="
                    + (correctedContextText == null
                        ? 0 : correctedContextText.length())
                    + ", confirmed=" + confirmed
                    + '}';
        }
    }

    public record ConfirmTranscript(
            Long draftId,
            String questionClientId,
            Long actorId,
            long expectedSourceRevision,
            long expectedDraftVersion,
            Long artifactId) {
    }

    public record RevisionResult(
            Long sourceId,
            long sourceRevision,
            Long artifactId,
            int revisionNumber,
            boolean confirmed) {
    }
}
