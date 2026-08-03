package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves the exact, owned draft section used by request-local authoring. */
@Service
public class PracticeImportTargetService {

    private final PracticeDraftRepository draftRepository;
    private final PracticeAuthorizationService authorizationService;
    private final AssessmentAuthoringCatalogService authoringCatalog;
    private final ObjectMapper objectMapper;

    public PracticeImportTargetService(
            PracticeDraftRepository draftRepository,
            PracticeAuthorizationService authorizationService,
            AssessmentAuthoringCatalogService authoringCatalog,
            ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.authorizationService = authorizationService;
        this.authoringCatalog = authoringCatalog;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ImportStartContext resolveStartContext(
            Long draftId,
            Integer requestedTestNo,
            String requestedSkill,
            String requestedLessonCode,
            Long actorId) {
        PracticeDraft draft = authorizedDraft(draftId, actorId);
        List<TargetSectionOption> sections = readTargetSections(draft);
        if (sections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bản nháp chưa có phần kỹ năng để biên soạn từ Text/PDF.");
        }
        if (requestedTestNo == null || requestedTestNo < 1
                || requestedSkill == null || requestedSkill.isBlank()
                || requestedLessonCode == null || requestedLessonCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Target Test/skill/lesson là bắt buộc.");
        }
        TargetSectionOption selected = sections.stream()
                .filter(section -> requestedTestNo.equals(section.testNo())
                        && requestedSkill.equalsIgnoreCase(section.skill())
                        && requestedLessonCode.equalsIgnoreCase(section.lessonCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Target Test/skill/lesson không khớp bản nháp được cấp quyền."));
        authoringCatalog.defaultTemplate().requireSkill(selected.skill());
        return new ImportStartContext(selected, sections);
    }

    @Transactional(readOnly = true)
    public TargetRoute requireExactTarget(
            Long draftId,
            Integer testNo,
            String skill,
            String lessonCode,
            Long actorId) {
        if (draftId == null || testNo == null || testNo < 1
                || skill == null || lessonCode == null) {
            throw new IllegalArgumentException("Target draft/section là bắt buộc.");
        }
        ImportStartContext context = resolveStartContext(
                draftId, testNo, skill, lessonCode, actorId);
        TargetSectionOption selected = context.selected();
        if (!testNo.equals(selected.testNo())
                || !selected.skill().equalsIgnoreCase(skill)
                || !selected.lessonCode().equalsIgnoreCase(lessonCode)) {
            throw new IllegalArgumentException(
                    "Target Test/skill/lesson không khớp bản nháp được cấp quyền.");
        }
        return new TargetRoute(
                draftId, selected.testNo(), selected.skill(), selected.lessonCode());
    }

    private PracticeDraft authorizedDraft(Long draftId, Long actorId) {
        if (draftId == null) {
            throw new IllegalArgumentException(
                    "Hãy mở công cụ Text/PDF từ một phần trong bản nháp.");
        }
        authorizationService.requireDraft(draftId, actorId, PracticeAction.EDIT);
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Bản nháp liên kết không tồn tại."));
    }

    private List<TargetSectionOption> readTargetSections(PracticeDraft draft) {
        try {
            JsonNode root = objectMapper.readTree(draft.getDraftJson());
            List<TargetSectionOption> result = new ArrayList<>();
            for (JsonNode section : root.path("sections")) {
                String skill = normalizeSkill(section.path("skill").asText(""));
                String lessonCode = section.path("lessonCode").asText("").trim();
                int testNo = section.path("testNo").asInt(
                        testNoFromLesson(lessonCode));
                if (testNo < 1 || skill == null || lessonCode.isBlank()) continue;
                result.add(new TargetSectionOption(
                        testNo,
                        lessonCode,
                        skill,
                        section.path("title").asText(skillLabel(skill))));
            }
            return List.copyOf(result);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Không thể đọc cấu trúc bản nháp hiện tại.", exception);
        }
    }

    private static String normalizeSkill(String value) {
        if (value == null || value.isBlank()) return null;
        String skill = value.trim().toUpperCase(Locale.ROOT);
        return List.of("READING", "LISTENING", "WRITING", "SPEAKING")
                .contains(skill) ? skill : null;
    }

    private static int testNoFromLesson(String lessonCode) {
        if (lessonCode == null
                || !lessonCode.toUpperCase(Locale.ROOT).matches("[LRWS]\\d+")) {
            return 0;
        }
        return Integer.parseInt(lessonCode.substring(1));
    }

    private static String skillLabel(String skill) {
        return switch (skill) {
            case "LISTENING" -> "Phần Nghe";
            case "WRITING" -> "Phần Viết";
            case "SPEAKING" -> "Phần Nói";
            default -> "Phần Đọc";
        };
    }

    public record TargetSectionOption(
            Integer testNo,
            String lessonCode,
            String skill,
            String title) {
    }

    public record ImportStartContext(
            TargetSectionOption selected,
            List<TargetSectionOption> sections) {
        public ImportStartContext {
            sections = sections == null ? List.of() : List.copyOf(sections);
        }
    }
}
