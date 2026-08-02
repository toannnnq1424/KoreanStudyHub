package com.ksh.features.practice.ai.readinglistening;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.features.practice.ai.transport.PracticeAiContractException;
import com.ksh.features.practice.ai.transport.TestPracticeStructuredGenerationPort;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.AssessmentSkill;
import com.ksh.features.practice.assessment.AssessmentStimulus;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.ExplanationContext;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadingListeningExplanationClientTest {

    @Test
    void structuredProviderErrorLogOmitsPayloadButKeepsSafeMetadata() {
        TestPracticeStructuredGenerationPort port =
                TestPracticeStructuredGenerationPort.throwing(
                        "openai-primary",
                        "safe-model",
                        new PracticeAiContractException(
                                "PROVIDER_HTTP_400",
                                false));
        ReadingListeningExplanationClient client =
                new ReadingListeningExplanationClient(
                        port,
                        new ObjectMapper());
        ExplanationContext context = context();

        String logs = captureLogs(ReadingListeningExplanationClient.class, () -> {
            assertThrows(ExplanationProviderException.class,
                    () -> client.generate(context, List.of()));
        });

        assertFalse(logs.contains("PRIVATE_PROVIDER_RESPONSE"));
        assertFalse(logs.contains("PRIVATE_PROMPT_TEXT"));
        assertFalse(logs.contains("PRIVATE_CACHE_JSON"));
        assertTrue(logs.contains("category=PROVIDER_HTTP_400"));
        assertTrue(logs.contains("model=safe-model"));
        assertTrue(logs.contains("skill=READING"));
    }

    @Test
    void matchingValidatorRequiresExactTargetCoverageAndOfficialCandidates() {
        ReadingListeningExplanationClient client =
                new ReadingListeningExplanationClient(
                        TestPracticeStructuredGenerationPort.unavailable(
                                "test", "safe-model"),
                        new ObjectMapper());
        ExplanationContext context = matchingContext();
        String valid = """
                {
                  "schemaVersion":"v4",
                  "strategyRegistryVersion":"rl-explanation-strategy-registry-v2",
                  "strategyCode":"MATCHING_MATRIX",
                  "strategyVersion":"v1",
                  "questionType":"MATCHING",
                  "explanation":{
                    "textEvidenceRefs":[{
                      "evidenceId":"e1","kind":"TEXT_SPAN",
                      "purpose":"ANSWER_RATIONALE","sourceRole":"PASSAGE",
                      "exactQuoteKo":"서울","startOffset":0,"endOffset":2
                    }],
                    "imageEvidenceRefs":[],
                    "relevantTranslations":[],
                    "strategyBlock":{"targetExplanations":[
                      {"claimId":"c1","targetId":"blank_1","candidateOptionId":"option_1","reasonVi":"Thủ đô là Seoul.","evidenceIds":["e1"]},
                      {"claimId":"c2","targetId":"blank_2","candidateOptionId":"option_2","reasonVi":"Đối chiếu đúng nhãn.","evidenceIds":["e1"]}
                    ]}
                  }
                }
                """;

        assertTrue(client.cleanAndValidateJson(valid, context, List.of()) != null);
        assertTrue(client.cleanAndValidateJson(
                valid.replace(
                        "\"targetId\":\"blank_2\",\"candidateOptionId\":\"option_2\"",
                        "\"targetId\":\"blank_2\",\"candidateOptionId\":\"option_1\""),
                context,
                List.of()) == null);
    }

    private static ExplanationContext context() {
        CanonicalQuestionType type = CanonicalQuestionType.SINGLE_CHOICE;
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("opt_1", "A"),
                        new QuestionContent.Option("opt_2", "B")),
                List.of());
        AnswerSpec answerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                type,
                List.of("opt_1"),
                null,
                List.of(),
                ScoringPolicyCode.ALL_OR_NOTHING);
        return new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                1L,
                10L,
                1,
                AssessmentSkill.READING,
                type,
                "PRIVATE_PROMPT_TEXT",
                "PRIVATE_GROUP_INSTRUCTION",
                content,
                answerSpec,
                new LearnerAnswer(
                        LearnerAnswer.SCHEMA_VERSION,
                        type,
                        List.of("opt_2"),
                        null,
                        Map.of(),
                        null),
                AssessmentStimulus.readingPassage(
                        "PRIVATE_CACHE_JSON passage",
                        "TEACHER"),
                "stored explanation",
                "vi",
                "NUMERIC",
                ObjectiveExplanationStrategyRegistry.requireSelection(
                        type,
                        ObjectiveExplanationStrategyRegistry.REGISTRY_VERSION,
                        ObjectiveExplanationStrategyRegistry.Code
                                .EVIDENCE_ONLY.name(),
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION));
    }

    private static ExplanationContext matchingContext() {
        CanonicalQuestionType type = CanonicalQuestionType.MATCHING;
        QuestionContent content = new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("option_1", "서울"),
                        new QuestionContent.Option("option_2", "제주")),
                List.of(
                        new QuestionContent.Blank("blank_1", "수도"),
                        new QuestionContent.Blank("blank_2", "섬")));
        AnswerSpec answerSpec = new AnswerSpec(
                AnswerSpec.SCHEMA_VERSION,
                type,
                List.of(),
                null,
                List.of(
                        new AnswerSpec.BlankAnswer("blank_1", List.of("option_1")),
                        new AnswerSpec.BlankAnswer("blank_2", List.of("option_2"))),
                ScoringPolicyCode.NORMALIZED_EXACT);
        return new ExplanationContext(
                ExplanationContext.SCHEMA_VERSION,
                2L,
                20L,
                2,
                AssessmentSkill.READING,
                type,
                "각 설명을 연결하십시오.",
                "A–H에서 고르십시오.",
                content,
                answerSpec,
                new LearnerAnswer(
                        LearnerAnswer.SCHEMA_VERSION,
                        type,
                        List.of(),
                        null,
                        Map.of(),
                        null),
                AssessmentStimulus.readingPassage(
                        "서울 근거",
                        "TEACHER"),
                "Giải thích của giáo viên",
                "vi",
                "LATIN",
                ObjectiveExplanationStrategyRegistry.requireSelection(
                        type,
                        ObjectiveExplanationStrategyRegistry.CURRENT_REGISTRY_VERSION,
                        ObjectiveExplanationStrategyRegistry.Code.MATCHING_MATRIX.name(),
                        ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION));
    }

    private static String captureLogs(Class<?> loggerClass, Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        StringBuilder logs = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            logs.append(event.getFormattedMessage()).append('\n');
        }
        return logs.toString();
    }
}
