package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.features.practice.assessment.AssessmentContractCodec;
import com.ksh.features.practice.assessment.PracticeContentRules;
import com.ksh.features.practice.assessment.QuestionTypeResolver;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateException;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticePdfAuthoringOutputValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final QuestionTypeResolver typeResolver = new QuestionTypeResolver();
    private final PracticePdfAuthoringOutputValidator validator =
            new PracticePdfAuthoringOutputValidator(
                    mapper,
                    new AssessmentContractCodec(mapper, typeResolver),
                    typeResolver,
                    new PracticeContentRules());

    @Test
    void acceptsCanonicalReadingMultipleAnswerAndMultiBlankContracts() {
        PracticePdfAuthoringRequest request = request("READING", SourceOperation.EXTRACT);
        ObjectNode root = root(request);
        ArrayNode questions = (ArrayNode) root.at("/groups/0/questions");
        questions.removeAll();
        questions.add(multipleAnswerQuestion());
        questions.add(fillBlankQuestion());

        PracticePdfAuthoringOutputValidator.ValidatedOutput validated =
                validator.validate(root, request);

        assertThat(validated.root().at("/groups/0/questions").size()).isEqualTo(2);
        assertThat(validated.root().at(
                "/groups/0/questions/1/answerSpec/blanks/0/acceptedValues").size())
                .isEqualTo(2);
    }

    @Test
    void acceptsCanonicalListeningTranscriptWithoutProviderAudioReference() {
        PracticePdfAuthoringRequest request = request(
                "LISTENING", SourceOperation.GENERATE);
        ObjectNode root = root(request);
        ObjectNode stimulus = (ObjectNode) root.at("/groups/0/stimulus");
        stimulus.put("type", "LISTENING_AUDIO");
        stimulus.put("transcriptText", "오늘은 월요일입니다.");
        stimulus.putArray("sourceRefs").add(sourceRef());

        ObjectNode validated = validator.validate(root, request).root();

        assertThat(validated.at("/groups/0/stimulus/type").asText())
                .isEqualTo("LISTENING_AUDIO");
        assertThat(validated.at(
                "/groups/0/questions/0/questionContent/audioReference").isMissingNode())
                .isTrue();
    }

    @Test
    void acceptsWritingQ51TypedAnswersAndQ54CanonicalEssay() {
        PracticePdfAuthoringRequest request = request("WRITING", SourceOperation.GENERATE);
        ObjectNode root = root(request);
        ArrayNode questions = (ArrayNode) root.at("/groups/0/questions");
        questions.removeAll();
        questions.add(writingQ51());
        questions.add(writingQ54());

        assertThat(validator.validate(root, request).root()
                .at("/groups/0/questions/0/answerSpec/writingBlankAuthority/blanks")
                .size()).isEqualTo(2);
    }

    @Test
    void acceptsOnlyCanonicalTextOnlySpeakingDelivery() {
        PracticePdfAuthoringRequest request = request("SPEAKING", SourceOperation.EXTRACT);
        ObjectNode root = root(request);
        ((ArrayNode) root.at("/groups/0/questions"))
                .removeAll().add(speakingQuestion());

        validator.validate(root, request);

        ((ObjectNode) root.at(
                "/groups/0/questions/0/questionContent/speakingDelivery"))
                .put("deliveryMode", "text_and_audio");
        assertSchemaFailure(root, request, "PDF_AUTHORING_SCHEMA_INVALID");
    }

    @Test
    void rejectsUnknownFieldsAtEveryDepth() {
        PracticePdfAuthoringRequest request = request("READING", SourceOperation.EXTRACT);
        ObjectNode root = root(request);
        ((ObjectNode) root.at("/groups/0/questions/0/questionContent/options/0"))
                .put("providerNote", "not in the vocabulary");

        assertSchemaFailure(root, request, "PDF_AUTHORING_SCHEMA_INVALID");
    }

    @Test
    void acceptsNullProviderPointsAndConfidenceBecauseTheyAreCodeOwnedOrAdvisory() {
        PracticePdfAuthoringRequest request = request("READING", SourceOperation.EXTRACT);
        ObjectNode output = root(request);
        ObjectNode question = (ObjectNode) output.at("/groups/0/questions/0");
        question.putNull("points");
        question.putNull("confidence");

        ObjectNode validated = validator.validate(output, request).root();

        assertThat(validated.at("/groups/0/questions/0/points").isMissingNode()).isTrue();
        assertThat(validated.at("/groups/0/questions/0/confidence").isMissingNode()).isTrue();
    }

    @Test
    void languageTagIsAcceptedOnlyForQuestionContentV3() {
        PracticePdfAuthoringRequest request = request("READING", SourceOperation.EXTRACT);
        ObjectNode v1 = root(request);
        ObjectNode content = (ObjectNode) v1.at("/groups/0/questions/0/questionContent");
        content.put("schemaVersion", "question-content-v1");
        content.remove("languageTag");
        validator.validate(v1, request);

        content.putNull("languageTag");
        assertSchemaFailure(v1, request, "PDF_AUTHORING_SCHEMA_INVALID");
    }

    @Test
    void rejectsEvaluationSubmissionAndResultFieldsRecursively() {
        PracticePdfAuthoringRequest request = request("READING", SourceOperation.EXTRACT);
        for (String forbidden : List.of(
                "scoreSummary", "rubricScores", "taskCoverage",
                "diagnosticStates", "evidenceLedger", "findings",
                "upgradedAnswer", "submission", "result")) {
            ObjectNode root = root(request);
            ((ObjectNode) root.at("/groups/0/questions/0"))
                    .putObject(forbidden).put("private", true);
            assertSchemaFailure(root, request, "PDF_AUTHORING_SCHEMA_INVALID");
        }
    }

    @Test
    void rejectsUnknownOrOutOfBoundsSourceEvidence() {
        PracticePdfAuthoringRequest request = request("READING", SourceOperation.EXTRACT);
        ObjectNode unknown = root(request);
        ((ObjectNode) unknown.at("/groups/0/questions/0/sourceRefs/0"))
                .put("sourceId", "not-requested");
        assertSchemaFailure(unknown, request, "PDF_SOURCE_REFERENCE_UNKNOWN");

        ObjectNode outOfBounds = root(request);
        ((ObjectNode) outOfBounds.at("/groups/0/questions/0/sourceRefs/0"))
                .put("end", SOURCE.length() + 1);
        assertSchemaFailure(outOfBounds, request, "PDF_AUTHORING_SCHEMA_INVALID");
    }

    @Test
    void rejectsWritingAcceptedAnswerEvidenceOutsideRequest() {
        PracticePdfAuthoringRequest request = request("WRITING", SourceOperation.EXTRACT);
        ObjectNode root = root(request);
        ((ArrayNode) root.at("/groups/0/questions"))
                .removeAll().add(writingQ51());
        ((ArrayNode) root.at(
                "/groups/0/questions/0/answerSpec/writingBlankAuthority/blanks/0/acceptedAnswers/0/evidenceIds"))
                .removeAll().add("provider-invented-evidence");

        assertSchemaFailure(root, request, "PDF_SOURCE_REFERENCE_UNKNOWN");
    }

    @Test
    void sourceDigestAndOperationAreExactRequestSnapshot() {
        PracticePdfAuthoringRequest request = request("READING", SourceOperation.GENERATE);
        ObjectNode wrongOperation = root(request);
        wrongOperation.put("operation", "EXTRACT");
        assertSchemaFailure(wrongOperation, request, "PDF_AUTHORING_SCHEMA_INVALID");

        ObjectNode wrongDigest = root(request);
        wrongDigest.put("sourceDigest", "sha256:" + "f".repeat(64));
        assertSchemaFailure(wrongDigest, request, "PDF_AUTHORING_SCHEMA_INVALID");
    }

    @Test
    void requiresTheExactLecturerRequestedSourceQuestionRange() {
        PracticePdfAuthoringRequest request = request(
                "READING", SourceOperation.EXTRACT, "Trích câu 1-4 và 48-50.");
        ObjectNode output = root(request);
        ArrayNode questions = (ArrayNode) output.at("/groups/0/questions");
        questions.removeAll();
        for (int number : List.of(1, 2, 3, 4, 48, 49, 50)) {
            ObjectNode question = singleChoiceQuestion();
            question.put("sourceQuestionId", "source-question-" + number);
            question.put("sourceQuestionNumber", number);
            questions.add(question);
        }

        validator.validate(output, request);

        questions.remove(6);
        assertSchemaFailure(output, request, "PDF_AUTHORING_SCOPE_MISMATCH");
    }

    @Test
    void requiresEachLecturerRequestedQuestionRangeToStayInItsOwnGroup() {
        PracticePdfAuthoringRequest request = request(
                "READING", SourceOperation.EXTRACT,
                "Gen 2 nhóm gồm nhóm 1 câu 1-2 và nhóm 2 câu 48-50.");
        ObjectNode output = root(request);
        ArrayNode firstQuestions = (ArrayNode) output.at("/groups/0/questions");
        firstQuestions.removeAll();
        firstQuestions.add(numberedQuestion(1)).add(numberedQuestion(2));
        ObjectNode second = addGroup(output, "group-2");
        second.putArray("questions")
                .add(numberedQuestion(48))
                .add(numberedQuestion(49))
                .add(numberedQuestion(50));

        validator.validate(output, request);

        firstQuestions.removeAll()
                .add(numberedQuestion(1))
                .add(numberedQuestion(48));
        second.withArray("questions").removeAll()
                .add(numberedQuestion(2))
                .add(numberedQuestion(49))
                .add(numberedQuestion(50));
        assertSchemaFailure(output, request, "PDF_AUTHORING_GROUP_SCOPE_MISMATCH");
    }

    @Test
    void readingProviderSchemaDoesNotOfferWritingOrSpeakingQuestionTypes() throws Exception {
        String readingSchema = mapper.writeValueAsString(
                PracticePdfAuthoringJsonContract.schema("READING"));

        assertThat(readingSchema)
                .contains("SINGLE_CHOICE", "MATCHING")
                .doesNotContain("\"ESSAY\"", "\"SPEAKING\"",
                        "question-content-v2");

        String speakingSchema = mapper.writeValueAsString(
                PracticePdfAuthoringJsonContract.schema("SPEAKING"));
        assertThat(speakingSchema)
                .contains("question-content-v2", "question-content-v3")
                .doesNotContain("question-content-v1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readingProviderSchemaLocksQuestionAndAnswerSpecToTheSameType() {
        Map<String, Object> schema = PracticePdfAuthoringJsonContract.schema("READING");
        Map<String, Object> rootProperties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> groups = (Map<String, Object>) rootProperties.get("groups");
        Map<String, Object> group = (Map<String, Object>) groups.get("items");
        Map<String, Object> groupProperties = (Map<String, Object>) group.get("properties");
        Map<String, Object> questions = (Map<String, Object>) groupProperties.get("questions");
        Map<String, Object> questionItems = (Map<String, Object>) questions.get("items");
        List<Map<String, Object>> branches = (List<Map<String, Object>>) questionItems.get("anyOf");

        assertThat(branches).hasSize(5);
        for (Map<String, Object> branch : branches) {
            Map<String, Object> properties = (Map<String, Object>) branch.get("properties");
            Map<String, Object> questionType =
                    (Map<String, Object>) properties.get("questionType");
            Map<String, Object> answerSpec =
                    (Map<String, Object>) properties.get("answerSpec");
            Map<String, Object> answerProperties =
                    (Map<String, Object>) answerSpec.get("properties");
            Map<String, Object> answerType =
                    (Map<String, Object>) answerProperties.get("questionType");

            assertThat(answerType.get("enum")).isEqualTo(questionType.get("enum"));
        }
    }

    private void assertSchemaFailure(
            ObjectNode output,
            PracticePdfAuthoringRequest request,
            String code) {
        assertThatThrownBy(() -> validator.validate(output, request))
                .isInstanceOf(PracticeAuthoringCandidateException.class)
                .extracting(failure -> ((PracticeAuthoringCandidateException) failure).code())
                .isEqualTo(code);
    }

    private ObjectNode root(PracticePdfAuthoringRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", PracticePdfAuthoringJsonContract.SCHEMA_VERSION);
        root.put("operation", request.operation().name());
        root.put("sourceDigest", request.sourceDigest());
        ObjectNode group = addGroup(root, "group-1");
        group.putArray("questions").add(singleChoiceQuestion());
        root.putArray("warnings");
        return root;
    }

    private ObjectNode addGroup(ObjectNode root, String groupId) {
        ObjectNode group = root.withArray("groups").addObject();
        group.put("sourceGroupId", groupId);
        group.put("label", "Nhóm câu hỏi");
        group.put("instruction", "Làm bài.");
        ObjectNode stimulus = group.putObject("stimulus");
        stimulus.put("type", "NONE");
        stimulus.put("passageText", "");
        stimulus.put("transcriptText", "");
        stimulus.putArray("sourceRefs");
        group.putArray("sourceRefs").add(sourceRef());
        return group;
    }

    private ObjectNode numberedQuestion(int sourceQuestionNumber) {
        ObjectNode question = singleChoiceQuestion();
        question.put("sourceQuestionId", "source-question-" + sourceQuestionNumber);
        question.put("sourceQuestionNumber", sourceQuestionNumber);
        return question;
    }

    private ObjectNode singleChoiceQuestion() {
        ObjectNode question = question("reading-q-1", "SINGLE_CHOICE");
        ObjectNode content = question.putObject("questionContent");
        content.put("schemaVersion", "question-content-v3");
        ArrayNode options = content.putArray("options");
        option(options, "opt-A", "월요일");
        option(options, "opt-B", "화요일");
        content.putArray("blanks");
        content.put("languageTag", "ko");
        ObjectNode answer = answer(question, "SINGLE_CHOICE", "ALL_OR_NOTHING");
        answer.putArray("correctOptionIds").add("opt-A");
        return question;
    }

    private ObjectNode multipleAnswerQuestion() {
        ObjectNode question = question("reading-q-multi", "MULTIPLE_ANSWER");
        ObjectNode content = question.putObject("questionContent");
        content.put("schemaVersion", "question-content-v3");
        ArrayNode options = content.putArray("options");
        option(options, "opt-A", "가");
        option(options, "opt-B", "나");
        option(options, "opt-C", "다");
        content.putArray("blanks");
        content.put("languageTag", "ko");
        ObjectNode answer = answer(question, "MULTIPLE_ANSWER", "ALL_OR_NOTHING");
        answer.putArray("correctOptionIds").add("opt-A").add("opt-C");
        return question;
    }

    private ObjectNode fillBlankQuestion() {
        ObjectNode question = question("reading-q-blanks", "FILL_BLANK");
        ObjectNode content = question.putObject("questionContent");
        content.put("schemaVersion", "question-content-v3");
        content.putArray("options");
        ArrayNode blanks = content.putArray("blanks");
        blank(blanks, "blank-1", "첫째");
        blank(blanks, "blank-2", "둘째");
        content.put("languageTag", "ko");
        ObjectNode answer = answer(question, "FILL_BLANK", "NORMALIZED_EXACT");
        ArrayNode answerBlanks = answer.putArray("blanks");
        answerBlank(answerBlanks, "blank-1", "하나", "한 개");
        answerBlank(answerBlanks, "blank-2", "둘", "두 개");
        return question;
    }

    private ObjectNode writingQ51() {
        ObjectNode question = question("writing-q51", "ESSAY");
        question.put("essayTaskType", "Q51");
        ObjectNode content = question.putObject("questionContent");
        content.put("schemaVersion", "question-content-v3");
        content.putArray("options");
        content.putArray("blanks");
        content.put("languageTag", "ko");
        ObjectNode response = content.putObject("writingResponse");
        response.put("responseSchemaVersion", "writing-blanks.v1");
        response.put("responseMode", "STRUCTURED_BLANKS");
        response.put("taskType", "Q51");
        ArrayNode responseBlanks = response.putArray("blanks");
        writingResponseBlank(responseBlanks, "q51-b1", 1, "인사");
        writingResponseBlank(responseBlanks, "q51-b2", 2, "요청");
        ObjectNode answer = answer(question, "ESSAY", "PROFILE_BASED");
        ObjectNode authority = answer.putObject("writingBlankAuthority");
        authority.put("contractVersion", "writing-blank-authority.v1");
        authority.put("taskType", "Q51");
        authority.put("normalization", "NFC");
        authority.put("whitespacePolicy", "TRIM_COLLAPSE");
        ArrayNode authorityBlanks = authority.putArray("blanks");
        writingAuthorityBlank(authorityBlanks, "q51-b1", 1, "안녕하세요");
        writingAuthorityBlank(authorityBlanks, "q51-b2", 2, "연락해 주세요");
        return question;
    }

    private ObjectNode writingQ54() {
        ObjectNode question = question("writing-q54", "ESSAY");
        question.put("essayTaskType", "Q54");
        ObjectNode content = question.putObject("questionContent");
        content.put("schemaVersion", "question-content-v3");
        content.putArray("options");
        content.putArray("blanks");
        content.put("languageTag", "ko");
        answer(question, "ESSAY", "PROFILE_BASED");
        return question;
    }

    private ObjectNode speakingQuestion() {
        ObjectNode question = question("speaking-q-1", "SPEAKING");
        ObjectNode content = question.putObject("questionContent");
        content.put("schemaVersion", "question-content-v3");
        content.putArray("options");
        content.putArray("blanks");
        content.put("languageTag", "ko");
        ObjectNode delivery = content.putObject("speakingDelivery");
        delivery.put("inputType", "manual_text");
        delivery.put("deliveryMode", "text_only");
        delivery.putNull("promptAudioReference");
        delivery.put("audioOrigin", "none");
        delivery.putNull("promptPlayLimit");
        delivery.put("preparationSeconds", 30);
        delivery.put("responseSeconds", 60);
        answer(question, "SPEAKING", "PROFILE_BASED");
        return question;
    }

    private ObjectNode question(String id, String type) {
        ObjectNode question = mapper.createObjectNode();
        question.put("sourceQuestionId", id);
        question.put("questionType", type);
        question.put("prompt", "Câu hỏi tiếng Hàn");
        question.put("points", 999);
        question.set("sourceRefs", mapper.createArrayNode().add(sourceRef()));
        question.put("confidence", 0.91);
        return question;
    }

    private ObjectNode answer(ObjectNode question, String type, String policy) {
        ObjectNode answer = question.putObject("answerSpec");
        answer.put("schemaVersion", "answer-spec-v1");
        answer.put("questionType", type);
        answer.putArray("correctOptionIds");
        answer.putNull("correctValue");
        answer.putArray("blanks");
        answer.put("scoringPolicyCode", policy);
        return answer;
    }

    private ObjectNode sourceRef() {
        ObjectNode ref = mapper.createObjectNode();
        ref.put("kind", "TEXT_SPAN");
        ref.put("sourceId", "source-1");
        ref.put("start", 0);
        ref.put("end", SOURCE.length());
        return ref;
    }

    private static void option(ArrayNode options, String id, String text) {
        ObjectNode option = options.addObject();
        option.put("id", id);
        option.put("text", text);
    }

    private static void blank(ArrayNode blanks, String id, String prompt) {
        ObjectNode blank = blanks.addObject();
        blank.put("id", id);
        blank.put("prompt", prompt);
    }

    private static void answerBlank(
            ArrayNode blanks, String id, String first, String second) {
        ObjectNode blank = blanks.addObject();
        blank.put("blankId", id);
        blank.putArray("acceptedValues").add(first).add(second);
    }

    private static void writingResponseBlank(
            ArrayNode blanks, String id, int ordinal, String context) {
        ObjectNode blank = blanks.addObject();
        blank.put("blankId", id);
        blank.put("ordinal", ordinal);
        blank.put("context", context);
    }

    private static void writingAuthorityBlank(
            ArrayNode blanks, String id, int ordinal, String acceptedText) {
        ObjectNode blank = blanks.addObject();
        blank.put("blankId", id);
        blank.put("ordinal", ordinal);
        ObjectNode accepted = blank.putArray("acceptedAnswers").addObject();
        accepted.put("text", acceptedText);
        accepted.put("equivalence", "EXACT");
        accepted.putNull("reason");
        accepted.putArray("evidenceIds").add("source-1");
    }

    private static PracticePdfAuthoringRequest request(
            String skill,
            SourceOperation operation) {
        return request(skill, operation, "");
    }

    private static PracticePdfAuthoringRequest request(
            String skill,
            SourceOperation operation,
            String lecturerRequest) {
        return new PracticePdfAuthoringRequest(
                PracticePdfAuthoringRequest.SourceType.TEXT,
                operation,
                "source.txt",
                "sha256:" + "6".repeat(64),
                new TargetRoute(91L, 1, skill, skill.substring(0, 1) + "1"),
                lecturerRequest,
                List.of(new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "source-1", null,
                        SOURCE.length(), SOURCE)),
                Map.of("trust", "UNTRUSTED_SOURCE_CONTENT"),
                List.of());
    }

    private static final String SOURCE =
            "박물관은 월요일에 쉽니다. 두 표현을 완성하세요.";
}
