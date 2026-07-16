package com.ksh.features.practice.repository;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional
class PracticeSetRepositoryTest {

    @Autowired
    private PracticeSetRepository setRepository;

    @Autowired
    private PracticeSectionRepository sectionRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void learnerCatalogQueryEnforcesVisibilitySearchSkillAndClassFilter() {
        User lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        ClassEntity enrolledClass = classRepository.saveAndFlush(new ClassEntity(
                "Lớp catalog", lecturer.getId(), lecturer.getId(), null,
                LocalDate.now(), LocalDate.now().plusMonths(1), 30));
        ClassEntity unrelatedClass = classRepository.saveAndFlush(new ClassEntity(
                "Lớp khác", lecturer.getId(), lecturer.getId(), null,
                LocalDate.now(), LocalDate.now().plusMonths(1), 30));
        String marker = "Catalog lazy " + System.nanoTime();

        PracticeSet global = setRepository.saveAndFlush(new PracticeSet(
                marker + " global", "search marker", PracticeSet.SKILL_READING,
                PracticeSet.SCOPE_GLOBAL, null, null, "{}",
                PracticeSet.STATUS_PUBLISHED, lecturer.getId()));
        PracticeSet classVisible = setRepository.saveAndFlush(new PracticeSet(
                marker + " class", "search marker", PracticeSet.SKILL_READING,
                PracticeSet.SCOPE_CLASS, enrolledClass.getId(), null, "{}",
                PracticeSet.STATUS_PUBLISHED, lecturer.getId()));
        setRepository.saveAndFlush(new PracticeSet(
                marker + " unrelated", "search marker", PracticeSet.SKILL_READING,
                PracticeSet.SCOPE_CLASS, unrelatedClass.getId(), null, "{}",
                PracticeSet.STATUS_PUBLISHED, lecturer.getId()));
        setRepository.saveAndFlush(new PracticeSet(
                marker + " archived", "search marker", PracticeSet.SKILL_READING,
                PracticeSet.SCOPE_GLOBAL, null, null, "{}",
                PracticeSet.STATUS_ARCHIVED, lecturer.getId()));

        sectionRepository.saveAndFlush(new PracticeSection(
                global.getId(), "Phần Nói", PracticeSet.SKILL_SPEAKING,
                "SPEAKING", null, 10, BigDecimal.TEN, 1));

        Page<PracticeSet> visible = setRepository.findLearnerVisiblePublished(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                PracticeSet.SCOPE_CLASS,
                lecturer.getId() + 1000,
                List.of(enrolledClass.getId()),
                0L,
                marker,
                "",
                PageRequest.of(0, 12));

        assertThat(visible.getContent())
                .extracting(PracticeSet::getId)
                .containsExactlyInAnyOrder(global.getId(), classVisible.getId());

        Page<PracticeSet> speaking = setRepository.findLearnerVisiblePublished(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                PracticeSet.SCOPE_CLASS,
                lecturer.getId() + 1000,
                List.of(enrolledClass.getId()),
                0L,
                marker,
                PracticeSet.SKILL_SPEAKING,
                PageRequest.of(0, 12));
        assertThat(speaking.getContent())
                .extracting(PracticeSet::getId)
                .containsExactly(global.getId());

        Page<PracticeSet> selectedClass = setRepository.findLearnerVisiblePublished(
                PracticeSet.STATUS_PUBLISHED,
                PracticeSet.SCOPE_GLOBAL,
                PracticeSet.SCOPE_CLASS,
                lecturer.getId() + 1000,
                List.of(enrolledClass.getId()),
                enrolledClass.getId(),
                marker,
                "",
                PageRequest.of(0, 12));
        assertThat(selectedClass.getContent())
                .extracting(PracticeSet::getId)
                .containsExactly(classVisible.getId());
    }
}
