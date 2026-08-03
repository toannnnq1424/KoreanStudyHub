package com.ksh.features.lessons.support;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.entities.Section;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Shared lesson resolution + published-visibility gate for student-facing
 * lesson surfaces (detail and progress).
 *
 * <p>Every failure collapses to a single {@link EntityNotFoundException} with
 * the canonical message so lesson existence is never leaked. This helper owns
 * ONLY the resolution + live-class + PUBLISHED gates; each caller keeps its own
 * access policy (ACTIVE enrollment, or enrolled-or-owning-lecturer) inline.
 */
@Component
public class LessonAccessResolver {

    private static final String NF_MSG = "Class not found or not accessible";

    private final ClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final LessonRepository lessonRepository;

    public LessonAccessResolver(ClassRepository classRepository,
                                SectionRepository sectionRepository,
                                LessonRepository lessonRepository) {
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.lessonRepository = lessonRepository;
    }

    /** The resolved live class, section and PUBLISHED lesson for a request. */
    public record ResolvedLesson(ClassEntity clazz, Section section, Lesson lesson) { }

    /**
     * Resolves for URL-scoped surfaces (class id + lesson id): verifies the
     * class is live, the lesson exists + is PUBLISHED + not soft-deleted, and
     * its section belongs to the given class.
     *
     * @throws EntityNotFoundException on any failure, always with the canonical
     *         message so existence is never leaked
     */
    public ResolvedLesson resolveInClass(Long classId, Long lessonId) {
        ClassEntity clazz = classRepository.findById(classId)
                .orElseThrow(() -> new EntityNotFoundException(NF_MSG));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new EntityNotFoundException(NF_MSG));
        Section section = sectionRepository.findById(lesson.getSectionId())
                .orElseThrow(() -> new EntityNotFoundException(NF_MSG));
        // Cross-class guard: deny if the lesson lives in another class.
        if (!classId.equals(section.getClassId())) {
            throw new EntityNotFoundException(NF_MSG);
        }
        requirePublished(lesson);
        return new ResolvedLesson(clazz, section, lesson);
    }

    /** DRAFT lessons are lecturer-private — never visible on student surfaces. */
    private void requirePublished(Lesson lesson) {
        if (!Lesson.STATUS_PUBLISHED.equals(lesson.getStatus())) {
            throw new EntityNotFoundException(NF_MSG);
        }
    }
}
