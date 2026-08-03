package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateService;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PracticeAssessmentExcelServiceTest {

    @Test
    void advancedV2IsRecognizedAndDeterministicallyRetired() throws Exception {
        Fixture fixture = fixture();
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("01_THONG_TIN_SET");
            assertRetired(
                    fixture,
                    workbookFile(workbook, "advanced-v2.xlsx"),
                    "ADVANCED_EXCEL_V2_RETIRED");
        }
        verifyNoInteractions(fixture.candidates());
    }

    @Test
    void legacyV1IsRecognizedAndDeterministicallyRetired() throws Exception {
        Fixture fixture = fixture();
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Manifest");
            workbook.createSheet("Sections");
            workbook.createSheet("Groups");
            workbook.createSheet("Questions");
            workbook.createSheet("OptionsAnswers");
            assertRetired(
                    fixture,
                    workbookFile(workbook, "legacy-v1.xlsx"),
                    "LEGACY_EXCEL_V1_RETIRED");
        }
        verifyNoInteractions(fixture.candidates());
    }

    @Test
    void unknownWorkbookDoesNotFallBackToEitherRetiredParser()
            throws Exception {
        Fixture fixture = fixture();
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Questions");
            assertRetired(
                    fixture,
                    workbookFile(workbook, "unknown.xlsx"),
                    "WORKBOOK_SCHEMA_UNSUPPORTED");
        }
        verifyNoInteractions(fixture.candidates());
    }

    private static void assertRetired(
            Fixture fixture,
            MockMultipartFile file,
            String expectedCode) {
        assertThatThrownBy(() -> fixture.service().preview(file, context()))
                .isInstanceOfSatisfying(
                        PracticeAssessmentExcelException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(expectedCode));
    }

    private static Fixture fixture() {
        ObjectMapper objectMapper = new ObjectMapper();
        AssessmentAuthoringCatalogService catalog =
                new AssessmentAuthoringCatalogService(
                        new PracticeContentRules());
        QuestionTypeResolver resolver = new QuestionTypeResolver();
        AssessmentContractCodec codec =
                new AssessmentContractCodec(objectMapper, resolver);
        PracticeDraftContractService contract =
                new PracticeDraftContractService(
                        objectMapper, catalog, resolver, codec);
        PracticeAuthoringCandidateService candidates =
                mock(PracticeAuthoringCandidateService.class);
        PracticeAssessmentExcelService service =
                new PracticeAssessmentExcelService(
                        catalog,
                        contract,
                        mock(PracticeDraftRepository.class),
                        objectMapper,
                        null,
                        candidates);
        return new Fixture(service, candidates);
    }

    private static PracticeAssessmentExcelService.ExcelImportContext context() {
        return new PracticeAssessmentExcelService.ExcelImportContext(
                mock(PracticeDraft.class), 1, "R1", "READING");
    }

    private static MockMultipartFile workbookFile(
            Workbook workbook, String filename) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private record Fixture(
            PracticeAssessmentExcelService service,
            PracticeAuthoringCandidateService candidates) {
    }
}
