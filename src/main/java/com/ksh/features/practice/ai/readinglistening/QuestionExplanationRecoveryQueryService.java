package com.ksh.features.practice.ai.readinglistening;

import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.QuestionExplanationArtifact;
import com.ksh.entities.QuestionExplanationGenerationTask;
import com.ksh.entities.QuestionVersionExplanationBinding;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationRetryService.RecoveryState;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.PracticePublishedVersionRepository;
import com.ksh.features.practice.repository.PracticeQuestionVersionRepository;
import com.ksh.features.practice.repository.PracticeSectionVersionRepository;
import com.ksh.features.practice.repository.QuestionExplanationArtifactRepository;
import com.ksh.features.practice.repository.QuestionExplanationGenerationTaskRepository;
import com.ksh.features.practice.repository.QuestionVersionExplanationBindingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionExplanationRecoveryQueryService {

    private final PracticeAuthorizationService authorizationService;
    private final PracticePublishedVersionRepository publishedVersionRepository;
    private final PracticeQuestionVersionRepository questionRepository;
    private final PracticeSectionVersionRepository sectionRepository;
    private final QuestionVersionExplanationBindingRepository bindingRepository;
    private final QuestionExplanationArtifactRepository artifactRepository;
    private final QuestionExplanationGenerationTaskRepository taskRepository;

    public QuestionExplanationRecoveryQueryService(
            PracticeAuthorizationService authorizationService,
            PracticePublishedVersionRepository publishedVersionRepository,
            PracticeQuestionVersionRepository questionRepository,
            PracticeSectionVersionRepository sectionRepository,
            QuestionVersionExplanationBindingRepository bindingRepository,
            QuestionExplanationArtifactRepository artifactRepository,
            QuestionExplanationGenerationTaskRepository taskRepository) {
        this.authorizationService = authorizationService;
        this.publishedVersionRepository = publishedVersionRepository;
        this.questionRepository = questionRepository;
        this.sectionRepository = sectionRepository;
        this.bindingRepository = bindingRepository;
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<RecoveryRow> load(
            Long setId,
            List<Long> selectedPublishedVersionIds,
            Long requestedBy) {
        authorizationService.requireSet(setId, requestedBy, PracticeAction.PUBLISH);

        List<Long> versionIds = selectedPublishedVersionIds == null
                ? List.of()
                : selectedPublishedVersionIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (versionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, PracticePublishedVersion> versions = index(
                publishedVersionRepository.findAllById(versionIds),
                PracticePublishedVersion::getId);
        if (versions.size() != versionIds.size()
                || versions.values().stream()
                        .anyMatch(version -> !Objects.equals(setId, version.getSetId()))) {
            throw new EntityNotFoundException(
                    "Không tìm thấy phiên bản đã xuất bản của học liệu đã chọn.");
        }

        List<PracticeQuestionVersion> questions = questionRepository
                .findByPublishedVersionIdInOrderByPublishedVersionIdAscSectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc(
                        versionIds);
        Map<Long, PracticeSectionVersion> sections = index(
                sectionRepository.findAllById(
                        questions.stream()
                                .map(PracticeQuestionVersion::getSectionVersionId)
                                .collect(Collectors.toCollection(java.util.LinkedHashSet::new))),
                PracticeSectionVersion::getId);

        List<PracticeQuestionVersion> readingListeningQuestions = questions.stream()
                .filter(question -> {
                    PracticeSectionVersion section = sections.get(question.getSectionVersionId());
                    return versions.containsKey(question.getPublishedVersionId())
                            && section != null
                            && Objects.equals(
                                    question.getPublishedVersionId(),
                                    section.getPublishedVersionId())
                            && Set.of("READING", "LISTENING")
                                    .contains(normalized(section.getSkill()));
                })
                .toList();
        if (readingListeningQuestions.isEmpty()) {
            return List.of();
        }
        Set<Long> questionIds = readingListeningQuestions.stream()
                .map(PracticeQuestionVersion::getId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        Map<Long, QuestionVersionExplanationBinding> bindingsByQuestion = bindingRepository
                .findByQuestionVersionIdInAndExplanationLanguage(
                        questionIds,
                        ReadingListeningExplanationClient.EXPLANATION_LANGUAGE)
                .stream()
                .sorted(Comparator.comparing(QuestionVersionExplanationBinding::getId))
                .collect(Collectors.toMap(
                        QuestionVersionExplanationBinding::getQuestionVersionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<Long> artifactIds = bindingsByQuestion.values().stream()
                .map(QuestionVersionExplanationBinding::getArtifactId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<Long, QuestionExplanationArtifact> artifacts = index(
                artifactRepository.findAllById(artifactIds),
                QuestionExplanationArtifact::getId);
        Map<Long, QuestionExplanationGenerationTask> tasksByArtifact = artifactIds.isEmpty()
                ? Map.of()
                : taskRepository.findByArtifactIdIn(artifactIds)
                        .stream()
                        .sorted(Comparator.comparing(QuestionExplanationGenerationTask::getId))
                        .collect(Collectors.toMap(
                                QuestionExplanationGenerationTask::getArtifactId,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new));

        Set<Long> sourceQuestionIds = tasksByArtifact.values().stream()
                .map(QuestionExplanationGenerationTask::getSourceQuestionVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<Long, PracticeQuestionVersion> sourceQuestions = sourceQuestionIds.isEmpty()
                ? Map.of()
                : index(
                        questionRepository.findAllById(sourceQuestionIds),
                        PracticeQuestionVersion::getId);
        Set<Long> sourceSectionIds = sourceQuestions.values().stream()
                .map(PracticeQuestionVersion::getSectionVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<Long, PracticeSectionVersion> sourceSections = sourceSectionIds.isEmpty()
                ? Map.of()
                : index(
                        sectionRepository.findAllById(sourceSectionIds),
                        PracticeSectionVersion::getId);
        Map<Long, QuestionVersionExplanationBinding> sourceBindingsByQuestion =
                sourceQuestionIds.isEmpty()
                        ? Map.of()
                        : bindingRepository
                                .findByQuestionVersionIdInAndExplanationLanguage(
                                        sourceQuestionIds,
                                        ReadingListeningExplanationClient.EXPLANATION_LANGUAGE)
                                .stream()
                                .sorted(Comparator.comparing(
                                        QuestionVersionExplanationBinding::getId))
                                .collect(Collectors.toMap(
                                        QuestionVersionExplanationBinding::getQuestionVersionId,
                                        Function.identity(),
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        LocalDateTime now = LocalDateTime.now();
        return readingListeningQuestions.stream()
                .map(question -> row(
                        question,
                        versions.get(question.getPublishedVersionId()),
                        sections.get(question.getSectionVersionId()),
                        bindingsByQuestion.get(question.getId()),
                        artifacts,
                        tasksByArtifact,
                        sourceQuestions,
                        sourceSections,
                        sourceBindingsByQuestion,
                        now))
                .toList();
    }

    private static RecoveryRow row(
            PracticeQuestionVersion question,
            PracticePublishedVersion version,
            PracticeSectionVersion section,
            QuestionVersionExplanationBinding binding,
            Map<Long, QuestionExplanationArtifact> artifacts,
            Map<Long, QuestionExplanationGenerationTask> tasks,
            Map<Long, PracticeQuestionVersion> sourceQuestions,
            Map<Long, PracticeSectionVersion> sourceSections,
            Map<Long, QuestionVersionExplanationBinding> sourceBindings,
            LocalDateTime now) {
        QuestionExplanationArtifact artifact = binding == null
                ? null
                : artifacts.get(binding.getArtifactId());
        QuestionExplanationGenerationTask task = artifact == null
                ? null
                : tasks.get(artifact.getId());
        boolean validBinding = QuestionExplanationRetryService.validBinding(
                question, section, binding, artifact);
        PracticeQuestionVersion sourceQuestion = task == null
                ? null
                : sourceQuestions.get(task.getSourceQuestionVersionId());
        PracticeSectionVersion sourceSection = sourceQuestion == null
                ? null
                : sourceSections.get(sourceQuestion.getSectionVersionId());
        QuestionVersionExplanationBinding sourceBinding = sourceQuestion == null
                ? null
                : sourceBindings.get(sourceQuestion.getId());
        boolean validTaskSource = QuestionExplanationRetryService.validTaskSource(
                artifact, task, sourceQuestion, sourceSection, sourceBinding);
        RecoveryState state = validBinding
                ? QuestionExplanationRetryService.recoveryState(
                        artifact, task, validTaskSource, now)
                : RecoveryState.FAILED_NON_RETRYABLE;
        long retryAfterSeconds = state == RecoveryState.RATE_LIMITED
                ? QuestionExplanationRetryService.retryAfterSeconds(task, now)
                : 0;
        return new RecoveryRow(
                question.getId(),
                version.getVersionNumber(),
                section.getSkill(),
                skillLabel(section.getSkill()),
                section.getTitle(),
                question.getQuestionNo(),
                question.getPrompt(),
                state,
                retryAfterSeconds);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String skillLabel(String skill) {
        return "LISTENING".equals(normalized(skill)) ? "Nghe" : "Đọc";
    }

    private static <T> Map<Long, T> index(
            Collection<T> values,
            Function<T, Long> id) {
        return values.stream().collect(Collectors.toMap(
                id,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    public record RecoveryRow(
            Long questionVersionId,
            Integer publishedVersionNumber,
            String skill,
            String skillLabel,
            String sectionTitle,
            Integer questionNo,
            String prompt,
            RecoveryState state,
            long retryAfterSeconds) {

        public boolean retryableAction() {
            return state == RecoveryState.FAILED_RETRYABLE;
        }

        public String stateLabel() {
            return switch (state) {
                case READY -> "Giải thích đã sẵn sàng";
                case PENDING -> "Đang xử lý giải thích";
                case FAILED_RETRYABLE -> "Có thể thử lại";
                case RATE_LIMITED -> "Tạm thời phải chờ";
                case FAILED_NON_RETRYABLE -> "Cần sửa và xuất bản lại";
            };
        }

        public String guidance() {
            return switch (state) {
                case READY -> "Không cần tạo thêm yêu cầu.";
                case PENDING -> "Hệ thống đang xử lý yêu cầu hiện có; tải lại trang không tạo yêu cầu mới.";
                case FAILED_RETRYABLE -> "Lỗi xử lý cuối cùng có thể được xếp lịch lại một lần.";
                case RATE_LIMITED -> "Vui lòng chờ khoảng " + retryAfterSeconds
                        + " giây trước khi thử lại.";
                case FAILED_NON_RETRYABLE ->
                        "Hãy sửa nội dung hoặc bằng chứng của câu hỏi rồi xuất bản phiên bản mới.";
            };
        }
    }
}
