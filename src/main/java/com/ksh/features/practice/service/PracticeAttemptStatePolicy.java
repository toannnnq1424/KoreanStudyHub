package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Shared, side-effect-free interpretation of practice-attempt lifecycle state.
 *
 * <p>Analysis state augments terminal display state only. It never turns an
 * attempt into a different lifecycle state and it never makes an attempt
 * resumable or re-evaluable.</p>
 */
public final class PracticeAttemptStatePolicy {

    public static final PracticeAttemptStatePolicy INSTANCE =
            new PracticeAttemptStatePolicy();

    private static final Comparator<PracticeAttempt> NEWEST_ACTIVITY_FIRST =
            Comparator.comparing(
                            PracticeAttemptStatePolicy::activityAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                            PracticeAttempt::getId,
                            Comparator.nullsLast(Comparator.reverseOrder()));

    public enum DisplayState {
        NOT_STARTED,
        IN_PROGRESS,
        SUBMITTED,
        SCORING,
        SCORED,
        PARTIAL,
        FAILED,
        STALE,
        DISCARDED,
        UNAVAILABLE
    }

    public enum VersionLockState {
        COMPLETE,
        INCOMPLETE,
        INCOMPATIBLE
    }

    public enum ResultEligibility {
        ELIGIBLE_CANONICAL,
        NOT_TERMINAL,
        DISCARDED,
        INCOMPLETE_VERSION_LOCK,
        INCOMPATIBLE_VERSION,
        INCONSISTENT_VERSION_IDENTITY
    }

    public enum ResumeRejection {
        NONE,
        DISCARDED,
        NOT_IN_PROGRESS,
        INCOMPLETE_VERSION_LOCK,
        INCOMPATIBLE_VERSION,
        DEADLINE_EXPIRED,
        INCONSISTENT_VERSION_IDENTITY
    }

    public enum ReEvaluationAction {
        FULL_ATTEMPT,
        SINGLE_WRITING_QUESTION
    }

    public enum ReEvaluationRejection {
        NONE,
        DISCARDED,
        NOT_TERMINAL,
        INCOMPLETE_VERSION_LOCK,
        INCOMPATIBLE_VERSION,
        INCONSISTENT_VERSION_IDENTITY,
        UNSUPPORTED_ACTION
    }

    public record Presentation(
            DisplayState state,
            String code,
            String label,
            Long resumeAttemptId
    ) {
    }

    public record ReEvaluationEligibility(
            boolean eligible,
            ReEvaluationRejection rejection,
            String messageVi
    ) {
        public void requireEligible() {
            if (!eligible) {
                throw new PracticeReEvaluationNotAllowedException(
                        rejection, messageVi);
            }
        }
    }

    public record ResumeEligibility(
            boolean eligible,
            ResumeRejection rejection,
            String messageVi
    ) {
        public void requireEligible() {
            if (!eligible) {
                throw new PracticeAttemptResumeNotAllowedException(
                        rejection, messageVi);
            }
        }
    }

    public static final class PracticeAttemptResumeNotAllowedException
            extends IllegalStateException {
        private final ResumeRejection rejection;

        public PracticeAttemptResumeNotAllowedException(
                ResumeRejection rejection,
                String message
        ) {
            super(message);
            this.rejection = rejection;
        }

        public ResumeRejection getRejection() {
            return rejection;
        }
    }

    public static final class PracticeResultNotAvailableException
            extends IllegalStateException {
        private final ResultEligibility eligibility;

        public PracticeResultNotAvailableException(
                ResultEligibility eligibility,
                String message
        ) {
            super(message);
            this.eligibility = eligibility;
        }

        public ResultEligibility getEligibility() {
            return eligibility;
        }
    }

    public static final class PracticeReEvaluationNotAllowedException
            extends IllegalStateException {
        private final ReEvaluationRejection rejection;

        public PracticeReEvaluationNotAllowedException(
                ReEvaluationRejection rejection,
                String message
        ) {
            super(message);
            this.rejection = rejection;
        }

        public ReEvaluationRejection getRejection() {
            return rejection;
        }
    }

    public boolean isActive(PracticeAttempt attempt) {
        return attempt != null
                && !PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus());
    }

    public boolean isCompleted(PracticeAttempt attempt) {
        return attempt != null
                && (PracticeAttempt.STATUS_SUBMITTED.equals(attempt.getStatus())
                || PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus()));
    }

    public boolean isCanonicalResumable(
            PracticeAttempt attempt,
            boolean coherentVersionIdentity
    ) {
        return resumeEligibility(attempt, coherentVersionIdentity).eligible();
    }

    public boolean isStaleOrRestartRequired(
            PracticeAttempt attempt,
            boolean coherentVersionIdentity
    ) {
        return attempt != null
                && PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())
                && !isCanonicalResumable(
                        attempt, coherentVersionIdentity);
    }

    public boolean isUnavailable(PracticeAttempt attempt) {
        return attempt == null
                || PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus())
                || (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())
                && !isCompleted(attempt));
    }

    public VersionLockState versionLockState(PracticeAttempt attempt) {
        if (attempt == null
                || attempt.getPublishedVersionId() == null
                || attempt.getSetVersionId() == null
                || attempt.getTestVersionId() == null
                || attempt.getSectionVersionId() == null) {
            return VersionLockState.INCOMPLETE;
        }
        String compatibility = attempt.getVersionCompatibilityStatus();
        if (compatibility != null
                && !compatibility.isBlank()
                && !"COMPATIBLE".equalsIgnoreCase(compatibility.strip())) {
            return VersionLockState.INCOMPATIBLE;
        }
        return VersionLockState.COMPLETE;
    }

    public ResumeEligibility resumeEligibility(
            PracticeAttempt attempt,
            boolean coherentVersionIdentity
    ) {
        if (attempt == null
                || PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus())) {
            return resumeRejected(
                    ResumeRejection.DISCARDED,
                    "Lượt làm bài không tồn tại hoặc đã bị hủy.");
        }
        if (!PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            return resumeRejected(
                    ResumeRejection.NOT_IN_PROGRESS,
                    "Lượt làm bài đã kết thúc và không thể tiếp tục.");
        }
        if (attempt.isExpired(java.time.LocalDateTime.now())) {
            return resumeRejected(
                    ResumeRejection.DEADLINE_EXPIRED,
                    "Lượt làm bài đã hết thời gian và không thể tiếp tục.");
        }
        VersionLockState lockState = versionLockState(attempt);
        if (lockState == VersionLockState.INCOMPLETE) {
            return resumeRejected(
                    ResumeRejection.INCOMPLETE_VERSION_LOCK,
                    "Lượt làm bài cũ thiếu khóa phiên bản đầy đủ. "
                            + "Vui lòng quay lại bài kiểm tra và bắt đầu lượt mới.");
        }
        if (lockState == VersionLockState.INCOMPATIBLE) {
            return resumeRejected(
                    ResumeRejection.INCOMPATIBLE_VERSION,
                    "Phiên bản của lượt làm bài không còn tương thích. "
                            + "Vui lòng quay lại bài kiểm tra và bắt đầu lượt mới.");
        }
        if (!coherentVersionIdentity) {
            return resumeRejected(
                    ResumeRejection.INCONSISTENT_VERSION_IDENTITY,
                    "Khóa phiên bản của lượt làm bài không nhất quán. "
                            + "Vui lòng quay lại bài kiểm tra và bắt đầu lượt mới.");
        }
        return new ResumeEligibility(true, ResumeRejection.NONE, null);
    }

    public void requireCanonicalResumeStructure(PracticeAttempt attempt) {
        resumeEligibility(attempt, true).requireEligible();
    }

    public void requireCoherentResumeIdentity(boolean coherent) {
        if (!coherent) {
            throw new PracticeAttemptResumeNotAllowedException(
                    ResumeRejection.INCONSISTENT_VERSION_IDENTITY,
                    "Khóa phiên bản của lượt làm bài không nhất quán. "
                            + "Vui lòng quay lại bài kiểm tra và bắt đầu lượt mới.");
        }
    }

    public ResultEligibility resultEligibility(
            PracticeAttempt attempt,
            boolean coherentVersionIdentity
    ) {
        if (attempt == null
                || PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus())) {
            return ResultEligibility.DISCARDED;
        }
        if (!isCompleted(attempt)) {
            return ResultEligibility.NOT_TERMINAL;
        }
        VersionLockState lockState = versionLockState(attempt);
        if (lockState == VersionLockState.INCOMPLETE) {
            return ResultEligibility.INCOMPLETE_VERSION_LOCK;
        }
        if (lockState == VersionLockState.INCOMPATIBLE) {
            return ResultEligibility.INCOMPATIBLE_VERSION;
        }
        return coherentVersionIdentity
                ? ResultEligibility.ELIGIBLE_CANONICAL
                : ResultEligibility.INCONSISTENT_VERSION_IDENTITY;
    }

    public boolean isResultEligible(
            PracticeAttempt attempt,
            boolean coherentVersionIdentity
    ) {
        return resultEligibility(attempt, coherentVersionIdentity)
                == ResultEligibility.ELIGIBLE_CANONICAL;
    }

    public void requireCanonicalResultStructure(PracticeAttempt attempt) {
        requireResultEligible(attempt, true);
    }

    public void requireCoherentResultIdentity(boolean coherent) {
        if (!coherent) {
            throw new PracticeResultNotAvailableException(
                    ResultEligibility.INCONSISTENT_VERSION_IDENTITY,
                    resultMessage(
                            ResultEligibility.INCONSISTENT_VERSION_IDENTITY));
        }
    }

    public void requireResultEligible(
            PracticeAttempt attempt,
            boolean coherentVersionIdentity
    ) {
        ResultEligibility eligibility =
                resultEligibility(attempt, coherentVersionIdentity);
        if (eligibility != ResultEligibility.ELIGIBLE_CANONICAL) {
            throw new PracticeResultNotAvailableException(
                    eligibility, resultMessage(eligibility));
        }
    }

    public ReEvaluationEligibility reEvaluationEligibility(
            PracticeAttempt attempt,
            ReEvaluationAction action
    ) {
        if (attempt == null
                || PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus())) {
            return rejected(
                    ReEvaluationRejection.DISCARDED,
                    "Lượt làm bài không tồn tại hoặc đã bị hủy.");
        }
        if (!isCompleted(attempt)) {
            return rejected(
                    ReEvaluationRejection.NOT_TERMINAL,
                    "Chỉ có thể chấm lại bài đã nộp hoặc đã chấm xong.");
        }
        VersionLockState lockState = versionLockState(attempt);
        if (lockState == VersionLockState.INCOMPLETE) {
            return rejected(
                    ReEvaluationRejection.INCOMPLETE_VERSION_LOCK,
                    "Bài làm thiếu khóa phiên bản đầy đủ nên không thể chấm lại an toàn.");
        }
        if (lockState == VersionLockState.INCOMPATIBLE) {
            return rejected(
                    ReEvaluationRejection.INCOMPATIBLE_VERSION,
                    "Phiên bản của bài làm không còn tương thích để chấm lại.");
        }
        if (action == ReEvaluationAction.SINGLE_WRITING_QUESTION
                && !"WRITING".equalsIgnoreCase(attempt.getSkill())) {
            return rejected(
                    ReEvaluationRejection.UNSUPPORTED_ACTION,
                    "Chỉ có thể chấm lại từng câu cho bài Writing.");
        }
        if (action == ReEvaluationAction.FULL_ATTEMPT
                && "SPEAKING".equalsIgnoreCase(attempt.getSkill())) {
            return rejected(
                    ReEvaluationRejection.UNSUPPORTED_ACTION,
                    "Chưa hỗ trợ chấm lại bài Speaking.");
        }
        if (action == null
                || (!"WRITING".equalsIgnoreCase(attempt.getSkill())
                && !"READING".equalsIgnoreCase(attempt.getSkill())
                && !"LISTENING".equalsIgnoreCase(attempt.getSkill()))) {
            return rejected(
                    ReEvaluationRejection.UNSUPPORTED_ACTION,
                    "Loại bài này chưa hỗ trợ thao tác chấm lại.");
        }
        return new ReEvaluationEligibility(
                true, ReEvaluationRejection.NONE, null);
    }

    public void requireCoherentReEvaluationIdentity(boolean coherent) {
        if (!coherent) {
            throw new PracticeReEvaluationNotAllowedException(
                    ReEvaluationRejection.INCONSISTENT_VERSION_IDENTITY,
                    "Khóa phiên bản của bài làm không nhất quán nên không thể chấm lại an toàn.");
        }
    }

    public Presentation presentation(
            PracticeAttempt attempt,
            boolean coherentVersionIdentity
    ) {
        if (attempt == null) {
            return presentation(null, DisplayState.NOT_STARTED, null);
        }
        if (PracticeAttempt.STATUS_DISCARDED.equals(attempt.getStatus())) {
            return presentation(attempt, DisplayState.DISCARDED, null);
        }
        if (PracticeAttempt.STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            if (isCanonicalResumable(
                    attempt, coherentVersionIdentity)) {
                return presentation(
                        attempt, DisplayState.IN_PROGRESS, attempt.getId());
            }
            return presentation(attempt, DisplayState.STALE, null);
        }
        String analysis = attempt.getAnalysisStatus();
        if (PracticeAttempt.ANALYSIS_QUEUED.equals(analysis)
                || PracticeAttempt.ANALYSIS_PROCESSING.equals(analysis)) {
            return presentation(attempt, DisplayState.SCORING, null);
        }
        if (PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus())
                && (PracticeAttempt.ANALYSIS_FAILED.equals(analysis)
                || PracticeAttempt.ANALYSIS_UNAVAILABLE.equals(analysis))) {
            return presentation(
                    attempt, DisplayState.PARTIAL, null);
        }
        if (PracticeAttempt.STATUS_GRADED.equals(attempt.getStatus())) {
            return presentation(attempt, DisplayState.SCORED, null);
        }
        if (!PracticeAttempt.STATUS_SUBMITTED.equals(attempt.getStatus())) {
            return presentation(attempt, DisplayState.UNAVAILABLE, null);
        }
        if (PracticeAttempt.ANALYSIS_SUCCEEDED.equals(analysis)) {
            return presentation(attempt, DisplayState.SCORED, null);
        }
        if (PracticeAttempt.ANALYSIS_UNAVAILABLE.equals(analysis)) {
            return presentation(
                    attempt, DisplayState.UNAVAILABLE, null);
        }
        if (PracticeAttempt.ANALYSIS_FAILED.equals(analysis)) {
            return presentation(
                    attempt,
                    attempt.isObjectiveSkill()
                            ? DisplayState.PARTIAL
                            : DisplayState.FAILED,
                    null);
        }
        return presentation(attempt, DisplayState.SUBMITTED, null);
    }

    public Comparator<PracticeAttempt> newestActivityFirst() {
        return NEWEST_ACTIVITY_FIRST;
    }

    public PracticeAttempt newest(List<PracticeAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return null;
        }
        return attempts.stream()
                .filter(attempt -> attempt != null)
                .min(NEWEST_ACTIVITY_FIRST)
                .orElse(null);
    }

    public PracticeAttempt newestCanonicalResumable(
            List<PracticeAttempt> attempts,
            java.util.function.Predicate<PracticeAttempt>
                    coherentVersionIdentity
    ) {
        if (attempts == null || attempts.isEmpty()) {
            return null;
        }
        return attempts.stream()
                .filter(attempt -> attempt != null)
                .filter(attempt -> isCanonicalResumable(
                        attempt,
                        coherentVersionIdentity.test(attempt)))
                .min(NEWEST_ACTIVITY_FIRST)
                .orElse(null);
    }

    public static LocalDateTime activityAt(PracticeAttempt attempt) {
        if (attempt == null) return null;
        if (attempt.getSubmittedAt() != null) return attempt.getSubmittedAt();
        if (attempt.getUpdatedAt() != null) return attempt.getUpdatedAt();
        return attempt.getCreatedAt();
    }

    private Presentation presentation(
            PracticeAttempt attempt,
            DisplayState state,
            Long resumeAttemptId
    ) {
        boolean speaking = attempt != null
                && "SPEAKING".equalsIgnoreCase(attempt.getSkill());
        return new Presentation(
                state,
                state.name(),
                label(state, speaking),
                resumeAttemptId);
    }

    public String label(PracticeAttempt attempt) {
        return label(attempt, true);
    }

    public String label(
            PracticeAttempt attempt,
            boolean coherentVersionIdentity
    ) {
        Presentation presentation =
                presentation(attempt, coherentVersionIdentity);
        return label(
                presentation.state(),
                attempt != null
                        && "SPEAKING".equalsIgnoreCase(attempt.getSkill()));
    }

    private String label(DisplayState state, boolean speaking) {
        return switch (state) {
            case NOT_STARTED -> "Chưa bắt đầu";
            case IN_PROGRESS -> "Đang làm";
            case SUBMITTED -> "Đã nộp";
            case SCORING -> speaking
                    ? "Đang xử lý phản hồi"
                    : "Đang chấm";
            case SCORED -> speaking
                    ? "Đã xử lý phản hồi"
                    : "Đã có kết quả";
            case PARTIAL -> "Có điểm, thiếu phản hồi";
            case FAILED -> speaking
                    ? "Chưa thể xử lý phản hồi"
                    : "Chấm điểm thất bại";
            case STALE -> "Cần bắt đầu lại";
            case DISCARDED -> "Đã hủy";
            case UNAVAILABLE -> "Không khả dụng";
        };
    }

    private ReEvaluationEligibility rejected(
            ReEvaluationRejection rejection,
            String message
    ) {
        return new ReEvaluationEligibility(false, rejection, message);
    }

    private ResumeEligibility resumeRejected(
            ResumeRejection rejection,
            String message
    ) {
        return new ResumeEligibility(false, rejection, message);
    }

    private String resultMessage(ResultEligibility eligibility) {
        return switch (eligibility) {
            case NOT_TERMINAL ->
                    "Kết quả chỉ khả dụng sau khi bài làm đã được nộp.";
            case DISCARDED ->
                    "Kết quả không tồn tại hoặc lượt làm bài đã bị hủy.";
            case INCOMPLETE_VERSION_LOCK ->
                    "Bài làm thiếu khóa phiên bản bất biến đầy đủ nên "
                            + "không thể hiển thị kết quả an toàn.";
            case INCOMPATIBLE_VERSION ->
                    "Phiên bản của bài làm không còn tương thích để "
                            + "hiển thị kết quả.";
            case INCONSISTENT_VERSION_IDENTITY ->
                    "Khóa phiên bản của bài làm không nhất quán nên "
                            + "không thể hiển thị kết quả an toàn.";
            case ELIGIBLE_CANONICAL -> null;
        };
    }
}
