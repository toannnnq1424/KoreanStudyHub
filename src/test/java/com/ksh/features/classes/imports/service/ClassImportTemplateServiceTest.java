package com.ksh.features.classes.imports.service;

import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.imports.parser.ExcelTemplateBuilder;
import com.ksh.features.classes.service.ClassesService;
import com.ksh.security.Role;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassImportTemplateServiceTest {
    @Test void exports_existing_eligible_identity_and_checks_class_ownership() throws Exception {
        var classes = mock(ClassesService.class);
        var users = mock(UserRepository.class);
        var student = mock(User.class);
        when(student.getEmail()).thenReturn("real@school.edu");
        when(student.getFullName()).thenReturn("Sinh viên thật");
        when(users.findImportCandidates(eq(43L), any())).thenReturn(List.of(student));
        var service = new ClassImportTemplateService(classes, users, new ExcelTemplateBuilder());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.build(43L, 7L, Role.LECTURER)))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("real@school.edu");
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
        }
        verify(classes).getOwnerManaged(43L, 7L, Role.LECTURER);
    }

    @Test void empty_pool_never_returns_fictional_sample_users() {
        var classes = mock(ClassesService.class);
        var users = mock(UserRepository.class);
        var service = new ClassImportTemplateService(classes, users, new ExcelTemplateBuilder());
        assertThatThrownBy(() -> service.build(43L, 7L, Role.LECTURER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Không có sinh viên");
    }
}
