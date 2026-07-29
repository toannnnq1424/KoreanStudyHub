package com.ksh.features.questionbank.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderQuestionBankCatalogFrontendContractTest {

    @Test
    void emptyDepartmentCatalogExplainsInheritedAdminCategories() throws IOException {
        String template = Files.readString(
                Path.of("src/main/resources/templates/questionbank/manage.html"));

        assertThat(template)
                .contains("Chưa có danh mục riêng của bộ môn")
                .contains("do ADMIN quản lý")
                .contains("KSH sẽ tự tạo liên kết danh mục tương ứng");
    }
}
