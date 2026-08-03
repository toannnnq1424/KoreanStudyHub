package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateState;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CandidateView;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.CreateCommand;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.ValidationIssue;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticePdfAuthoringCandidateAssemblerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsValidatedOutputOnlyIntoAim2CandidateCommand() throws Exception {
        PracticePdfAuthoringOutputValidator validator =
                mock(PracticePdfAuthoringOutputValidator.class);
        PracticeAuthoringCandidateService candidateService =
                mock(PracticeAuthoringCandidateService.class);
        ObjectNode output = (ObjectNode) mapper.readTree(outputJson());
        PracticePdfAuthoringRequest request = request();
        when(validator.validate(any(), any())).thenReturn(
                new PracticePdfAuthoringOutputValidator.ValidatedOutput(output));
        CandidateView expected = new CandidateView(
                "candidate-1", CandidateState.REVIEWING, 3L,
                "sha256:" + "b".repeat(64), mapper.createObjectNode(), List.of());
        when(candidateService.createOrReuse(any(), any())).thenReturn(expected);
        PracticePdfAuthoringCandidateAssembler assembler =
                new PracticePdfAuthoringCandidateAssembler(
                        mapper, validator, candidateService,
                        new AssessmentAuthoringCatalogService(
                                new PracticeContentRules()));
        ObjectNode execution = mapper.createObjectNode();
        execution.put("purpose", "PRACTICE_PDF_AUTHORING");
        execution.put("bindingRevision", 0L);
        PracticePdfAiOrchestrator.GenerationResult generation =
                new PracticePdfAiOrchestrator.GenerationResult(
                        output, execution, "authoring-v1-b0", "request-1", "provider-1");

        CandidateView actual = assembler.assemble(request, generation, 101L);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<CreateCommand> command =
                ArgumentCaptor.forClass(CreateCommand.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ValidationIssue>> issues =
                ArgumentCaptor.forClass(List.class);
        verify(candidateService).createOrReuse(command.capture(), issues.capture());
        assertThat(command.getValue().actorId()).isEqualTo(101L);
        assertThat(command.getValue().source().kind()).isEqualTo(SourceKind.PDF_AI);
        assertThat(command.getValue().source().contractVersion())
                .isEqualTo("practice-pdf-authoring-output-v1");
        assertThat(command.getValue().source().operation())
                .isEqualTo(SourceOperation.EXTRACT);
        assertThat(command.getValue().source().aiExecution().path("purpose").asText())
                .isEqualTo("PRACTICE_PDF_AUTHORING");
        assertThat(command.getValue().source().aiExecution()
                .path("bindingRevision").asLong()).isZero();
        assertThat(command.getValue().groups().at("/0/questions/0/reviewState").asText())
                .isEqualTo("REVIEW_REQUIRED");
        assertThat(command.getValue().groups().at("/0/questions/0/points").asDouble())
                .isEqualTo(1d);
        assertThat(command.getValue().groups().at(
                "/0/questions/0/questionContent/imageReference").asText())
                .isEqualTo("/practice/materials/700/content");
        assertThat(command.getValue().groups().at(
                "/0/stimulus/provenance/source").asText()).isEqualTo("PDF_AI");
        assertThat(command.getValue().groups().at(
                "/0/stimulus/provenance/approved").asBoolean()).isFalse();
        assertThat(issues.getValue()).extracting(ValidationIssue::code)
                .containsExactlyInAnyOrder(
                        "PDF_LOW_CONFIDENCE",
                        "PDF_PROVIDER_POINTS_NORMALIZED",
                        "PDF_PROVIDER_WARNING");
    }

    private PracticePdfAuthoringRequest request() {
        String source = "박물관은 월요일에 쉽니다.";
        return new PracticePdfAuthoringRequest(
                PracticePdfAuthoringRequest.SourceType.PDF,
                SourceOperation.EXTRACT,
                "source.pdf",
                "sha256:" + "6".repeat(64),
                new TargetRoute(91L, 1, "READING", "R1"),
                "",
                List.of(new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "source-1", null, source.length(), source)),
                Map.of(
                        "trust", "UNTRUSTED_SOURCE_CONTENT",
                        "assetReferences", Map.of(
                                "asset-ref-1", "/practice/materials/700/content")),
                List.of());
    }

    private String outputJson() {
        return """
                {
                  "schemaVersion":"practice-pdf-authoring-output-v1",
                  "operation":"EXTRACT",
                  "sourceDigest":"sha256:%s",
                  "groups":[{
                    "sourceGroupId":"group-1",
                    "label":"Đọc hiểu",
                    "instruction":"Đọc và chọn đáp án.",
                    "stimulus":{
                      "type":"NONE","passageText":"","transcriptText":"",
                      "sourceRefs":[]
                    },
                    "sourceRefs":[],
                    "questions":[{
                      "sourceQuestionId":"question-1",
                      "questionType":"SINGLE_CHOICE",
                      "prompt":"Ngày nghỉ là ngày nào?",
                      "points":999,
                      "questionContent":{
                        "schemaVersion":"question-content-v3",
                        "options":[
                          {"id":"opt-A","text":"월요일"},
                          {"id":"opt-B","text":"화요일"}
                        ],
                        "blanks":[],
                        "imageReference":"asset-ref-1",
                        "languageTag":"ko"
                      },
                      "answerSpec":{
                        "schemaVersion":"answer-spec-v1",
                        "questionType":"SINGLE_CHOICE",
                        "correctOptionIds":["opt-A"],
                        "correctValue":null,
                        "blanks":[],
                        "scoringPolicyCode":"ALL_OR_NOTHING"
                      },
                      "sourceRefs":[{
                        "kind":"TEXT_SPAN","sourceId":"source-1",
                        "start":0,"end":1
                      }],
                      "confidence":0.5
                    }]
                  }],
                  "warnings":[{
                    "code":"SOURCE_REVIEW_NOTE",
                    "messageVi":"Kiểm tra lại bản gốc.",
                    "sourceRefs":[]
                  }]
                }
                """.formatted("6".repeat(64));
    }
}
