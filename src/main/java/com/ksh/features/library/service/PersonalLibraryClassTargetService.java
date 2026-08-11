package com.ksh.features.library.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.entities.LibraryAsset;
import com.ksh.entities.Section;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.features.library.dto.LibraryDtos.PersonalAssetClassTarget;
import com.ksh.features.library.dto.LibraryDtos.PersonalAssetClassTargets;
import com.ksh.features.library.dto.LibraryDtos.PersonalAssetLessonTarget;
import com.ksh.features.library.dto.LibraryDtos.PersonalAssetSectionTarget;
import com.ksh.security.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ksh.common.IConstant.MSG_LIBRARY_BIND_INVALID_KIND;
import static com.ksh.entities.LibraryAsset.KIND_DOCUMENT;

/** Read-only owner/admin target tree for sharing one personal document. */
@Service
public class PersonalLibraryClassTargetService {

    /** Keeps generated IN clauses comfortably below database parameter limits. */
    static final int TARGET_QUERY_BATCH_SIZE = 250;

    private final LibraryService libraryService;
    private final ClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final LessonRepository lessonRepository;

    public PersonalLibraryClassTargetService(LibraryService libraryService,
                                             ClassRepository classRepository,
                                             SectionRepository sectionRepository,
                                             LessonRepository lessonRepository) {
        this.libraryService = libraryService;
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional(readOnly = true)
    public PersonalAssetClassTargets targets(Long actorId, Role role, Long assetId) {
        LibraryAsset asset = libraryService.getOwnedAsset(actorId, assetId);
        if (!KIND_DOCUMENT.equals(asset.getKind())) {
            throw new IllegalArgumentException(MSG_LIBRARY_BIND_INVALID_KIND);
        }
        // Validate the persisted row/key before offering any target for it.
        libraryService.requireOwnedStorageKey(actorId, asset);

        List<ClassEntity> candidates;
        if (role == Role.ADMIN) {
            candidates = classRepository.findAllByOrderByCreatedAtDesc();
        } else if (role == Role.LECTURER || role == Role.LEADER) {
            candidates = classRepository.findAllByLecturerIdOrderByCreatedAtDesc(actorId);
        } else {
            throw new AccessDeniedException("Bạn không có quyền chia sẻ tài liệu vào lớp");
        }

        List<ClassEntity> eligibleClasses = candidates.stream()
                .filter(clazz -> !ClassEntity.STATUS_ARCHIVED.equals(clazz.getStatus()))
                .toList();
        List<Section> allSections = loadSections(eligibleClasses.stream()
                .map(ClassEntity::getId).toList());
        Map<Long, List<Section>> sectionsByClass = groupSectionsByClass(allSections);
        Map<Long, List<Lesson>> lessonsBySection = groupLessonsBySection(loadLessons(
                allSections.stream().map(Section::getId).toList()));

        List<PersonalAssetClassTarget> classes = new ArrayList<>();
        for (ClassEntity clazz : eligibleClasses) {
            List<PersonalAssetSectionTarget> sections = sectionsByClass
                    .getOrDefault(clazz.getId(), List.of()).stream()
                    .map(section -> new PersonalAssetSectionTarget(
                            section.getId(), section.getTitle(),
                            lessonsBySection.getOrDefault(section.getId(), List.of()).stream()
                                    .map(lesson -> new PersonalAssetLessonTarget(
                                            lesson.getId(), lesson.getTitle(), lesson.getStatus(),
                                            lesson.getSourceLessonTemplateId() != null))
                                    .toList()))
                    .toList();
            classes.add(new PersonalAssetClassTarget(
                    clazz.getId(), clazz.getName(), clazz.getStatus(), sections));
        }
        return new PersonalAssetClassTargets(asset.getId(), List.copyOf(classes));
    }

    private List<Section> loadSections(List<Long> classIds) {
        List<Section> sections = new ArrayList<>();
        forEachBatch(classIds, batch -> sections.addAll(sectionRepository
                .findByClassIdInOrderByClassIdAscDisplayOrderAsc(batch)));
        return sections;
    }

    private List<Lesson> loadLessons(List<Long> sectionIds) {
        List<Lesson> lessons = new ArrayList<>();
        forEachBatch(sectionIds, batch -> lessons.addAll(lessonRepository
                .findBySectionIdInOrderBySectionIdAscDisplayOrderAsc(batch)));
        return lessons;
    }

    private static Map<Long, List<Section>> groupSectionsByClass(List<Section> sections) {
        Map<Long, List<Section>> grouped = new LinkedHashMap<>();
        for (Section section : sections) {
            grouped.computeIfAbsent(section.getClassId(), ignored -> new ArrayList<>())
                    .add(section);
        }
        return grouped;
    }

    private static Map<Long, List<Lesson>> groupLessonsBySection(List<Lesson> lessons) {
        Map<Long, List<Lesson>> grouped = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            grouped.computeIfAbsent(lesson.getSectionId(), ignored -> new ArrayList<>())
                    .add(lesson);
        }
        return grouped;
    }

    private static void forEachBatch(List<Long> ids,
                                     java.util.function.Consumer<List<Long>> consumer) {
        for (int start = 0; start < ids.size(); start += TARGET_QUERY_BATCH_SIZE) {
            int end = Math.min(start + TARGET_QUERY_BATCH_SIZE, ids.size());
            consumer.accept(ids.subList(start, end));
        }
    }
}
