package com.ksh.features.library.service;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class CurriculumReplacementContractTest {
    @Test void preview_reads_without_write_lock_and_material_route_exists() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/ksh/features/library/service/LessonTemplateService.java"));
        String preview = service.substring(service.indexOf("public LibraryAsset authorizedResource"), service.indexOf("private static String resourceKind"));
        assertThat(preview).contains("subjectResolver.require", "existingAssetIds(template).contains(assetId)", "assetRepository.findById(assetId)");
        assertThat(preview).doesNotContain("persistedTemplateAsset", "findByIdForUpdate");
        String controller = Files.readString(Path.of("src/main/java/com/ksh/features/student/controller/FileViewerController.java"));
        assertThat(controller).contains("@GetMapping(\"/file-viewer/material\")", "materialsService.download(classId, materialId, user.getId(), user.getRole())");
    }
    @Test void replacement_retires_tree_without_deleting_files_or_progress() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/ksh/features/library/service/LessonTemplateService.java"));
        String method = service.substring(service.indexOf("public List<LessonCloneResult> distributeSubject"), service.indexOf("/** Soft-deletes an owned template"));
        assertThat(method).contains("getEditableForUpdate", "retired.forEach(Lesson::markDeleted)", "section.markDeleted()", "lessonRepository.flush()", "distribute(template.getId(), targets");
        assertThat(method).doesNotContain("deleteAllByLesson", "storage.delete", "attachmentRepository.delete");
        String ui = Files.readString(Path.of("src/main/resources/templates/library/index.html"));
        assertThat(ui).doesNotContain("th:disabled=\"${clazz.alreadyDistributed()}", " · ID ");
        assertThat(ui).contains("resource.uploaderName()", "resource.previewUrl()");
    }
}
