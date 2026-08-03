package com.ksh.features.library.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Lesson;
import com.ksh.entities.LessonTemplate;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.lessons.repository.LessonRepository;
import com.ksh.features.lessons.repository.SectionRepository;
import com.ksh.features.library.dto.LibraryDtos.LessonTemplateRow;
import com.ksh.features.library.dto.LessonTemplateForm;
import com.ksh.features.library.repository.LessonTemplateRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration contracts for the subject Library hierarchy and distribution. */
@SpringBootTest
@Transactional
class LessonTemplateServiceTest {

    @Autowired private LessonTemplateService templateService;
    @Autowired private LessonTemplateRepository templateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private LessonRepository lessonRepository;

    private User lecturer;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        assertThat(lecturer.getDepartmentId()).as("seeded lecturer subject").isNotNull();
    }

    @Test
    void saveForm_persists_subject_chapter_lesson_hierarchy() {
        LessonTemplateRow row = templateService.saveForm(
                lecturer.getId(), Role.LECTURER, richtextForm("Chương 2", "Bài kính ngữ"));

        LessonTemplate saved = templateRepository.findById(row.id()).orElseThrow();
        assertThat(saved.getSubjectId()).isEqualTo(lecturer.getDepartmentId());
        assertThat(saved.getChapterTitle()).isEqualTo("Chương 2");
        assertThat(saved.getTitle()).isEqualTo("Bài kính ngữ");
        assertThat(saved.getContentType()).isEqualTo(Lesson.CONTENT_TYPE_RICHTEXT);
        assertThat(row.subjectCode()).isNotBlank();
    }

    @Test
    void distribute_creates_published_snapshot_in_each_same_subject_class() {
        LessonTemplateRow template = templateService.saveForm(
                lecturer.getId(), Role.LECTURER, richtextForm("Chương 1", "Bài phân phối"));
        ClassEntity first = activeClass("Library A");
        ClassEntity second = activeClass("Library B");

        var results = templateService.distribute(template.id(),
                List.of(first.getId(), second.getId()), lecturer.getId(), Role.LECTURER);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(result -> {
            Lesson lesson = lessonRepository.findById(result.lessonId()).orElseThrow();
            assertThat(lesson.getStatus()).isEqualTo(Lesson.STATUS_PUBLISHED);
        });
        assertThat(sectionRepository.findByClassIdOrderByDisplayOrderAsc(first.getId()))
                .extracting(section -> section.getTitle())
                .containsExactly("Chương 1");
    }

    @Test
    void distribute_rejects_duplicate_lesson_in_same_class_chapter() {
        LessonTemplateRow template = templateService.saveForm(
                lecturer.getId(), Role.LECTURER, richtextForm("Chương 1", "Bài duy nhất"));
        ClassEntity clazz = activeClass("Library duplicate");

        templateService.distribute(template.id(), List.of(clazz.getId()),
                lecturer.getId(), Role.LECTURER);

        assertThatThrownBy(() -> templateService.distribute(template.id(), List.of(clazz.getId()),
                lecturer.getId(), Role.LECTURER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã có bài học cùng tên");
    }

    private LessonTemplateForm richtextForm(String chapter, String title) {
        LessonTemplateForm form = new LessonTemplateForm();
        form.setChapterTitle(chapter);
        form.setTitle(title);
        form.setContentType(Lesson.CONTENT_TYPE_RICHTEXT);
        form.setContentRichtext("<p>Nội dung</p>");
        return form;
    }

    private ClassEntity activeClass(String name) {
        ClassEntity clazz = new ClassEntity(name, lecturer.getId(), lecturer.getId(),
                null, null, null, 100);
        clazz.setCode("L" + UUID.randomUUID().toString().substring(0, 7).toUpperCase());
        clazz.setDepartmentId(lecturer.getDepartmentId());
        clazz.approve(lecturer.getId(), LocalDateTime.now());
        return classRepository.saveAndFlush(clazz);
    }
}
