package com.ksh.features.lessons.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SiblingAppendLockContractTest {

    @Test
    void sectionCreateLocksClassBeforeComputingOrder() throws Exception {
        assertBefore(readFeature("lessons/service/SectionsService.java"),
                "classesService.getEditableForUpdate(classId, userId, role)",
                "sectionRepository.findMaxDisplayOrder(clazz.getId())");
    }

    @Test
    void lessonCreateLocksSectionBeforeComputingOrder() throws Exception {
        assertBefore(readFeature("lessons/service/LessonsService.java"),
                "reorderService.lockSectionForUpdate(sectionId, classId)",
                "lessonRepository.findMaxDisplayOrder(sectionId)");
    }

    @Test
    void libraryDistributionLocksTargetSectionBeforeMaterializingSnapshot() throws Exception {
        String source = readFeature("library/service/LessonTemplateService.java");
        int lock = source.indexOf(
                "reorderService.lockSectionForUpdate(section.getId(), classId)");
        int snapshotCall = source.indexOf("snapshotTemplateToSection(", lock);
        int snapshotMethod = source.indexOf(
                "private LessonCloneResult snapshotTemplateToSection", snapshotCall);
        int materialize = source.indexOf("materializeDraft(sectionId", snapshotMethod);
        assertTrue(lock >= 0 && snapshotCall > lock
                && snapshotMethod > snapshotCall && materialize > snapshotMethod);
        assertTrue(!source.contains("cloneLessonToSection"));
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second, firstIndex);
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex);
    }

    private static String readFeature(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/ksh/features").resolve(relative),
                StandardCharsets.UTF_8);
    }
}
