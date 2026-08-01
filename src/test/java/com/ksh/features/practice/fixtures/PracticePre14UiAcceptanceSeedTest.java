package com.ksh.features.practice.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationTestFixtures;
import com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingContractTestFixtures;
import com.ksh.features.practice.ai.writing.WritingDiagnosticContract;
import com.ksh.features.practice.ai.writing.WritingEvaluationNormalizer;
import com.ksh.features.practice.ai.writing.WritingEvidenceLedgerVerifier;
import com.ksh.features.practice.ai.writing.WritingRubricCriterion;
import com.ksh.features.practice.ai.writing.WritingScoringCriterion;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.ai.writing.WritingTaskRequirementPolicy;
import com.ksh.features.practice.assessment.AnswerSpec;
import com.ksh.features.practice.assessment.CanonicalQuestionType;
import com.ksh.features.practice.assessment.LearnerAnswer;
import com.ksh.features.practice.assessment.ObjectiveExplanationStrategyRegistry;
import com.ksh.features.practice.assessment.QuestionContent;
import com.ksh.features.practice.assessment.ScoringPolicyCode;
import com.ksh.features.practice.assessment.WritingBlankContract;
import com.ksh.features.practice.assessment.WritingBlankContractVerifier;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.service.PracticeAttemptAnswerCodec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ksh.features.practice.ai.writing.WritingContractTestFixtures.addEvidence;
import static com.ksh.features.practice.ai.writing.WritingContractTestFixtures.addFinding;
import static com.ksh.features.practice.ai.writing.WritingContractTestFixtures.applyRawScore;
import static com.ksh.features.practice.ai.writing.WritingContractTestFixtures.replaceIds;
import static com.ksh.features.practice.ai.writing.WritingContractTestFixtures.rubric;
import static com.ksh.features.practice.ai.writing.WritingContractTestFixtures.zeroEnvelope;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicitly enabled DEV/TEST-only Pre-14 UI acceptance seed.
 *
 * <p>The harness refuses every catalog outside {@code ksh_test_pre14_ui_*}.
 * It never runs during the normal suite and never calls an AI, STT, TTS,
 * ingestion or media provider.</p>
 */
class PracticePre14UiAcceptanceSeedTest {

    private static final String ENABLED =
            "KSH_PRE14_UI_SEED_ENABLED";
    private static final String JDBC_URL =
            "KSH_PRE14_UI_SEED_JDBC_URL";
    private static final String JDBC_USER =
            "KSH_PRE14_UI_SEED_JDBC_USER";
    private static final String JDBC_PASSWORD =
            "KSH_PRE14_UI_SEED_JDBC_PASSWORD";
    private static final String LOAD_BASE =
            "KSH_PRE14_UI_SEED_LOAD_BASE";
    private static final Pattern SAFE_URL = Pattern.compile(
            "^jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):\\d+/"
                    + "(ksh_test_pre14_ui_[A-Za-z0-9_]+)"
                    + "(?:\\?.*)?$");
    private static final Path BASE_FIXTURE = Path.of(
            "scripts/dev/practice-phase13e-result-fixtures.sql");
    private static final String FIXTURE_TIME =
            "2026-07-30 06:00:00";
    private static final long READING_OBJECTIVE_VERSION_ID = 141L;
    private static final long LISTENING_OBJECTIVE_VERSION_ID = 142L;
    private static final List<SpeakingChipDescriptor>
            PREMIUM_SPEAKING_CHIPS = List.of(
            speakingChip(
                    SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                    "S_CONTENT_RELEVANCE", "관심사", "CONTENT",
                    "mức độ liên quan đến đề"),
            speakingChip(
                    SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                    "S_CONTENT_PROMPT_COVERAGE", "목표", "CONTENT",
                    "mức độ bao phủ yêu cầu"),
            speakingChip(
                    SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                    "S_CONTENT_SPECIFICITY_EXAMPLES", "한국어 모임",
                    "CONTENT", "độ cụ thể và ví dụ"),
            speakingChip(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "S_GRAMMAR_PARTICLES", "친구들과", "GRAMMAR",
                    "tiểu từ"),
            speakingChip(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "S_GRAMMAR_TENSE_ASPECT", "공부했습니다", "GRAMMAR",
                    "thời và thể"),
            speakingChip(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "S_GRAMMAR_ENDINGS", "하고 싶습니다", "GRAMMAR",
                    "đuôi câu"),
            speakingChip(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "S_GRAMMAR_SENTENCE_STRUCTURE",
                    "시간이 있으면 연습합니다", "GRAMMAR",
                    "cấu trúc câu"),
            speakingChip(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "S_GRAMMAR_HONORIFIC_REGISTER",
                    "말씀드리겠습니다", "GRAMMAR",
                    "kính ngữ và mức độ trang trọng"),
            speakingChip(
                    SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL,
                    "S_GRAMMAR_CONNECTORS", "그래서", "GRAMMAR",
                    "từ nối và liên kết"),
            speakingChip(
                    SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                    "S_VOCAB_TOPIC_WORDS", "한국 드라마", "VOCABULARY",
                    "từ vựng theo chủ đề"),
            speakingChip(
                    SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                    "S_VOCAB_NATURAL_EXPRESSIONS", "꾸준히 익혀 가다",
                    "VOCABULARY", "cách diễn đạt tự nhiên"),
            speakingChip(
                    SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                    "S_VOCAB_REPETITION_CONTROL", "정말", "VOCABULARY",
                    "kiểm soát lặp từ"),
            speakingChip(
                    SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS,
                    "S_VOCAB_WORD_CHOICE", "도움이 되다", "VOCABULARY",
                    "lựa chọn từ ngữ"),
            speakingChip(
                    SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                    "S_COHERENCE_ORGANIZATION", "첫째", "ORGANIZATION",
                    "tổ chức ý"),
            speakingChip(
                    SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                    "S_COHERENCE_LOGICAL_FLOW", "그 결과", "ORGANIZATION",
                    "mạch phát triển logic"),
            speakingChip(
                    SpeakingRubricCriterion.COHERENCE_ORGANIZATION,
                    "S_COHERENCE_DISCOURSE_MARKERS", "마지막으로",
                    "ORGANIZATION", "dấu hiệu liên kết diễn ngôn"));
    private static final List<StrategyFixture> GROUP_SOURCE_STRATEGIES =
            List.of(
                    strategy(
                            "EXACT_EVIDENCE_ONLY",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "FULL_SOURCE_INLINE_HIGHLIGHT",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "MCQ_OPTION_ELIMINATION",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "TFNG_CONTRADICTION_TABLE",
                            CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
                    strategy(
                            "FILL_SLOT_GRAMMAR_ANALYSIS",
                            CanonicalQuestionType.FILL_BLANK),
                    strategy(
                            "KEYWORD_PARAPHRASE_BRIDGE",
                            CanonicalQuestionType.FILL_BLANK));
    private static final List<StrategyFixture> STANDALONE_STRATEGIES =
            List.of(
                    strategy(
                            "QUESTION_EVIDENCE_TRANSLATION_TABLE",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "EVIDENCE_AND_ELIMINATION",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "BILINGUAL_STEP_BY_STEP",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "NOT_GIVEN_BOUNDARY",
                            CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
                    strategy(
                            "FULL_SOURCE_INLINE_HIGHLIGHT",
                            CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
                    strategy(
                            "EXACT_EVIDENCE_ONLY",
                            CanonicalQuestionType.FILL_BLANK));
    private static final List<StrategyFixture> LECTURER_STRATEGIES =
            List.of(
                    strategy(
                            "EXACT_EVIDENCE_ONLY",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "FULL_SOURCE_INLINE_HIGHLIGHT",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "QUESTION_EVIDENCE_TRANSLATION_TABLE",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "MCQ_OPTION_ELIMINATION",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "EVIDENCE_AND_ELIMINATION",
                            CanonicalQuestionType.SINGLE_CHOICE),
                    strategy(
                            "TFNG_CONTRADICTION_TABLE",
                            CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
                    strategy(
                            "NOT_GIVEN_BOUNDARY",
                            CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
                    strategy(
                            "FILL_SLOT_GRAMMAR_ANALYSIS",
                            CanonicalQuestionType.FILL_BLANK),
                    strategy(
                            "KEYWORD_PARAPHRASE_BRIDGE",
                            CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN),
                    strategy(
                            "BILINGUAL_STEP_BY_STEP",
                            CanonicalQuestionType.FILL_BLANK),
                    strategy(
                            "EVIDENCE_AND_ELIMINATION",
                            CanonicalQuestionType.MULTIPLE_ANSWER),
                    strategy(
                            "MATCHING_MATRIX",
                            CanonicalQuestionType.MATCHING));

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadDeterministicPre14UiAcceptanceSeed() throws Exception {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv(ENABLED)),
                "DEV/TEST acceptance seed is opt-in");
        String url = requiredEnv(JDBC_URL);
        String user = requiredEnv(JDBC_USER);
        String password = requiredEnvPresent(JDBC_PASSWORD);
        String catalog = safeCatalog(url);

        Flyway.configure()
                .dataSource(url, user, password)
                .cleanDisabled(true)
                .load()
                .migrate();

        try (Connection connection =
                     DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            if ("true".equalsIgnoreCase(System.getenv(LOAD_BASE))) {
                loadHistoricalBase(connection, catalog);
            }
            seedObjectiveScenarios(connection);
            seedWritingScenarios(connection);
            seedSpeakingScenarios(connection);
            seedLecturerAuthoringScenarios(connection);
            connection.commit();
            verifySeed(connection);
        }
    }

    private void loadHistoricalBase(
            Connection connection,
            String catalog) throws Exception {
        String sql = Files.readString(BASE_FIXTURE)
                .replace(
                        "USE ksh_phase13e_result_ui;",
                        "USE `" + catalog + "`;");
        ScriptUtils.executeSqlScript(
                connection,
                new EncodedResource(
                        new ByteArrayResource(
                                sql.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8));
    }

    private void seedObjectiveScenarios(
            Connection connection) throws Exception {
        List<ObjectiveScenario> scenarios = new ArrayList<>();
        scenarios.addAll(objectiveScenarios("READING", 14100, 14101));
        scenarios.addAll(objectiveScenarios("LISTENING", 14200, 14201));

        upsertObjectiveVersionSnapshot(
                connection, "READING", 1L, 1L, 1L,
                READING_OBJECTIVE_VERSION_ID);
        upsertObjectiveVersionSnapshot(
                connection, "LISTENING", 2L, 2L, 2L,
                LISTENING_OBJECTIVE_VERSION_ID);
        seedObjectiveGroups(connection, "READING", 14100);
        seedObjectiveGroups(connection, "LISTENING", 14200);
        for (ObjectiveScenario scenario : scenarios) {
            upsertObjectiveQuestion(connection, scenario);
            upsertObjectiveArtifact(connection, scenario);
        }
        upsertObjectiveAttempt(
                connection,
                14100L,
                "READING",
                1L,
                1L,
                READING_OBJECTIVE_VERSION_ID,
                scenarios.stream()
                        .filter(row -> "READING".equals(row.skill()))
                        .toList());
        upsertObjectiveAttempt(
                connection,
                14200L,
                "LISTENING",
                2L,
                2L,
                LISTENING_OBJECTIVE_VERSION_ID,
                scenarios.stream()
                        .filter(row -> "LISTENING".equals(row.skill()))
                        .toList());
        upsertObjectivePlayerAttempt(connection);
    }

    private static long objectiveVersionId(String skill) {
        return "READING".equals(skill)
                ? READING_OBJECTIVE_VERSION_ID
                : LISTENING_OBJECTIVE_VERSION_ID;
    }

    private static void upsertObjectiveVersionSnapshot(
            Connection connection,
            String skill,
            long setId,
            long testId,
            long sectionId,
            long versionId) throws Exception {
        String label = "READING".equals(skill)
                ? "Luyện đọc theo dạng câu hỏi"
                : "Luyện nghe theo dạng câu hỏi";
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_published_versions (
                  id, set_id, version_number, status, content_hash,
                  published_by, published_at, created_at
                ) VALUES (?, ?, ?, 'PUBLISHED', ?, 1, ?, ?)
                ON DUPLICATE KEY UPDATE
                  set_id=VALUES(set_id),
                  version_number=VALUES(version_number),
                  status='PUBLISHED',
                  content_hash=VALUES(content_hash),
                  published_by=VALUES(published_by),
                  published_at=VALUES(published_at)
                """)) {
            statement.setLong(1, versionId);
            statement.setLong(2, setId);
            statement.setInt(3, Math.toIntExact(versionId));
            statement.setString(
                    4,
                    sha256("pre14-objective-version-" + skill));
            statement.setString(5, FIXTURE_TIME);
            statement.setString(6, FIXTURE_TIME);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_set_versions (
                  id, published_version_id, set_id, title, description,
                  skill, scope, class_id, metadata_json, creation_method,
                  cover_image_url
                ) VALUES (?, ?, ?, ?,
                  'DEV/TEST deterministic typed R/L result fixture',
                  ?, 'GLOBAL', NULL,
                  JSON_OBJECT('source','PRE14_DEV_TEST_FIXTURE'),
                  'MANUAL', NULL)
                ON DUPLICATE KEY UPDATE
                  published_version_id=VALUES(published_version_id),
                  set_id=VALUES(set_id),
                  title=VALUES(title),
                  description=VALUES(description),
                  skill=VALUES(skill),
                  scope=VALUES(scope),
                  metadata_json=VALUES(metadata_json),
                  creation_method=VALUES(creation_method)
                """)) {
            statement.setLong(1, versionId);
            statement.setLong(2, versionId);
            statement.setLong(3, setId);
            statement.setString(4, label);
            statement.setString(5, skill);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_test_versions (
                  id, published_version_id, set_version_id, test_id,
                  title, description, display_order, estimated_minutes
                ) VALUES (?, ?, ?, ?, ?,
                  'DEV/TEST deterministic typed R/L result fixture',
                  0, 30)
                ON DUPLICATE KEY UPDATE
                  published_version_id=VALUES(published_version_id),
                  set_version_id=VALUES(set_version_id),
                  test_id=VALUES(test_id),
                  title=VALUES(title),
                  description=VALUES(description),
                  display_order=VALUES(display_order),
                  estimated_minutes=VALUES(estimated_minutes)
                """)) {
            statement.setLong(1, versionId);
            statement.setLong(2, versionId);
            statement.setLong(3, versionId);
            statement.setLong(4, testId);
            statement.setString(5, label);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_section_versions (
                  id, published_version_id, test_version_id, section_id,
                  title, skill, section_type, instructions, delivery_json,
                  duration_minutes, total_points, display_order
                ) VALUES (?, ?, ?, ?, ?, ?, 'MAIN',
                  'Đọc hoặc nghe kỹ nguồn đề rồi trả lời các câu hỏi.',
                  JSON_OBJECT('source','PRE14_DEV_TEST_FIXTURE'),
                  30, 12.00, 0)
                ON DUPLICATE KEY UPDATE
                  published_version_id=VALUES(published_version_id),
                  test_version_id=VALUES(test_version_id),
                  section_id=VALUES(section_id),
                  title=VALUES(title),
                  skill=VALUES(skill),
                  section_type=VALUES(section_type),
                  instructions=VALUES(instructions),
                  delivery_json=VALUES(delivery_json),
                  duration_minutes=VALUES(duration_minutes),
                  total_points=VALUES(total_points),
                  display_order=VALUES(display_order)
                """)) {
            statement.setLong(1, versionId);
            statement.setLong(2, versionId);
            statement.setLong(3, versionId);
            statement.setLong(4, sectionId);
            statement.setString(5, label);
            statement.setString(6, skill);
            statement.executeUpdate();
        }
    }

    private List<ObjectiveScenario> objectiveScenarios(
            String skill,
            long groupBase,
            long questionBase) throws Exception {
        List<ObjectiveScenario> rows = new ArrayList<>();
        int sequence = 0;
        for (String sourceMode : List.of(
                "GROUP_SOURCE", "STANDALONE_NO_PASSAGE")) {
            List<StrategyFixture> strategies =
                    "GROUP_SOURCE".equals(sourceMode)
                            ? GROUP_SOURCE_STRATEGIES
                            : STANDALONE_STRATEGIES;
            for (StrategyFixture strategy : strategies) {
                long questionVersionId = questionBase + sequence;
                String answerState = switch (sequence % 4) {
                    case 0 -> "SELECTED_CORRECT";
                    case 1 -> "SELECTED_WRONG";
                    case 2 -> "CORRECT_NOT_SELECTED";
                    default -> "UNANSWERED";
                };
                rows.add(objectiveScenario(
                        skill,
                        sourceMode,
                        strategy.questionType(),
                        strategy.code(),
                        answerState,
                        questionVersionId,
                        groupBase + ("GROUP_SOURCE".equals(sourceMode)
                                ? 0 : 50),
                        sequence));
                sequence++;
            }
        }
        rows.add(objectiveScenario(
                skill,
                "GROUP_SOURCE",
                CanonicalQuestionType.MATCHING,
                "MATCHING_MATRIX",
                "SELECTED_CORRECT",
                questionBase + sequence,
                groupBase,
                sequence));
        sequence++;
        rows.add(objectiveScenario(
                skill,
                "STANDALONE_NO_PASSAGE",
                CanonicalQuestionType.MULTIPLE_ANSWER,
                "EVIDENCE_AND_ELIMINATION",
                "SELECTED_WRONG",
                questionBase + sequence,
                groupBase + 50,
                sequence));
        return List.copyOf(rows);
    }

    private ObjectiveScenario objectiveScenario(
            String skill,
            String sourceMode,
            CanonicalQuestionType type,
            String strategy,
            String answerState,
            long questionVersionId,
            long groupVersionId,
            int sequence) throws Exception {
        boolean standalone =
                "STANDALONE_NO_PASSAGE".equals(sourceMode);
        String groupSource = objectiveGroupSource(skill);
        String prompt = switch (type) {
            case SINGLE_CHOICE -> standalone
                    ? "정답은 도서관입니다. 어디에서 공부합니까?"
                    : "READING".equals(skill)
                    ? "민수는 어디에서 공부합니까?"
                    : "여자는 어디에서 자료를 찾습니까?";
            case TRUE_FALSE_NOT_GIVEN -> standalone
                    ? "NOT_GIVEN_BOUNDARY".equals(strategy)
                    ? "자료: 민수는 도서관에서 공부합니다. 문장: 민수는 매일 세 시간 공부합니다. 이 문장은 맞습니까?"
                    : "민수는 도서관에서 공부합니다. 이 문장은 맞습니까?"
                    : "TFNG_CONTRADICTION_TABLE".equals(strategy)
                    ? "READING".equals(skill)
                    ? "민수는 공원에서 공부합니다."
                    : "여자는 공원에서 발표 자료를 찾습니다."
                    : "READING".equals(skill)
                    ? "민수는 도서관에서 공부합니다."
                    : "여자는 도서관에서 발표 자료를 찾습니다.";
            case FILL_BLANK -> standalone
                    ? "민수는 도서관에서 공부합니다. 민수는 ___에서 공부합니다."
                    : "READING".equals(skill)
                    ? "민수는 ___에서 공부합니다."
                    : "여자는 ___에서 발표 자료를 찾습니다.";
            case MULTIPLE_ANSWER ->
                    "정답은 도서관과 전자 자료실입니다. 자료를 찾을 수 있는 두 곳을 모두 고르십시오.";
            case MATCHING ->
                    "다음 문장 1–4와 가장 알맞은 정보 A–H를 연결하십시오.";
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException();
        };
        ObjectiveExplanationStrategyRegistry.requireSelection(
                type,
                ObjectiveExplanationStrategyRegistry
                        .CURRENT_REGISTRY_VERSION,
                strategy,
                ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION);
        String source = standalone ? prompt : groupSource;
        String quote = switch (type) {
            case SINGLE_CHOICE -> standalone
                    ? "정답은 도서관입니다"
                    : "도서관에서";
            case TRUE_FALSE_NOT_GIVEN, FILL_BLANK ->
                    standalone
                            ? "민수는 도서관에서 공부합니다"
                            : objectiveEvidenceQuote(skill);
            case MULTIPLE_ANSWER -> "정답은 도서관과 전자 자료실입니다";
            case MATCHING -> objectiveEvidenceQuote(skill);
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException();
        };
        QuestionContent content = content(type, skill);
        AnswerSpec answerSpec = answerSpec(type, strategy, skill);
        String rawAnswer = learnerAnswer(type, answerState, answerSpec);
        return new ObjectiveScenario(
                skill,
                sourceMode,
                type,
                strategy,
                answerState,
                questionVersionId,
                questionVersionId,
                groupVersionId,
                sequence + 1,
                prompt,
                source,
                quote,
                mapper.writeValueAsString(content),
                mapper.writeValueAsString(answerSpec),
                rawAnswer);
    }

    private static QuestionContent content(
            CanonicalQuestionType type,
            String skill) {
        return switch (type) {
            case SINGLE_CHOICE -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(
                            new QuestionContent.Option(
                                    "option_1", "도서관"),
                            new QuestionContent.Option(
                                    "option_2", "공원"),
                            new QuestionContent.Option(
                                    "option_3", "교실")),
                    List.of());
            case TRUE_FALSE_NOT_GIVEN -> QuestionContent.empty();
            case FILL_BLANK -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(),
                    List.of(new QuestionContent.Blank(
                            "blank_1",
                            "READING".equals(skill)
                                    ? "민수는 ___에서 공부합니다."
                                    : "여자는 ___에서 발표 자료를 찾습니다.")));
            case MULTIPLE_ANSWER -> new QuestionContent(
                    QuestionContent.SCHEMA_VERSION,
                    List.of(
                            new QuestionContent.Option("option_1", "도서관"),
                            new QuestionContent.Option("option_2", "공원"),
                            new QuestionContent.Option("option_3", "교실"),
                            new QuestionContent.Option("option_4", "전자 자료실")),
                    List.of());
            case MATCHING -> matchingContent(skill);
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException();
        };
    }

    private static QuestionContent matchingContent(String skill) {
        boolean reading = "READING".equals(skill);
        return new QuestionContent(
                QuestionContent.SCHEMA_VERSION,
                List.of(
                        new QuestionContent.Option("option_1", "도서관"),
                        new QuestionContent.Option("option_2", "공원"),
                        new QuestionContent.Option("option_3", "교실"),
                        new QuestionContent.Option("option_4", "전자 자료실"),
                        new QuestionContent.Option("option_5", reading
                                ? "지역 문화 행사" : "책과 학술 자료"),
                        new QuestionContent.Option("option_6", reading
                                ? "문법과 어휘 복습" : "주제별 검색"),
                        new QuestionContent.Option("option_7", reading
                                ? "친구와 읽기 자료 비교" : "자료 정리"),
                        new QuestionContent.Option("option_8", reading
                                ? "근거가 있는 문장" : "친구들과 발표 순서 점검")),
                List.of(
                        new QuestionContent.Blank("blank_1", "1. 첫 번째 장소"),
                        new QuestionContent.Blank("blank_2", "2. 다음 행동"),
                        new QuestionContent.Blank("blank_3", "3. 핵심 정보"),
                        new QuestionContent.Blank("blank_4", "4. 마무리 활동")));
    }

    private static AnswerSpec answerSpec(
            CanonicalQuestionType type,
            String strategy,
            String skill) {
        return switch (type) {
            case SINGLE_CHOICE -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION,
                    type,
                    List.of("option_1"),
                    null,
                    List.of(),
                    ScoringPolicyCode.ALL_OR_NOTHING);
            case TRUE_FALSE_NOT_GIVEN -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION,
                    type,
                    List.of(),
                    "TFNG_CONTRADICTION_TABLE".equals(strategy)
                            ? "FALSE"
                            : "NOT_GIVEN_BOUNDARY".equals(strategy)
                            ? "NOT_GIVEN"
                            : "TRUE",
                    List.of(),
                    ScoringPolicyCode.ALL_OR_NOTHING);
            case FILL_BLANK -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION,
                    type,
                    List.of(),
                    null,
                    List.of(new AnswerSpec.BlankAnswer(
                            "blank_1",
                            List.of("도서관"))),
                    ScoringPolicyCode.NORMALIZED_EXACT);
            case MULTIPLE_ANSWER -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION,
                    type,
                    List.of("option_1", "option_4"),
                    null,
                    List.of(),
                    ScoringPolicyCode.ALL_OR_NOTHING);
            case MATCHING -> new AnswerSpec(
                    AnswerSpec.SCHEMA_VERSION,
                    type,
                    List.of(),
                    null,
                    List.of(
                            new AnswerSpec.BlankAnswer("blank_1", List.of("option_1")),
                            new AnswerSpec.BlankAnswer("blank_2", List.of(
                                    "READING".equals(skill) ? "option_7" : "option_4")),
                            new AnswerSpec.BlankAnswer("blank_3", List.of(
                                    "READING".equals(skill) ? "option_5" : "option_6")),
                            new AnswerSpec.BlankAnswer("blank_4", List.of("option_8"))),
                    ScoringPolicyCode.NORMALIZED_EXACT);
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException();
        };
    }

    private String learnerAnswer(
            CanonicalQuestionType type,
            String state,
            AnswerSpec answerSpec) throws Exception {
        LearnerAnswer answer = switch (type) {
            case SINGLE_CHOICE -> new LearnerAnswer(
                    LearnerAnswer.SCHEMA_VERSION,
                    type,
                    "SELECTED_CORRECT".equals(state)
                            ? List.of("option_1")
                            : "SELECTED_WRONG".equals(state)
                            ? List.of("option_2")
                            : List.of(),
                    null,
                    Map.of(),
                    null);
            case TRUE_FALSE_NOT_GIVEN -> new LearnerAnswer(
                    LearnerAnswer.SCHEMA_VERSION,
                    type,
                    List.of(),
                    "SELECTED_CORRECT".equals(state)
                            ? answerSpec.correctValue()
                            : "SELECTED_WRONG".equals(state)
                            ? ("TRUE".equals(answerSpec.correctValue())
                                    ? "FALSE"
                                    : "TRUE")
                            : null,
                    Map.of(),
                    null);
            case FILL_BLANK -> new LearnerAnswer(
                    LearnerAnswer.SCHEMA_VERSION,
                    type,
                    List.of(),
                    null,
                    "SELECTED_CORRECT".equals(state)
                            ? Map.of("blank_1", "도서관")
                            : "SELECTED_WRONG".equals(state)
                            ? Map.of("blank_1", "공원")
                            : Map.of(),
                    null);
            case MULTIPLE_ANSWER -> new LearnerAnswer(
                    LearnerAnswer.SCHEMA_VERSION,
                    type,
                    "SELECTED_CORRECT".equals(state)
                            ? answerSpec.correctOptionIds()
                            : "SELECTED_WRONG".equals(state)
                            ? List.of("option_1", "option_2")
                            : List.of(),
                    null,
                    Map.of(),
                    null);
            case MATCHING -> new LearnerAnswer(
                    LearnerAnswer.SCHEMA_VERSION,
                    type,
                    List.of(),
                    null,
                    "SELECTED_CORRECT".equals(state)
                            ? answerSpec.blanks().stream().collect(
                            java.util.stream.Collectors.toMap(
                                    AnswerSpec.BlankAnswer::blankId,
                                    row -> row.acceptedValues().get(0)))
                            : "SELECTED_WRONG".equals(state)
                            ? Map.of(
                                    "blank_1", "option_2",
                                    "blank_2", "option_3",
                                    "blank_3", "option_4",
                                    "blank_4", "option_5")
                            : Map.of(),
                    null);
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException();
        };
        return mapper.writeValueAsString(answer);
    }

    private void seedObjectiveGroups(
            Connection connection,
            String skill,
            long groupBase) throws Exception {
        String source = objectiveGroupSource(skill);
        long publishedVersionId = objectiveVersionId(skill);
        long sectionVersionId = publishedVersionId;
        upsertGroup(
                connection,
                groupBase,
                publishedVersionId,
                sectionVersionId,
                "READING".equals(skill)
                        ? "Nguồn bài đọc đã khóa"
                        : "Nguồn bài nghe đã khóa",
                "READING".equals(skill) ? "PASSAGE" : "AUDIO_TRANSCRIPT",
                "READING".equals(skill) ? source : null,
                "LISTENING".equals(skill) ? source : null,
                "LISTENING".equals(skill)
                        ? "/audio/practice/listening-speaker-check.wav"
                        : null,
                0);
        upsertGroup(
                connection,
                groupBase + 50,
                publishedVersionId,
                sectionVersionId,
                "Câu độc lập không có nguồn chung",
                "NONE",
                null,
                null,
                null,
                1);
    }

    private static String objectiveGroupSource(String skill) {
        if ("READING".equals(skill)) {
            return """
                    민수는 도서관에서 한국어를 공부합니다. 도서관은 집에서 가깝고 조용해서 집중하기 좋습니다.

                    평일에는 수업이 끝난 뒤 두 시간 동안 문법과 어휘를 복습합니다. 모르는 표현은 사전에서 뜻을 확인하고 예문을 공책에 적습니다. 금요일에는 같은 반 친구와 함께 읽기 자료를 비교하며 서로의 답을 설명합니다.

                    이번 주에는 지역 문화 행사에 관한 글을 읽었습니다. 민수는 글의 중심 생각과 세부 정보를 따로 표시한 뒤, 근거가 있는 문장만 사용해 질문에 답했습니다. 이런 연습 덕분에 긴 글에서도 필요한 정보를 더 빠르게 찾을 수 있게 되었습니다.
                    """;
        }
        if ("LISTENING".equals(skill)) {
            return """
                    진행자 00:00
                    다음은 학교 발표를 준비하는 학생들의 대화입니다. 대화를 듣고 질문에 답하십시오.

                    여자 00:08
                    여자는 도서관에서 발표 자료를 찾습니다. 조용한 열람실에서 책과 학술 자료를 먼저 확인하려고 합니다.

                    남자 00:18
                    필요한 책이 대출 중이면 전자 자료실도 이용해 보세요. 주제별 검색 기능을 쓰면 관련 자료를 빠르게 찾을 수 있습니다.

                    여자 00:31
                    좋은 생각이에요. 자료를 정리한 뒤에는 발표 순서를 친구들과 함께 점검하겠습니다.
                    """;
        }
        throw new IllegalArgumentException(
                "Unsupported objective seed skill: " + skill);
    }

    private static String objectiveEvidenceQuote(String skill) {
        return "READING".equals(skill)
                ? "민수는 도서관에서 한국어를 공부합니다"
                : "여자는 도서관에서 발표 자료를 찾습니다";
    }

    private static void upsertGroup(
            Connection connection,
            long id,
            long publishedVersionId,
            long sectionVersionId,
            String label,
            String stimulusType,
            String passage,
            String transcript,
            String audioUrl,
            int order) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_question_group_versions (
                  id, published_version_id, section_version_id, group_id,
                  group_label, question_from, question_to, instruction,
                  stimulus_type, passage_text, transcript_text, image_url,
                  stimulus_provenance_json, audio_url, example_json,
                  display_order
                ) VALUES (?, ?, ?, ?, ?, 1, 12, ?,
                  ?, ?, ?, NULL,
                  JSON_OBJECT('source','PRE14_DEV_TEST_FIXTURE',
                              'approved',TRUE),
                  ?, NULL, ?)
                ON DUPLICATE KEY UPDATE
                  published_version_id=VALUES(published_version_id),
                  section_version_id=VALUES(section_version_id),
                  group_label=VALUES(group_label),
                  instruction=VALUES(instruction),
                  stimulus_type=VALUES(stimulus_type),
                  passage_text=VALUES(passage_text),
                  transcript_text=VALUES(transcript_text),
                  stimulus_provenance_json=VALUES(
                    stimulus_provenance_json),
                  audio_url=VALUES(audio_url),
                  display_order=VALUES(display_order)
                """)) {
            statement.setLong(1, id);
            statement.setLong(2, publishedVersionId);
            statement.setLong(3, sectionVersionId);
            statement.setLong(4, id);
            statement.setString(5, label);
            statement.setString(
                    6,
                    "Đọc hoặc nghe nguồn dưới đây rồi trả lời câu hỏi.");
            statement.setString(7, stimulusType);
            statement.setString(8, passage);
            statement.setString(9, transcript);
            statement.setString(10, audioUrl);
            statement.setInt(11, order);
            statement.executeUpdate();
        }
    }

    private void upsertObjectiveQuestion(
            Connection connection,
            ObjectiveScenario scenario) throws Exception {
        long publishedVersionId =
                objectiveVersionId(scenario.skill());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_question_versions (
                  id, published_version_id, section_version_id,
                  group_version_id, question_id, question_no,
                  question_type, prompt, options_json,
                  question_content_json, answer_key, answer_spec_json,
                  explanation,
                  explanation_strategy_registry_version,
                  explanation_strategy_code,
                  explanation_strategy_version,
                  points, display_order, writing_task_type
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, NULL, ?,
                  NULL, ?, ?, ?, 1.00, ?, NULL)
                ON DUPLICATE KEY UPDATE
                  published_version_id=VALUES(published_version_id),
                  section_version_id=VALUES(section_version_id),
                  group_version_id=VALUES(group_version_id),
                  question_no=VALUES(question_no),
                  question_type=VALUES(question_type),
                  prompt=VALUES(prompt),
                  question_content_json=VALUES(question_content_json),
                  answer_spec_json=VALUES(answer_spec_json),
                  explanation_strategy_registry_version=VALUES(
                    explanation_strategy_registry_version),
                  explanation_strategy_code=VALUES(
                    explanation_strategy_code),
                  explanation_strategy_version=VALUES(
                    explanation_strategy_version),
                  display_order=VALUES(display_order)
                """)) {
            statement.setLong(1, scenario.questionVersionId());
            statement.setLong(2, publishedVersionId);
            statement.setLong(3, publishedVersionId);
            statement.setLong(4, scenario.groupVersionId());
            statement.setLong(5, scenario.questionId());
            statement.setInt(6, scenario.questionNo());
            statement.setString(7, scenario.type().name());
            statement.setString(8, scenario.prompt());
            statement.setString(9, scenario.questionContentJson());
            statement.setString(10, scenario.answerSpecJson());
            statement.setString(
                    11,
                    ObjectiveExplanationStrategyRegistry
                            .CURRENT_REGISTRY_VERSION);
            statement.setString(12, scenario.strategyCode());
            statement.setString(
                    13,
                    ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION);
            statement.setInt(14, scenario.questionNo() - 1);
            statement.executeUpdate();
        }
    }

    private void upsertObjectiveArtifact(
            Connection connection,
            ObjectiveScenario scenario) throws Exception {
        long artifactId = 151000L + scenario.questionVersionId();
        String fingerprint =
                sha256("pre14-ui-objective-" + scenario.questionVersionId());
        String input = mapper.writeValueAsString(
                objectiveInput(scenario));
        String output = mapper.writeValueAsString(
                objectiveOutput(scenario));
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO question_explanation_artifacts (
                  id, fingerprint, legacy_cache_id, skill, question_type,
                  assessment_schema_version, provider_model,
                  prompt_version, response_schema_version,
                  explanation_language, question_hash, stimulus_hash,
                  answer_spec_hash, media_bundle_hash,
                  input_contract_json, status, explanation_json,
                  error_category, last_error_message, ready_at, failed_at,
                  created_at, updated_at
                ) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, 'v4', 'vi',
                  ?, ?, ?, ?, ?, 'READY', ?, NULL, NULL, ?, NULL, ?, ?)
                ON DUPLICATE KEY UPDATE
                  fingerprint=VALUES(fingerprint),
                  skill=VALUES(skill),
                  question_type=VALUES(question_type),
                  response_schema_version='v4',
                  input_contract_json=VALUES(input_contract_json),
                  status='READY',
                  explanation_json=VALUES(explanation_json),
                  error_category=NULL,
                  last_error_message=NULL,
                  ready_at=VALUES(ready_at),
                  failed_at=NULL,
                  updated_at=VALUES(updated_at)
                """)) {
            statement.setLong(1, artifactId);
            statement.setString(2, fingerprint);
            statement.setString(3, scenario.skill());
            statement.setString(4, scenario.type().name());
            statement.setString(5, "rl-assessment-contract-v1");
            statement.setString(6, "pre14-dev-test-fixture");
            statement.setString(7, "rl-explanation-prompt-v4");
            statement.setString(
                    8,
                    sha256("question-" + scenario.questionVersionId()));
            statement.setString(
                    9,
                    sha256("stimulus-" + scenario.questionVersionId()));
            statement.setString(
                    10,
                    sha256("answer-" + scenario.questionVersionId()));
            statement.setString(11, sha256("no-media"));
            statement.setString(12, input);
            statement.setString(13, output);
            statement.setString(14, FIXTURE_TIME);
            statement.setString(15, FIXTURE_TIME);
            statement.setString(16, FIXTURE_TIME);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO question_version_explanation_bindings (
                  id, question_version_id, artifact_id,
                  explanation_language, fingerprint, binding_status,
                  bound_at, superseded_at
                ) VALUES (?, ?, ?, 'vi', ?, 'ACTIVE', ?, NULL)
                ON DUPLICATE KEY UPDATE
                  artifact_id=VALUES(artifact_id),
                  fingerprint=VALUES(fingerprint),
                  binding_status='ACTIVE',
                  bound_at=VALUES(bound_at),
                  superseded_at=NULL
                """)) {
            statement.setLong(1, artifactId);
            statement.setLong(2, scenario.questionVersionId());
            statement.setLong(3, artifactId);
            statement.setString(4, fingerprint);
            statement.setString(5, FIXTURE_TIME);
            statement.executeUpdate();
        }
    }

    private ObjectNode objectiveInput(
            ObjectiveScenario scenario) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "rl-explanation-input-v3");
        root.put("skill", scenario.skill());
        root.put("questionType", scenario.type().name());
        root.put("prompt", scenario.prompt());
        ObjectNode strategy = root.putObject("explanationStrategy");
        strategy.put(
                "registryVersion",
                ObjectiveExplanationStrategyRegistry
                        .CURRENT_REGISTRY_VERSION);
        strategy.put("strategyCode", scenario.strategyCode());
        strategy.put(
                "strategyVersion",
                ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION);
        root.set(
                "questionContent",
                mapper.readTree(scenario.questionContentJson()));
        root.set(
                "answerSpec",
                mapper.readTree(scenario.answerSpecJson()));
        ObjectNode stimulus = root.putObject("stimulus");
        stimulus.put("schemaVersion", "assessment-stimulus-v1");
        boolean standalone = "STANDALONE_NO_PASSAGE".equals(
                scenario.sourceMode());
        if (standalone) {
            stimulus.put("type", "STANDALONE_PROMPT");
            stimulus.put("passageText", scenario.prompt());
            stimulus.putNull("transcriptText");
        } else if ("READING".equals(scenario.skill())) {
            stimulus.put("type", "READING_PASSAGE");
            stimulus.put("passageText", scenario.source());
            stimulus.putNull("transcriptText");
        } else {
            stimulus.put("type", "LISTENING_AUDIO");
            stimulus.putNull("passageText");
            stimulus.put("transcriptText", scenario.source());
        }
        stimulus.put("approved", true);
        root.putArray("media");
        return root;
    }

    private ObjectNode objectiveOutput(
            ObjectiveScenario scenario) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "v4");
        root.put(
                "strategyRegistryVersion",
                ObjectiveExplanationStrategyRegistry
                        .CURRENT_REGISTRY_VERSION);
        root.put("strategyCode", scenario.strategyCode());
        root.put(
                "strategyVersion",
                ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION);
        root.put("questionType", scenario.type().name());
        ObjectNode explanation = root.putObject("explanation");
        ArrayNode textEvidence = explanation.putArray(
                "textEvidenceRefs");
        ObjectNode evidence = textEvidence.addObject();
        evidence.put("evidenceId", "E-" + scenario.questionVersionId());
        boolean transcript = "LISTENING".equals(scenario.skill())
                && "GROUP_SOURCE".equals(scenario.sourceMode());
        evidence.put(
                "kind", transcript ? "TRANSCRIPT_SPAN" : "TEXT_SPAN");
        evidence.put("purpose", "ANSWER_RATIONALE");
        evidence.put(
                "sourceRole",
                "STANDALONE_NO_PASSAGE".equals(scenario.sourceMode())
                        ? "QUESTION_PROMPT"
                        : "READING".equals(scenario.skill())
                        ? "PASSAGE" : "TRANSCRIPT");
        evidence.put("exactQuoteKo", scenario.quote());
        int start = scenario.source().indexOf(scenario.quote());
        if (start < 0) {
            throw new IllegalArgumentException(
                    "Objective fixture quote is not exact for "
                            + scenario.questionVersionId()
                            + " quote=" + scenario.quote()
                            + " source=" + scenario.source());
        }
        evidence.put("startOffset", start);
        evidence.put("endOffset", start + scenario.quote().length());
        explanation.putArray("imageEvidenceRefs");
        ArrayNode translations = explanation.putArray(
                "relevantTranslations");
        translations.addObject()
                .put("evidenceId", "E-" + scenario.questionVersionId())
                .put("translationVi", evidenceTranslationVi(scenario));
        ObjectNode block = explanation.putObject("strategyBlock");
        String evidenceId = "E-" + scenario.questionVersionId();
        ObjectiveExplanationStrategyRegistry.GenerationFamily
                outputFamily = switch (scenario.type()) {
            case TRUE_FALSE_NOT_GIVEN ->
                    ObjectiveExplanationStrategyRegistry.GenerationFamily
                            .TFNG_RELATION;
            case FILL_BLANK ->
                    ObjectiveExplanationStrategyRegistry.GenerationFamily
                            .FILL_CONSTRAINTS;
            case SINGLE_CHOICE -> ObjectiveExplanationStrategyRegistry.Code
                    .parse(scenario.strategyCode()).generationFamily();
            case MULTIPLE_ANSWER ->
                    ObjectiveExplanationStrategyRegistry.GenerationFamily
                            .EVIDENCE_AND_ELIMINATION;
            case MATCHING ->
                    ObjectiveExplanationStrategyRegistry.GenerationFamily
                            .EVIDENCE;
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException();
        };
        if (scenario.type() == CanonicalQuestionType.MATCHING) {
            matchingRationales(
                    block.putArray("targetExplanations"),
                    scenario,
                    evidenceId);
            return root;
        }
        switch (outputFamily) {
            case EVIDENCE -> block.putArray("evidenceClaims")
                    .add(claim(
                            "C-EVIDENCE-" + scenario.questionVersionId(),
                            evidenceClaimVi(scenario),
                            evidenceId));
            case OPTION_ELIMINATION ->
                    optionRationales(
                            block.putArray("optionRationales"),
                            scenario.questionVersionId(),
                            evidenceId,
                            scenario);
            case FULL_CONTEXT -> {
                block.putArray("contextClaims")
                        .add(claim(
                                "C-CONTEXT-" + scenario.questionVersionId(),
                                contextClaimVi(scenario),
                                evidenceId));
                block.set(
                        "answerClaim",
                        claim(
                                "C-ANSWER-" + scenario.questionVersionId(),
                                answerClaimVi(scenario),
                                evidenceId));
            }
            case EVIDENCE_AND_ELIMINATION -> {
                block.putArray("contextClaims")
                        .add(claim(
                                "C-HYBRID-CONTEXT-"
                                        + scenario.questionVersionId(),
                                contextClaimVi(scenario),
                                evidenceId));
                block.set(
                        "answerClaim",
                        claim(
                                "C-HYBRID-ANSWER-"
                                        + scenario.questionVersionId(),
                                answerClaimVi(scenario),
                                evidenceId));
                optionRationales(
                        block.putArray("optionRationales"),
                        scenario.questionVersionId() + 50_000,
                        evidenceId,
                        scenario);
            }
            case TFNG_RELATION -> {
                boolean contradiction = "TFNG_CONTRADICTION_TABLE"
                        .equals(scenario.strategyCode());
                boolean notGiven = "NOT_GIVEN_BOUNDARY"
                        .equals(scenario.strategyCode());
                block.set("claim", claim(
                        "C-TFNG-CLAIM-" + scenario.questionVersionId(),
                        "Mệnh đề được đối chiếu trực tiếp với câu nguồn: “"
                                + scenario.quote() + "”.",
                        evidenceId));
                block.set("whyTrue", claim(
                        "C-TFNG-TRUE-" + scenario.questionVersionId(),
                        contradiction
                                ? "Không chọn Đúng vì mệnh đề nêu 공원, trong khi nguồn xác định 도서관."
                                : notGiven
                                ? "Không chọn Đúng vì nguồn không nêu thời lượng học mỗi ngày."
                                : "Nguồn nêu trực tiếp cùng người, địa điểm và hành động nên mệnh đề Đúng.",
                        evidenceId));
                block.set("whyFalse", claim(
                        "C-TFNG-FALSE-" + scenario.questionVersionId(),
                        contradiction
                                ? "Nguồn nêu 도서관, trái trực tiếp với địa điểm 공원 trong mệnh đề, nên đáp án là Sai."
                                : notGiven
                                ? "Không chọn Sai vì nguồn cũng không phủ định thời lượng ba giờ."
                                : "Không chọn Sai vì nguồn không nêu thông tin trái với mệnh đề.",
                        evidenceId));
                block.set("whyNotGiven", claim(
                        "C-TFNG-NG-" + scenario.questionVersionId(),
                        contradiction
                                ? "Không chọn Không có thông tin vì nguồn đã nêu rõ địa điểm 도서관 để đối chiếu với 공원."
                                : notGiven
                                ? "Chọn Không có thông tin vì nguồn chỉ nêu địa điểm, không nêu số giờ học mỗi ngày."
                                : "Không chọn Không có thông tin vì địa điểm 도서관 đã xuất hiện trực tiếp.",
                        evidenceId));
                block.set("missingInformation", claim(
                        "C-TFNG-MISSING-" + scenario.questionVersionId(),
                        notGiven
                                ? "Nguồn thiếu thời lượng và tần suất học cần để kết luận mệnh đề."
                                : "Nguồn đã nêu đủ chủ thể, hành động và địa điểm cần kiểm chứng.",
                        evidenceId));
            }
            case FILL_CONSTRAINTS -> {
                ObjectNode blank = block.putArray("blankExplanations")
                        .addObject();
                blank.put(
                        "claimId",
                        "C-FILL-" + scenario.questionVersionId());
                blank.put("blankId", "blank_1");
                blank.put(
                        "contextExplanationVi",
                        "Nguồn xác định địa điểm cần điền.");
                blank.put(
                        "semanticConstraintVi",
                        "Cần một danh từ chỉ địa điểm.");
                blank.put(
                        "grammarConstraintVi",
                        "Danh từ đứng trước tiểu từ 에서.");
                blank.put(
                        "registerConstraintVi",
                        "Không có ràng buộc văn phong bổ sung.");
                blank.putArray("evidenceIds").add(evidenceId);
            }
        }
        return root;
    }

    private ObjectNode claim(
            String claimId,
            String textVi,
            String evidenceId) {
        ObjectNode claim = mapper.createObjectNode();
        claim.put("claimId", claimId);
        claim.put("textVi", textVi);
        claim.putArray("evidenceIds").add(evidenceId);
        return claim;
    }

    private void optionRationales(
            ArrayNode target,
            long id,
            String evidenceId,
            ObjectiveScenario scenario) {
        int optionCount = scenario.type()
                == CanonicalQuestionType.MULTIPLE_ANSWER ? 4 : 3;
        for (int index = 1; index <= optionCount; index++) {
            ObjectNode rationale = target.addObject();
            rationale.put("claimId", "C-OPTION-" + id + "-" + index);
            rationale.put("optionId", "option_" + index);
            rationale.put(
                    "reasonVi",
                    switch (index) {
                        case 1 -> "Giữ lại 도서관 vì cụm “"
                                + scenario.quote()
                                + "” xác nhận đúng địa điểm.";
                        case 2 -> "Loại 공원 vì nguồn chỉ nêu 도서관, không nêu công viên.";
                        case 3 -> "Loại 교실 vì nguồn không nêu lớp học là nơi thực hiện hành động.";
                        case 4 -> "Giữ lại 전자 자료실 vì câu hỏi xác định đây là địa điểm thứ hai cần chọn.";
                        default -> throw new IllegalArgumentException(
                                "Unexpected objective fixture option: " + index);
                    });
            rationale.putArray("evidenceIds").add(evidenceId);
        }
    }

    private void matchingRationales(
            ArrayNode target,
            ObjectiveScenario scenario,
            String evidenceId) throws Exception {
        AnswerSpec answerSpec = mapper.readValue(
                scenario.answerSpecJson(), AnswerSpec.class);
        for (AnswerSpec.BlankAnswer answer : answerSpec.blanks()) {
            ObjectNode rationale = target.addObject();
            rationale.put(
                    "claimId",
                    "C-MATCH-" + scenario.questionVersionId()
                            + "-" + answer.blankId());
            rationale.put("targetId", answer.blankId());
            rationale.put(
                    "candidateOptionId", answer.acceptedValues().get(0));
            rationale.put(
                    "reasonVi",
                    "Đối chiếu câu nguồn và ghép đúng nhãn A–H theo thông tin đã khóa.");
            rationale.putArray("evidenceIds").add(evidenceId);
        }
    }

    private static String evidenceTranslationVi(
            ObjectiveScenario scenario) {
        if (scenario.quote().contains("여자는")) {
            return "Người phụ nữ tìm tài liệu thuyết trình tại thư viện.";
        }
        if (scenario.quote().contains("민수는")) {
            return "Min-su học tại thư viện.";
        }
        if (scenario.quote().contains("정답은")) {
            return "Đáp án là thư viện.";
        }
        return "Tại thư viện.";
    }

    private static String contextClaimVi(
            ObjectiveScenario scenario) {
        return switch (scenario.type()) {
            case SINGLE_CHOICE -> "Câu hỏi yêu cầu xác định địa điểm được nêu trong nguồn.";
            case TRUE_FALSE_NOT_GIVEN -> "Mệnh đề phải được kiểm tra theo đúng chủ thể, hành động và địa điểm trong nguồn.";
            case FILL_BLANK -> "Ô trống cần một danh từ chỉ địa điểm đứng trước tiểu từ 에서.";
            case MULTIPLE_ANSWER -> "Câu hỏi yêu cầu chọn đủ hai địa điểm được nêu trực tiếp.";
            case MATCHING -> "Mỗi câu phải được ghép với đúng một nhãn A–H theo nguồn chung.";
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException();
        };
    }

    private static String answerClaimVi(
            ObjectiveScenario scenario) {
        return switch (scenario.type()) {
            case SINGLE_CHOICE -> "Cụm “"
                    + scenario.quote()
                    + "” xác định đáp án 도서관 (thư viện).";
            case TRUE_FALSE_NOT_GIVEN -> "TFNG_CONTRADICTION_TABLE"
                    .equals(scenario.strategyCode())
                    ? "Nguồn nêu 도서관, trái với địa điểm 공원 trong mệnh đề, vì vậy kết luận là Sai."
                    : "NOT_GIVEN_BOUNDARY".equals(scenario.strategyCode())
                    ? "Nguồn không nêu thời lượng học mỗi ngày, vì vậy kết luận là Không có thông tin."
                    : "Câu nguồn trực tiếp xác nhận nội dung mệnh đề, vì vậy kết luận là Đúng.";
            case FILL_BLANK -> "Từ 도서관 hoàn chỉnh ngữ nghĩa địa điểm và kết hợp đúng với 에서.";
            case MULTIPLE_ANSWER -> "Hai đáp án đúng là 도서관 và 전자 자료실; chọn thiếu hoặc thừa đều không đúng.";
            case MATCHING -> "Mỗi target dùng candidate ID chính thức đã được khóa trong answer spec.";
            case ESSAY, SPEAKING ->
                    throw new IllegalArgumentException();
        };
    }

    private static String evidenceClaimVi(
            ObjectiveScenario scenario) {
        return "Vùng “" + scenario.quote()
                + "” là span ngắn nhất đủ để xác định đáp án 도서관.";
    }

    private void upsertObjectiveAttempt(
            Connection connection,
            long attemptId,
            String skill,
            long setId,
            long testId,
            long versionId,
            List<ObjectiveScenario> scenarios) throws Exception {
        long sectionId = "READING".equals(skill) ? 1L : 2L;
        ObjectNode answers = mapper.createObjectNode();
        for (ObjectiveScenario scenario : scenarios) {
            answers.put(
                    String.valueOf(scenario.questionId()),
                    scenario.rawAnswer());
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_attempts (
                  id, user_id, set_id, test_id, skill, section_id,
                  status, analysis_status, score, total_points,
                  score_unit, earned_points, score_percentage,
                  answers_json, ai_feedback_json,
                  analysis_requested_at, analysis_completed_at,
                  analysis_engine, analysis_error_code,
                  started_at, deadline_at, submitted_at, discarded_at,
                  created_at, updated_at, lock_version,
                  published_version_id, set_version_id,
                  test_version_id, section_version_id,
                  version_compatibility_status,
                  version_compatibility_note
                ) VALUES (
                  ?, 4, ?, ?, ?, ?, 'GRADED', 'NOT_REQUESTED',
                  50.00, 100.00, 'EARNED_POINTS', 50.00, 50.00,
                  ?, NULL, NULL, NULL, NULL, NULL,
                  ?, '2026-07-30 07:00:00', ?, NULL,
                  ?, ?, 0, ?, ?, ?, ?, NULL,
                  'Pre-14 DEV/TEST deterministic UI acceptance seed'
                )
                ON DUPLICATE KEY UPDATE
                  user_id=4, set_id=VALUES(set_id), test_id=VALUES(test_id),
                  skill=VALUES(skill), section_id=VALUES(section_id),
                  status='GRADED', analysis_status='NOT_REQUESTED',
                  score=VALUES(score), total_points=VALUES(total_points),
                  score_unit=VALUES(score_unit),
                  earned_points=VALUES(earned_points),
                  score_percentage=VALUES(score_percentage),
                  answers_json=VALUES(answers_json),
                  ai_feedback_json=NULL,
                  submitted_at=VALUES(submitted_at),
                  discarded_at=NULL, updated_at=VALUES(updated_at),
                  lock_version=0,
                  published_version_id=VALUES(published_version_id),
                  set_version_id=VALUES(set_version_id),
                  test_version_id=VALUES(test_version_id),
                  section_version_id=VALUES(section_version_id),
                  version_compatibility_note=VALUES(
                    version_compatibility_note)
                """)) {
            statement.setLong(1, attemptId);
            statement.setLong(2, setId);
            statement.setLong(3, testId);
            statement.setString(4, skill);
            statement.setLong(5, sectionId);
            statement.setString(6, mapper.writeValueAsString(answers));
            statement.setString(7, FIXTURE_TIME);
            statement.setString(8, FIXTURE_TIME);
            statement.setString(9, FIXTURE_TIME);
            statement.setString(10, FIXTURE_TIME);
            statement.setLong(11, versionId);
            statement.setLong(12, versionId);
            statement.setLong(13, versionId);
            statement.setLong(14, versionId);
            statement.executeUpdate();
        }
    }

    private void upsertObjectivePlayerAttempt(
            Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_attempts (
                  id, user_id, set_id, test_id, skill, section_id,
                  status, analysis_status, answers_json,
                  started_at, deadline_at, created_at, updated_at,
                  lock_version, published_version_id, set_version_id,
                  test_version_id, section_version_id,
                  version_compatibility_note
                ) VALUES (
                  14800, 4, 1, 1, 'READING', 1,
                  'IN_PROGRESS', 'NOT_REQUESTED', '{}',
                  ?, '2036-08-01 07:00:00', ?, ?,
                  0, 141, 141, 141, 141,
                  'Pre-14 typed player acceptance seed'
                )
                ON DUPLICATE KEY UPDATE
                  user_id=4, set_id=1, test_id=1, skill='READING',
                  section_id=1, status='IN_PROGRESS',
                  analysis_status='NOT_REQUESTED', answers_json='{}',
                  started_at=VALUES(started_at),
                  deadline_at=VALUES(deadline_at),
                  submitted_at=NULL, discarded_at=NULL,
                  score=NULL, total_points=NULL, score_unit=NULL,
                  earned_points=NULL, score_percentage=NULL,
                  ai_feedback_json=NULL, analysis_requested_at=NULL,
                  analysis_completed_at=NULL, analysis_engine=NULL,
                  analysis_error_code=NULL, updated_at=VALUES(updated_at),
                  lock_version=0, published_version_id=141,
                  set_version_id=141, test_version_id=141,
                  section_version_id=141,
                  version_compatibility_note=VALUES(
                    version_compatibility_note)
                """)) {
            statement.setString(1, FIXTURE_TIME);
            statement.setString(2, FIXTURE_TIME);
            statement.setString(3, FIXTURE_TIME);
            statement.executeUpdate();
        }
    }

    private void seedWritingScenarios(
            Connection connection) throws Exception {
        upsertWritingQuestion(
                connection, 14351L, 14351L, 51, "Q51",
                "문맥에 맞게 첫 번째와 두 번째 빈칸을 완성하십시오.");
        upsertWritingQuestion(
                connection, 14352L, 14352L, 52, "Q52",
                "안내문 문맥에 맞게 두 빈칸을 완성하십시오.");
        upsertWritingQuestion(
                connection, 4L, 4L, 53, "Q53",
                "2024년과 2026년 교통수단 이용률 변화를 "
                        + "200~300자로 설명하십시오.");
        upsertWritingQuestion(
                connection, 14354L, 14354L, 54, "Q54",
                "교육 현장에서 디지털 기기를 사용하는 것에 대한 "
                        + "자신의 입장을 밝히고 장점, 위험과 구체적인 "
                        + "제안을 포함하여 600~700자로 논하십시오.");

        for (WritingCase fixtureCase : WritingCase.values()) {
            long attemptId = 14301L + fixtureCase.ordinal();
            ObjectNode answers = mapper.createObjectNode();
            ObjectNode feedback = mapper.createObjectNode();
            for (WritingQuestion question : writingQuestions()) {
                String answer = writingAnswer(
                        question.taskType(), fixtureCase);
                answers.put(
                        String.valueOf(question.questionId()), answer);
                if (fixtureCase == WritingCase.PENDING) {
                    continue;
                }
                if (fixtureCase == WritingCase.UNAVAILABLE) {
                    ObjectNode unavailable = feedback.putObject(
                            String.valueOf(question.questionId()));
                    unavailable.put(
                            "evaluation_status",
                            "EVALUATION_UNAVAILABLE");
                    unavailable.put(
                            "evaluation_reason",
                            "DEV_TEST_PROVIDER_DISABLED");
                    unavailable.put("score_available", false);
                    continue;
                }
                ObjectNode envelope = writingEnvelope(
                        question.taskType(), fixtureCase, answer);
                String normalized = new WritingEvaluationNormalizer(mapper)
                        .normalize(
                                mapper.writeValueAsString(envelope),
                                question.taskType(),
                                answer,
                                null);
                JsonNode normalizedNode = mapper.readTree(normalized);
                assertThat(normalizedNode.path(
                                "evaluation_status").asText())
                        .as("%s %s seed contract",
                                question.taskType(), fixtureCase)
                        .isEqualTo("EVALUATED");
                feedback.set(
                        String.valueOf(question.questionId()),
                        normalizedNode);
            }
            upsertWritingAttempt(
                    connection,
                    attemptId,
                    answers,
                    feedback,
                    fixtureCase);
        }
        seedPremiumWritingAttempt(
                connection, 14601L, PremiumWritingCase.Q51_ALL_STRENGTHS);
        seedPremiumWritingAttempt(
                connection, 14602L, PremiumWritingCase.Q52_ALL_IMPROVEMENTS);
        seedPremiumWritingAttempt(
                connection, 14603L, PremiumWritingCase.Q53_ALL_STRENGTHS);
        seedPremiumWritingAttempt(
                connection, 14604L, PremiumWritingCase.Q54_ALL_IMPROVEMENTS);
        seedPremiumWritingAttempt(
                connection, 14605L, PremiumWritingCase.Q54_UNIQUE_STRENGTHS);
        seedPremiumWritingAttempt(
                connection, 14606L, PremiumWritingCase.Q53_UNIQUE_IMPROVEMENTS);
    }

    private void seedPremiumWritingAttempt(
            Connection connection,
            long attemptId,
            PremiumWritingCase premiumCase) throws Exception {
        WritingBlankContract.LearnerResponse q51Response =
                premiumClozeResponse(
                        WritingTaskType.Q51,
                        WritingRubricCriterion.Polarity.STRENGTH);
        WritingBlankContract.LearnerResponse q52Response =
                premiumClozeResponse(
                        WritingTaskType.Q52,
                        WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT);
        String q51Source = mapper.writeValueAsString(q51Response);
        String q52Source = mapper.writeValueAsString(q52Response);
        String q53Answer = Q53_CANONICAL_ANSWER;
        String q54Answer =
                premiumCase == PremiumWritingCase.Q54_ALL_IMPROVEMENTS
                        ? q54PremiumImprovementAnswer()
                        : Q54_PERFECT_ANSWER;

        PracticeAttemptAnswerCodec codec =
                new PracticeAttemptAnswerCodec(mapper);
        PracticeAttemptAnswerCodec.DecodedAnswers decodedAnswers =
                new PracticeAttemptAnswerCodec.DecodedAnswers(
                        Map.of(
                                "4", q53Answer,
                                "14354", q54Answer),
                        Map.of(
                                "14351", q51Response,
                                "14352", q52Response),
                        false,
                        false);
        ObjectNode answers = (ObjectNode) mapper.readTree(
                codec.write(decodedAnswers));

        ObjectNode feedback = mapper.createObjectNode();
        BigDecimal reconciledAttemptScore = BigDecimal.ZERO;
        boolean canonicalPairwiseAttempt =
                premiumCase == PremiumWritingCase.Q54_ALL_IMPROVEMENTS;
        for (WritingQuestion question : writingQuestions()) {
            String taskType = question.taskType();
            String source = switch (taskType) {
                case "Q51" -> q51Source;
                case "Q52" -> q52Source;
                case "Q53" -> q53Answer;
                case "Q54" -> q54Answer;
                default -> throw new IllegalArgumentException(
                        "Unsupported premium Writing task");
            };
            ObjectNode envelope;
            boolean target = premiumCase.taskType().equals(taskType);
            if (canonicalPairwiseAttempt) {
                envelope = switch (taskType) {
                    case "Q51" ->
                            premiumClozeEnvelope(
                                    "Q51",
                                    source,
                                    WritingRubricCriterion.Polarity.STRENGTH);
                    case "Q52" ->
                            premiumClozeEnvelope(
                                    "Q52",
                                    source,
                                    WritingRubricCriterion.Polarity
                                            .NEEDS_IMPROVEMENT);
                    case "Q53" -> q53PremiumStrengthEnvelope(source);
                    case "Q54" -> q54PremiumImprovementEnvelope(source);
                    default -> throw new IllegalArgumentException(
                            "Unsupported premium Writing task");
                };
            } else if (!target) {
                envelope = noDiagnosticPremiumEnvelope(
                        taskType, source);
            } else {
                envelope = switch (premiumCase) {
                    case Q51_ALL_STRENGTHS ->
                            premiumClozeEnvelope(
                                    "Q51",
                                    source,
                                    WritingRubricCriterion.Polarity.STRENGTH);
                    case Q52_ALL_IMPROVEMENTS ->
                            premiumClozeEnvelope(
                                    "Q52",
                                    source,
                                    WritingRubricCriterion.Polarity
                                            .NEEDS_IMPROVEMENT);
                    case Q53_ALL_STRENGTHS ->
                            q53PremiumStrengthEnvelope(source);
                    case Q54_ALL_IMPROVEMENTS ->
                            q54PremiumImprovementEnvelope(source);
                    case Q54_UNIQUE_STRENGTHS ->
                            q54UniqueStrengthEnvelope(source);
                    case Q53_UNIQUE_IMPROVEMENTS ->
                            q53UniqueImprovementEnvelope(source);
                };
            }
            ObjectNode normalized = normalizePremiumWriting(
                    taskType, source, envelope);
            if (target || canonicalPairwiseAttempt) {
                addTeacherSampleFixture(
                        normalized, premiumCase, taskType);
            }
            feedback.set(
                    String.valueOf(question.questionId()),
                    normalized);
            reconciledAttemptScore = reconciledAttemptScore.add(
                    normalized.path("raw_score").decimalValue());
        }
        assertThat(reconciledAttemptScore)
                .as("%s reconciled attempt score", premiumCase)
                .isBetween(BigDecimal.ZERO, new BigDecimal("100"));
        upsertPremiumWritingAttempt(
                connection,
                attemptId,
                answers,
                feedback,
                reconciledAttemptScore);
    }

    private ObjectNode noDiagnosticPremiumEnvelope(
            String taskType,
            String source) {
        ObjectNode envelope =
                zeroEnvelope(mapper, taskType, source);
        int maximum = WritingScoringPolicy.rubricFor(taskType)
                .criteria().stream()
                .mapToInt(WritingScoringCriterion::maxScore)
                .sum();
        applyRawScore(
                envelope,
                taskType,
                source,
                maximum);
        return envelope;
    }

    private WritingBlankContract.LearnerResponse premiumClozeResponse(
            WritingTaskType taskType,
            WritingRubricCriterion.Polarity polarity) {
        List<WritingRubricCriterion> features =
                WritingRubricCriterion.activeForTask(taskType.name())
                        .stream()
                        .filter(feature -> feature.polarity() == polarity)
                        .toList();
        List<WritingBlankContract.LearnerBlankAnswer> answers =
                new ArrayList<>();
        for (int blankIndex = 1; blankIndex <= 2; blankIndex++) {
            String suffix = blankIndex == 1 ? "첫째" : "둘째";
            String text = features.stream()
                    .map(feature ->
                            feature.koreanLabel() + " " + suffix)
                    .collect(java.util.stream.Collectors.joining(" · "));
            answers.add(new WritingBlankContract.LearnerBlankAnswer(
                    taskType.name().toLowerCase()
                            + "-b" + blankIndex,
                    text));
        }
        return new WritingBlankContract.LearnerResponse(
                WritingBlankContract.LEARNER_SCHEMA_VERSION,
                taskType,
                WritingBlankContract.RESPONSE_MODE,
                answers);
    }

    private ObjectNode normalizePremiumWriting(
            String taskType,
            String answer,
            ObjectNode envelope) throws Exception {
        try {
            new WritingEvidenceLedgerVerifier().verify(
                    envelope, taskType, answer);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    taskType + " premium envelope: "
                            + exception.getMessage(),
                    exception);
        }
        String normalized = new WritingEvaluationNormalizer(mapper)
                .normalize(
                        mapper.writeValueAsString(envelope),
                        taskType,
                        answer,
                        null);
        ObjectNode normalizedNode =
                (ObjectNode) mapper.readTree(normalized);
        assertThat(normalizedNode.path(
                        "evaluation_status").asText())
                .as("%s premium seed contract: %s",
                        taskType,
                        normalizedNode.path(
                                "evaluation_reason").asText())
                .isEqualTo("EVALUATED");
        return normalizedNode;
    }

    private void addTeacherSampleFixture(
            ObjectNode normalized,
            PremiumWritingCase premiumCase,
            String taskType) {
        ObjectNode sample = normalized.putObject("teacher_sample");
        sample.put("contractVersion", "ksh-teacher-sample-v1");
        sample.put("source", "TEACHER_AUTHORED");
        sample.put("authorRole", "LECTURER");
        sample.put("fixtureId", "PRE14-" + premiumCase.name());
        sample.put(
                "content",
                switch (taskType) {
                    case "Q51" ->
                            "문맥과 문체에 맞게 두 빈칸을 자연스럽게 완성합니다.";
                    case "Q52" ->
                            "안내문의 목적과 연결 관계를 확인하여 정확하게 씁니다.";
                    case "Q53" -> Q53_PERFECT_ANSWER;
                    case "Q54" -> Q54_PERFECT_ANSWER;
                    default -> throw new IllegalArgumentException();
                });
    }

    private static List<WritingQuestion> writingQuestions() {
        return List.of(
                new WritingQuestion(14351L, "Q51"),
                new WritingQuestion(14352L, "Q52"),
                new WritingQuestion(4L, "Q53"),
                new WritingQuestion(14354L, "Q54"));
    }

    private static String writingAnswer(
            String taskType,
            WritingCase fixtureCase) {
        if ("Q51".equals(taskType) || "Q52".equals(taskType)) {
            return fixtureCase == WritingCase.NO_DIAGNOSTIC
                    ? "답을 쓰지 못했습니다."
                    : "첫 번째 답은 문맥에 맞지만 두 번째 표현은 어색합니다.";
        }
        if ("Q53".equals(taskType)) {
            if (fixtureCase == WritingCase.MIXED
                    || fixtureCase == WritingCase.PENDING
                    || fixtureCase == WritingCase.UNAVAILABLE) {
                return Q53_CANONICAL_ANSWER;
            }
            if (fixtureCase == WritingCase.FULL) {
                return Q53_PERFECT_ANSWER;
            }
        }
        if ("Q54".equals(taskType)) {
            if (fixtureCase == WritingCase.MIXED
                    || fixtureCase == WritingCase.PENDING
                    || fixtureCase == WritingCase.UNAVAILABLE) {
                return Q54_MIXED_ANSWER;
            }
            if (fixtureCase == WritingCase.FULL) {
                return Q54_PERFECT_ANSWER;
            }
        }
        int rawScore = switch (fixtureCase) {
            case NO_DIAGNOSTIC -> 0;
            case FULL -> WritingScoringPolicy.rubricFor(taskType)
                    .criteria().stream()
                    .mapToInt(WritingScoringCriterion::maxScore)
                    .sum();
            case MIXED, PARTIAL -> "Q53".equals(taskType) ? 25 : 45;
            case PENDING, UNAVAILABLE -> throw new IllegalStateException(
                    "Pending/unavailable answers are handled above");
        };
        return WritingContractTestFixtures.scoreBearingLearnerAnswer(
                taskType, rawScore);
    }

    private ObjectNode writingEnvelope(
            String taskType,
            WritingCase fixtureCase,
            String answer) {
        if (("Q51".equals(taskType) || "Q52".equals(taskType))
                && fixtureCase != WritingCase.NO_DIAGNOSTIC
                && fixtureCase != WritingCase.FULL) {
            return clozePartialEnvelope(
                    taskType,
                    answer,
                    fixtureCase == WritingCase.MIXED);
        }
        if ("Q53".equals(taskType)
                && fixtureCase == WritingCase.MIXED) {
            return q53AtomicEnvelope(answer);
        }
        if ("Q54".equals(taskType)
                && fixtureCase == WritingCase.MIXED) {
            return q54MixedEnvelope(answer);
        }
        if (fixtureCase == WritingCase.FULL
                && ("Q53".equals(taskType)
                || "Q54".equals(taskType))) {
            return essayPerfectEvidenceRichEnvelope(taskType, answer);
        }
        ObjectNode envelope = zeroEnvelope(
                mapper, taskType, answer);
        if (fixtureCase == WritingCase.NO_DIAGNOSTIC) {
            return envelope;
        }
        int maximum = WritingScoringPolicy.rubricFor(taskType)
                .criteria().stream()
                .mapToInt(WritingScoringCriterion::maxScore)
                .sum();
        int rawScore = fixtureCase == WritingCase.FULL
                ? maximum
                : "Q53".equals(taskType) ? 25 : 45;
        applyRawScore(
                envelope, taskType, answer, rawScore);
        return envelope;
    }

    private ObjectNode premiumClozeEnvelope(
            String taskType,
            String answer,
            WritingRubricCriterion.Polarity polarity) {
        ObjectNode root = zeroEnvelope(mapper, taskType, answer);
        List<WritingRubricCriterion> features =
                WritingRubricCriterion.activeForTask(taskType)
                        .stream()
                        .filter(feature -> feature.polarity() == polarity)
                        .toList();
        int expected = polarity
                == WritingRubricCriterion.Polarity.STRENGTH ? 7 : 9;
        assertThat(features)
                .as("%s premium cloze feature catalogue", taskType)
                .hasSize(expected);

        Map<String, List<String>> rubricEvidence =
                new java.util.LinkedHashMap<>();
        Map<String, List<String>> rubricFindings =
                new java.util.LinkedHashMap<>();
        for (int blankIndex = 1; blankIndex <= 2; blankIndex++) {
            String suffix = blankIndex == 1 ? "첫째" : "둘째";
            String requirementId =
                    "CLOZE_BLANK_" + blankIndex + "_CONTEXT";
            String coverageEvidenceId = null;
            for (int featureIndex = 0;
                 featureIndex < features.size();
                 featureIndex++) {
                WritingRubricCriterion feature =
                        features.get(featureIndex);
                String evidenceId =
                        "EV_" + taskType + "_" + blankIndex
                                + "_" + (featureIndex + 1);
                String findingId =
                        "F_" + taskType + "_" + blankIndex
                                + "_" + feature.id();
                String exact =
                        feature.koreanLabel() + " " + suffix;
                addEvidence(
                        root,
                        evidenceId,
                        answer,
                        exact,
                        uniqueOffset(answer, exact));
                String parent =
                        WritingDiagnosticContract
                                .expectedParentCriterionId(
                                        feature,
                                        taskType,
                                        List.of(requirementId));
                assertThat(parent)
                        .as("%s %s blank %s parent",
                                taskType, feature.id(), blankIndex)
                        .isNotBlank();
                addFinding(
                        root,
                        findingId,
                        polarity
                                == WritingRubricCriterion.Polarity.STRENGTH
                                ? "STRENGTH" : "IMPROVEMENT",
                        polarity
                                == WritingRubricCriterion.Polarity.STRENGTH
                                ? "KEEP" : "REPLACE",
                        feature.id(),
                        writingSubtype(feature),
                        parent,
                        evidenceId,
                        List.of(requirementId),
                        polarity
                                == WritingRubricCriterion.Polarity.STRENGTH
                                ? "Bằng chứng trực tiếp xác nhận "
                                + feature.vietnameseLabel()
                                + " cho ô " + blankIndex + "."
                                : "Cần điều chỉnh "
                                + feature.vietnameseLabel()
                                + " tại ô " + blankIndex + ".",
                        polarity
                                == WritingRubricCriterion.Polarity.STRENGTH
                                ? ""
                                : "개선된 " + feature.koreanLabel(),
                        polarity
                                == WritingRubricCriterion.Polarity.STRENGTH
                                ? "MINOR" : "MODERATE");
                rubricEvidence.computeIfAbsent(
                                parent, ignored -> new ArrayList<>())
                        .add(evidenceId);
                rubricFindings.computeIfAbsent(
                                parent, ignored -> new ArrayList<>())
                        .add(findingId);
                if (feature == WritingRubricCriterion
                        .W_CLOZE_CONTEXT_FIT) {
                    coverageEvidenceId = evidenceId;
                }
            }
            ObjectNode coverage =
                    WritingContractTestFixtures.coverage(
                            root, requirementId);
            coverage.put(
                    "status",
                    polarity == WritingRubricCriterion.Polarity.STRENGTH
                            ? "MET" : "NOT_MET");
            if (coverageEvidenceId != null
                    && polarity
                    == WritingRubricCriterion.Polarity.STRENGTH) {
                replaceIds(
                        coverage,
                        "evidenceIds",
                        coverageEvidenceId);
            }
        }
        for (WritingScoringCriterion criterion
                : WritingScoringPolicy.rubricFor(taskType).criteria()) {
            ObjectNode row =
                    rubric(root, criterion.criterionId());
            row.put(
                    "score",
                    polarity == WritingRubricCriterion.Polarity.STRENGTH
                            ? criterion.maxScore() : 0);
            List<String> evidenceIds = rubricEvidence.getOrDefault(
                    criterion.criterionId(), List.of());
            List<String> findingIds = rubricFindings.getOrDefault(
                    criterion.criterionId(), List.of());
            replaceIds(
                    row,
                    "evidenceIds",
                    evidenceIds.toArray(String[]::new));
            replaceIds(
                    row,
                    "findingIds",
                    findingIds.toArray(String[]::new));
        }
        if (polarity
                == WritingRubricCriterion.Polarity.NEEDS_IMPROVEMENT) {
            WritingRubricCriterion first = features.get(0);
            ObjectNode upgrade = root.with("upgradedAnswer");
            upgrade.put(
                    "content",
                    "두 빈칸을 문맥과 문체에 맞게 정확하게 고쳤습니다.");
            rewrite(
                    upgrade,
                    "F_" + taskType + "_1_" + first.id(),
                    "EV_" + taskType + "_1_1",
                    "개선된 " + first.koreanLabel(),
                    "Sửa một span có bằng chứng để chứng minh "
                            + "bài nâng cấp có provenance.");
        }
        return root;
    }

    private static String writingSubtype(
            WritingRubricCriterion feature) {
        return switch (feature) {
            case W_ACCURATE_SPELLING_SPACING,
                    W_SPELLING_SPACING_ERRORS -> "SPACING";
            case W_FORMAL_REGISTER_CONSISTENCY,
                    W_CLOZE_REGISTER_MATCH,
                    W_REGISTER_CONSISTENCY_ISSUES -> "REGISTER";
            case W_FORMAL_VOCABULARY_USAGE,
                    W_TOPIC_SPECIFIC_EXPRESSIONS ->
                    "DOMAIN_SINO_KOREAN";
            case W_NATURAL_KOREAN_EXPRESSIONS,
                    W_SENTENCE_COMPLETION_NATURALNESS,
                    W_AWKWARD_UNNATURAL_EXPRESSIONS ->
                    "NATURALNESS";
            case W_CLOZE_CONTEXT_FIT ->
                    "CONTENT_RELEVANCE";
            case W_CONNECTIVE_ENDING_ACCURACY,
                    W_CLOZE_GRAMMAR_COMPATIBILITY ->
                    "ENDINGS_CONJUGATION";
            case W_VOCABULARY_ERRORS -> "VOCABULARY_SENSE_CONTEXT";
            case W_GRAMMAR_ERRORS,
                    W_SENTENCE_STRUCTURE_ISSUES ->
                    "SENTENCE_COMPLETENESS";
            case W_PARTICLE_ERRORS -> "MORPHOLOGY_PARTICLES";
            case W_ADVANCED_GRAMMAR_STRUCTURES,
                    W_SENTENCE_PATTERN_VARIETY ->
                    "ADNOMINAL_RELATIVE_EMBEDDED_CLAUSE";
            case W_LENGTH_REQUIREMENT_MET -> "TASK_LENGTH";
            case W_TASK_REQUIREMENT_COVERAGE,
                    W_TASK_REQUIREMENT_MISSING ->
                    "REQUIREMENT_COVERAGE";
            case W_ACCURATE_DATA_DESCRIPTION -> "DATA_ACCURACY";
            case W_CLEAR_THESIS_OR_MAIN_IDEA -> "THESIS_MAIN_IDEA";
            case W_RELEVANT_EXAMPLES_OR_REASONS,
                    W_UNSUPPORTED_CLAIM -> "SUPPORT";
            case W_OFF_TOPIC_OR_WEAK_RELEVANCE ->
                    "CONTENT_RELEVANCE";
            case W_INSUFFICIENT_IDEA_DEVELOPMENT ->
                    "CONTENT_DEVELOPMENT";
            case W_LOGICAL_ORGANIZATION -> "GLOBAL_ORGANIZATION";
            case W_EFFECTIVE_TRANSITIONS -> "TRANSITION_USE";
            case W_WEAK_PARAGRAPH_ORGANIZATION -> "PARAGRAPHING";
            case W_LOGICAL_FLOW_ISSUES,
                    W_Q53_DATA_FLOW_ISSUES -> "LOGICAL_RELATION";
            case W_TRANSITION_DEVICE_ISSUES -> "CONNECTIVES";
            case W_REPETITIVE_WORDS_EXPRESSIONS -> "REPETITION";
            default -> throw new IllegalArgumentException(
                    "No premium subtype for " + feature.id());
        };
    }

    private ObjectNode q53PremiumStrengthEnvelope(
            String answer) {
        ObjectNode root = zeroEnvelope(mapper, "Q53", answer);
        List<WritingRubricCriterion> textFeatures =
                WritingRubricCriterion.activeForTask("Q53")
                        .stream()
                        .filter(feature -> feature.polarity()
                                == WritingRubricCriterion.Polarity.STRENGTH)
                        .filter(feature -> feature.supports(
                                WritingRubricCriterion.EvidenceScope
                                        .TEXT_SPAN))
                        .toList();
        assertThat(textFeatures)
                .as("Q53 premium text-grounded strength features")
                .hasSize(9);
        List<WritingRubricCriterion> wholeAnswerFeatures =
                WritingRubricCriterion.activeForTask("Q53")
                        .stream()
                        .filter(feature -> feature.polarity()
                                == WritingRubricCriterion.Polarity.STRENGTH)
                        .filter(feature -> !feature.supports(
                                WritingRubricCriterion.EvidenceScope
                                        .TEXT_SPAN))
                        .filter(feature -> feature.supports(
                                WritingRubricCriterion.EvidenceScope
                                        .WHOLE_ANSWER))
                        .toList();
        assertThat(wholeAnswerFeatures)
                .as("Q53 premium whole-answer strength features")
                .containsExactlyInAnyOrder(
                        WritingRubricCriterion.W_LENGTH_REQUIREMENT_MET,
                        WritingRubricCriterion.W_TASK_REQUIREMENT_COVERAGE,
                        WritingRubricCriterion.W_LOGICAL_ORGANIZATION);
        Map<String, List<String>> rubricEvidence =
                new java.util.LinkedHashMap<>();
        Map<String, List<String>> rubricFindings =
                new java.util.LinkedHashMap<>();
        Map<WritingRubricCriterion, String> evidenceByFeature =
                new java.util.LinkedHashMap<>();
        for (int index = 0; index < textFeatures.size(); index++) {
            WritingRubricCriterion feature = textFeatures.get(index);
            String evidenceId = "EV_Q53_PREMIUM_" + (index + 1);
            String findingId = "F_Q53_PREMIUM_" + feature.id();
            String exact = q53StrengthEvidence(feature);
            addEvidence(
                    root,
                    evidenceId,
                    answer,
                    exact,
                    uniqueOffset(answer, exact));
            String parent =
                    WritingDiagnosticContract
                            .expectedParentCriterionId(
                                    feature, "Q53");
            assertThat(parent)
                    .as("Q53 %s parent", feature.id())
                    .isNotBlank();
            addFinding(
                    root,
                    findingId,
                    "STRENGTH",
                    "KEEP",
                    feature.id(),
                    writingSubtype(feature),
                    parent,
                    evidenceId,
                    List.of(),
                    "Bằng chứng trực tiếp xác nhận "
                            + feature.vietnameseLabel() + ".",
                    "",
                    "MODERATE");
            evidenceByFeature.put(feature, evidenceId);
            rubricEvidence.computeIfAbsent(
                            parent, ignored -> new ArrayList<>())
                    .add(evidenceId);
            rubricFindings.computeIfAbsent(
                            parent, ignored -> new ArrayList<>())
                    .add(findingId);
        }
        for (WritingRubricCriterion feature : wholeAnswerFeatures) {
            String parent =
                    WritingDiagnosticContract
                            .expectedParentCriterionId(
                                    feature, "Q53");
            String findingId =
                    "F_Q53_PREMIUM_" + feature.id();
            List<String> requirementIds = switch (feature) {
                case W_LENGTH_REQUIREMENT_MET ->
                        List.of("Q53_LENGTH_200_300");
                case W_TASK_REQUIREMENT_COVERAGE ->
                        WritingTaskRequirementPolicy
                                .requirementsFor("Q53")
                                .stream()
                                .map(WritingTaskRequirementPolicy.Requirement
                                        ::requirementId)
                                .toList();
                case W_LOGICAL_ORGANIZATION -> List.of();
                default -> throw new IllegalArgumentException(
                        "Unexpected Q53 whole-answer strength");
            };
            addFinding(
                    root,
                    findingId,
                    "STRENGTH",
                    "KEEP",
                    feature.id(),
                    writingSubtype(feature),
                    parent,
                    null,
                    requirementIds,
                    "Bằng chứng toàn bài xác nhận "
                            + feature.vietnameseLabel() + ".",
                    "",
                    "MODERATE");
            if (parent != null) {
                rubricFindings.computeIfAbsent(
                                parent, ignored -> new ArrayList<>())
                        .add(findingId);
            }
        }
        String coverageEvidence = evidenceByFeature.get(
                WritingRubricCriterion.W_ACCURATE_DATA_DESCRIPTION);
        for (WritingTaskRequirementPolicy.Requirement requirement
                : WritingTaskRequirementPolicy.requirementsFor("Q53")) {
            ObjectNode row = WritingContractTestFixtures.coverage(
                    root, requirement.requirementId());
            row.put("status", "MET");
            if (requirement.evidenceRequired()) {
                replaceIds(
                        row,
                        "evidenceIds",
                        coverageEvidence);
            }
        }
        for (WritingScoringCriterion criterion
                : WritingScoringPolicy.rubricFor("Q53").criteria()) {
            ObjectNode row = rubric(
                    root, criterion.criterionId());
            row.put("score", criterion.maxScore());
            replaceIds(
                    row,
                    "evidenceIds",
                    rubricEvidence
                            .getOrDefault(
                                    criterion.criterionId(),
                                    List.of())
                            .toArray(String[]::new));
            replaceIds(
                    row,
                    "findingIds",
                    rubricFindings
                            .getOrDefault(
                                    criterion.criterionId(),
                                    List.of())
                            .toArray(String[]::new));
        }
        return root;
    }

    private static String q53StrengthEvidence(
            WritingRubricCriterion feature) {
        return switch (feature) {
            case W_ADVANCED_GRAMMAR_STRUCTURES ->
                    "때문이라고 볼 수 있다";
            case W_ACCURATE_SPELLING_SPACING ->
                    "2024년과 2026년";
            case W_FORMAL_REGISTER_CONSISTENCY ->
                    "비교한다";
            case W_FORMAL_VOCABULARY_USAGE ->
                    "교통수단 이용률";
            case W_TOPIC_SPECIFIC_EXPRESSIONS ->
                    "친환경 이동";
            case W_NATURAL_KOREAN_EXPRESSIONS ->
                    "같은 수준을 유지했다";
            case W_SENTENCE_PATTERN_VARIETY ->
                    "건강에 관심이 높아지고";
            case W_ACCURATE_DATA_DESCRIPTION ->
                    "45%에서 35%로 감소했고";
            case W_EFFECTIVE_TRANSITIONS -> "반면";
            default -> throw new IllegalArgumentException(
                    "Q53 text strength is not catalogued: "
                            + feature.id());
        };
    }

    private static String q54PremiumImprovementAnswer() {
        return Q54_MIXED_ANSWER
                .replace(
                        "학습 자료에 빠르게 접근하게 하며",
                        "학습 자료를 빠르게 만들며")
                .replace(
                        "여러 방식으로 확인하게 해 준다",
                        "여러 방식으로 확인이 되어진다")
                .replace(
                        "학생은 모르는 표현을 바로 검색하고",
                        "학생는 모르는 표현을 바로 검색하는")
                .replace(
                        "도구가 된다",
                        "도구가 돼요")
                .replace(
                        "그러나 화면에 오래 집중하면",
                        "그리고 그러나 화면에 오래 집중하면")
                .replace(
                        "정보를 그대로 복사하면 자신의 생각을 정리하는 "
                                + "힘이 약해질 수도 있다",
                        "정보를 그대로 복사하면 자신의 생각이 힘이 "
                                + "약해질 수도 있다")
                .replace(
                        "결국 디지털 기기를 교육에 사용해야 한다",
                        "결국 디지털 기기를 교육에 사용 해야 한다");
    }

    private ObjectNode q54PremiumImprovementEnvelope(
            String answer) {
        ObjectNode root = zeroEnvelope(mapper, "Q54", answer);
        addEvidence(
                root,
                "EV_Q54_PREMIUM_SCORE",
                answer,
                answer.substring(0, 1),
                0);
        List<WritingRubricCriterion> features =
                WritingRubricCriterion.activeForTask("Q54")
                        .stream()
                        .filter(feature -> feature.polarity()
                                == WritingRubricCriterion.Polarity
                                .NEEDS_IMPROVEMENT)
                        .toList();
        assertThat(features)
                .as("Q54 premium improvement catalogue")
                .hasSize(14);
        Map<String, List<String>> rubricEvidence =
                new java.util.LinkedHashMap<>();
        Map<String, List<String>> rubricFindings =
                new java.util.LinkedHashMap<>();
        for (int index = 0; index < features.size(); index++) {
            WritingRubricCriterion feature = features.get(index);
            String findingId =
                    "F_Q54_PREMIUM_" + feature.id();
            String exact = q54ImprovementEvidence(feature);
            String evidenceId = exact == null
                    ? null
                    : "EV_Q54_PREMIUM_" + (index + 1);
            if (evidenceId != null) {
                int offset = feature
                        == WritingRubricCriterion
                        .W_REPETITIVE_WORDS_EXPRESSIONS
                        ? occurrenceOffset(answer, exact, 4)
                        : uniqueOffset(answer, exact);
                addEvidence(
                        root,
                        evidenceId,
                        answer,
                        exact,
                        offset);
            }
            String parent =
                    WritingDiagnosticContract
                            .expectedParentCriterionId(
                                    feature, "Q54");
            assertThat(parent)
                    .as("Q54 %s parent", feature.id())
                    .isNotBlank();
            List<String> requirementIds =
                    q54ImprovementRequirements(feature);
            String operation = evidenceId == null
                    ? "MISSING"
                    : feature == WritingRubricCriterion
                    .W_REPETITIVE_WORDS_EXPRESSIONS
                    ? "REDUNDANT" : "REPLACE";
            ObjectNode finding = addFinding(
                    root,
                    findingId,
                    "IMPROVEMENT",
                    operation,
                    feature.id(),
                    writingSubtype(feature),
                    parent,
                    evidenceId,
                    requirementIds,
                    "Đoạn này cần điều chỉnh về "
                            + feature.vietnameseLabel() + ".",
                    "REPLACE".equals(operation)
                            ? "교사가 다듬은 정확한 표현" : "",
                    "MODERATE");
            if (feature == WritingRubricCriterion
                    .W_REPETITIVE_WORDS_EXPRESSIONS) {
                finding.put("frequency", 5);
            }
            rubricFindings.computeIfAbsent(
                            parent, ignored -> new ArrayList<>())
                    .add(findingId);
            if (evidenceId != null) {
                rubricEvidence.computeIfAbsent(
                                parent, ignored -> new ArrayList<>())
                        .add(evidenceId);
            }
        }
        WritingContractTestFixtures.coverage(
                        root, "Q54_LENGTH_600_700")
                .put("status", "MET");
        rubricEvidence.computeIfAbsent(
                        "W_CONTENT_TASK_ACHIEVEMENT",
                        ignored -> new ArrayList<>())
                .add("EV_Q54_PREMIUM_SCORE");
        for (WritingScoringCriterion criterion
                : WritingScoringPolicy.rubricFor("Q54").criteria()) {
            ObjectNode row = rubric(
                    root, criterion.criterionId());
            row.put("score", 1);
            replaceIds(
                    row,
                    "evidenceIds",
                    rubricEvidence
                            .getOrDefault(
                                    criterion.criterionId(),
                                    List.of())
                            .toArray(String[]::new));
            replaceIds(
                    row,
                    "findingIds",
                    rubricFindings
                            .getOrDefault(
                                    criterion.criterionId(),
                                    List.of())
                            .toArray(String[]::new));
        }
        WritingRubricCriterion rewriteFeature =
                WritingRubricCriterion.W_SPELLING_SPACING_ERRORS;
        int rewriteIndex = features.indexOf(rewriteFeature);
        ObjectNode upgrade = root.with("upgradedAnswer");
        upgrade.put("content", Q54_PERFECT_ANSWER);
        rewrite(
                upgrade,
                "F_Q54_PREMIUM_" + rewriteFeature.id(),
                "EV_Q54_PREMIUM_" + (rewriteIndex + 1),
                "사용해야",
                "Sửa cách chữ bằng một rewrite có span và finding xác minh.");
        return root;
    }

    private static String q54ImprovementEvidence(
            WritingRubricCriterion feature) {
        return switch (feature) {
            case W_TRANSITION_DEVICE_ISSUES -> "그리고 그러나";
            case W_VOCABULARY_ERRORS -> "자료를 빠르게 만들며";
            case W_GRAMMAR_ERRORS -> "바로 검색하는";
            case W_PARTICLE_ERRORS -> "학생는";
            case W_REPETITIVE_WORDS_EXPRESSIONS -> "디지털 기기";
            case W_AWKWARD_UNNATURAL_EXPRESSIONS ->
                    "확인이 되어진다";
            case W_SENTENCE_STRUCTURE_ISSUES ->
                    "자신의 생각이 힘이 약해질";
            case W_REGISTER_CONSISTENCY_ISSUES -> "도구가 돼요";
            case W_SPELLING_SPACING_ERRORS -> "사용 해야";
            case W_OFF_TOPIC_OR_WEAK_RELEVANCE,
                    W_INSUFFICIENT_IDEA_DEVELOPMENT,
                    W_UNSUPPORTED_CLAIM,
                    W_WEAK_PARAGRAPH_ORGANIZATION,
                    W_LOGICAL_FLOW_ISSUES -> null;
            default -> throw new IllegalArgumentException(
                    "Q54 improvement is not catalogued: "
                            + feature.id());
        };
    }

    private static List<String> q54ImprovementRequirements(
            WritingRubricCriterion feature) {
        return switch (feature) {
            case W_OFF_TOPIC_OR_WEAK_RELEVANCE ->
                    List.of("Q54_PROMPT_COVERAGE");
            case W_INSUFFICIENT_IDEA_DEVELOPMENT,
                    W_UNSUPPORTED_CLAIM ->
                    List.of("Q54_SUPPORT");
            case W_WEAK_PARAGRAPH_ORGANIZATION,
                    W_LOGICAL_FLOW_ISSUES ->
                    List.of("Q54_LOGICAL_DEVELOPMENT");
            default -> List.of();
        };
    }

    private ObjectNode q54UniqueStrengthEnvelope(
            String answer) {
        ObjectNode root = zeroEnvelope(mapper, "Q54", answer);
        applyRawScore(root, "Q54", answer, 50);
        List<WritingRubricCriterion> features = List.of(
                WritingRubricCriterion.W_CLEAR_THESIS_OR_MAIN_IDEA,
                WritingRubricCriterion.W_RELEVANT_EXAMPLES_OR_REASONS);
        List<String> findingIds = new ArrayList<>();
        for (WritingRubricCriterion feature : features) {
            String findingId =
                    "F_Q54_PREMIUM_" + feature.id();
            String parent =
                    WritingDiagnosticContract
                            .expectedParentCriterionId(
                                    feature, "Q54");
            List<String> requirementIds =
                    feature == WritingRubricCriterion
                            .W_CLEAR_THESIS_OR_MAIN_IDEA
                            ? List.of("Q54_POSITION")
                            : List.of("Q54_SUPPORT");
            addFinding(
                    root,
                    findingId,
                    "STRENGTH",
                    "KEEP",
                    feature.id(),
                    writingSubtype(feature),
                    parent,
                    null,
                    requirementIds,
                    "Bằng chứng toàn bài xác nhận "
                            + feature.vietnameseLabel() + ".",
                    "",
                    "MODERATE");
            findingIds.add(findingId);
        }
        replaceIds(
                rubric(root, "W_CONTENT_TASK_ACHIEVEMENT"),
                "findingIds",
                findingIds.toArray(String[]::new));
        return root;
    }

    private ObjectNode q53UniqueImprovementEnvelope(
            String answer) {
        ObjectNode root = zeroEnvelope(mapper, "Q53", answer);
        addEvidence(
                root,
                "EV_Q53_UNIQUE_SCORE",
                answer,
                answer.substring(0, 1),
                0);
        List<WritingRubricCriterion> features = List.of(
                WritingRubricCriterion.W_TASK_REQUIREMENT_MISSING,
                WritingRubricCriterion.W_Q53_DATA_FLOW_ISSUES);
        for (WritingRubricCriterion feature : features) {
            String parent =
                    WritingDiagnosticContract
                            .expectedParentCriterionId(
                                    feature, "Q53");
            String findingId =
                    "F_Q53_UNIQUE_" + feature.id();
            List<String> requirementIds =
                    feature == WritingRubricCriterion
                            .W_TASK_REQUIREMENT_MISSING
                            ? List.of("Q53_PLAUSIBLE_CAUSE")
                            : List.of();
            addFinding(
                    root,
                    findingId,
                    "IMPROVEMENT",
                    "MISSING",
                    feature.id(),
                    writingSubtype(feature),
                    parent,
                    null,
                    requirementIds,
                    "Fixture phụ xác nhận "
                            + feature.vietnameseLabel() + ".",
                    "",
                    "MODERATE");
            ObjectNode row = rubric(root, parent);
            List<String> existing = new ArrayList<>();
            row.path("findingIds").forEach(
                    id -> existing.add(id.asText()));
            existing.add(findingId);
            replaceIds(
                    row,
                    "findingIds",
                    existing.toArray(String[]::new));
        }
        WritingContractTestFixtures.coverage(
                        root, "Q53_LENGTH_200_300")
                .put("status", "MET");
        for (String criterionId : List.of(
                "W_CONTENT_TASK_ACHIEVEMENT",
                "W_ORGANIZATION_COHERENCE")) {
            ObjectNode row = rubric(root, criterionId);
            row.put("score", 1);
            replaceIds(
                    row,
                    "evidenceIds",
                    "EV_Q53_UNIQUE_SCORE");
        }
        return root;
    }

    private static void replaceEvidenceReference(
            ArrayNode rows,
            String oldId,
            String newId) {
        for (JsonNode row : rows) {
            JsonNode values = row.path("evidenceIds");
            if (!values.isArray()) {
                continue;
            }
            for (int index = 0; index < values.size(); index++) {
                if (oldId.equals(values.get(index).asText())) {
                    ((ArrayNode) values).set(
                            index,
                            TextNode.valueOf(newId));
                }
            }
        }
    }

    private ObjectNode clozePartialEnvelope(
            String taskType,
            String answer,
            boolean includeStrength) {
        ObjectNode envelope = zeroEnvelope(
                mapper, taskType, answer);
        addEvidence(
                envelope,
                "EV_CLOZE_IMPROVEMENT",
                answer,
                "어색",
                answer.indexOf("어색"));
        addFinding(
                envelope,
                "F_CLOZE_IMPROVEMENT",
                "IMPROVEMENT",
                "REPLACE",
                "W_CLOZE_GRAMMAR_COMPATIBILITY",
                "ENDINGS_CONJUGATION",
                "W_CLOZE_BLANK_1_GRAMMAR",
                "EV_CLOZE_IMPROVEMENT",
                List.of("CLOZE_BLANK_1_CONTEXT"),
                "Ô thứ nhất dùng kết thúc chưa tương thích.",
                "자연스럽습니다",
                "MODERATE");
        ObjectNode grammar = rubric(
                envelope, "W_CLOZE_BLANK_1_GRAMMAR");
        grammar.put("score", 1);
        replaceIds(
                grammar, "evidenceIds", "EV_CLOZE_IMPROVEMENT");
        replaceIds(
                grammar, "findingIds", "F_CLOZE_IMPROVEMENT");
        if (includeStrength) {
            addEvidence(
                    envelope,
                    "EV_CLOZE_STRENGTH",
                    answer,
                    "문맥",
                    answer.indexOf("문맥"));
            addFinding(
                    envelope,
                    "F_CLOZE_STRENGTH",
                    "STRENGTH",
                    "KEEP",
                    "W_CLOZE_CONTEXT_FIT",
                    "CONTENT_RELEVANCE",
                    "W_CLOZE_BLANK_1_CONTEXT",
                    "EV_CLOZE_STRENGTH",
                    List.of("CLOZE_BLANK_1_CONTEXT"),
                    "Ô thứ nhất vẫn bám đúng ngữ cảnh.",
                    "",
                    "MINOR");
            ObjectNode context = rubric(
                    envelope, "W_CLOZE_BLANK_1_CONTEXT");
            context.put("score", 2);
            replaceIds(
                    context, "evidenceIds", "EV_CLOZE_STRENGTH");
            replaceIds(
                    context, "findingIds", "F_CLOZE_STRENGTH");
        }
        return envelope;
    }

    private ObjectNode q53AtomicEnvelope(String answer) {
        ObjectNode root = zeroEnvelope(mapper, "Q53", answer);
        addEvidence(root, "EV_CAR", answer,
                "45%에서 35%로 감소했고",
                uniqueOffset(answer, "45%에서 35%로 감소했고"));
        addEvidence(root, "EV_TRANSIT", answer,
                "10%에서 5%로 줄었다",
                uniqueOffset(answer, "10%에서 5%로 줄었다"));
        addEvidence(root, "EV_BIKE", answer,
                "20%에서 35%로 크게 증가했으며",
                uniqueOffset(answer, "20%에서 35%로 크게 증가했으며"));
        addEvidence(root, "EV_CONTRAST", answer, "반면",
                uniqueOffset(answer, "반면"));
        addEvidence(root, "EV_WALKING", answer,
                "도보는 25%로 같았다",
                uniqueOffset(answer, "도보는 25%로 같았다"));
        addEvidence(root, "EV_CAUSE", answer,
                "때문이라고 볼 수 있다",
                uniqueOffset(answer, "때문이라고 볼 수 있다"));
        addEvidence(root, "EV_FOUR_MODES", answer,
                "전체적으로 자동차와 대중교통은 줄고 자전거는 늘었으며 "
                        + "도보는 같은 수준을 유지했다",
                uniqueOffset(
                        answer,
                        "전체적으로 자동차와 대중교통은 줄고 자전거는 늘었으며 "
                                + "도보는 같은 수준을 유지했다"));
        addFinding(root, "F_CAR", "STRENGTH", "KEEP",
                "W_ACCURATE_DATA_DESCRIPTION", "DATA_ACCURACY",
                "W_CONTENT_TASK_ACHIEVEMENT", "EV_CAR",
                List.of("Q53_DATA_2024"),
                "Mô tả xu hướng giảm của ô tô.", "", "MODERATE");
        addFinding(root, "F_TRANSIT", "STRENGTH", "KEEP",
                "W_ACCURATE_DATA_DESCRIPTION", "DATA_ACCURACY",
                "W_CONTENT_TASK_ACHIEVEMENT", "EV_TRANSIT",
                List.of("Q53_DATA_2026"),
                "Mô tả xu hướng giảm của phương tiện công cộng.",
                "", "MODERATE");
        addFinding(root, "F_BIKE", "STRENGTH", "KEEP",
                "W_ACCURATE_DATA_DESCRIPTION", "DATA_ACCURACY",
                "W_CONTENT_TASK_ACHIEVEMENT", "EV_BIKE",
                List.of("Q53_MAIN_CHANGES"),
                "Mô tả chính xác mức tăng của xe đạp.",
                "", "MODERATE");
        addFinding(root, "F_CONTRAST", "STRENGTH", "KEEP",
                "W_EFFECTIVE_TRANSITIONS", "TRANSITION_USE",
                "W_ORGANIZATION_COHERENCE", "EV_CONTRAST",
                List.of(),
                "Dùng dấu hiệu chuyển ý tương phản đúng chỗ.",
                "", "MINOR");
        addFinding(root, "F_Q53_WALKING", "IMPROVEMENT", "REPLACE",
                "W_AWKWARD_UNNATURAL_EXPRESSIONS", "NATURALNESS",
                "W_LANGUAGE_EXPRESSION", "EV_WALKING",
                List.of(),
                "Cụm này diễn đạt trạng thái không đổi chưa tự nhiên.",
                "도보 이용률은 25%로 동일하게 유지되었다",
                "MINOR");
        addFinding(root, "F_CAUSE", "STRENGTH", "KEEP",
                "W_NATURAL_KOREAN_EXPRESSIONS", "NATURALNESS",
                "W_LANGUAGE_EXPRESSION", "EV_CAUSE",
                List.of("Q53_PLAUSIBLE_CAUSE"),
                "Cấu trúc nêu nguyên nhân khả dĩ phù hợp.",
                "", "MODERATE");
        for (String requirement : List.of(
                "Q53_FOUR_TRANSPORT_MODES",
                "Q53_DATA_2024",
                "Q53_DATA_2026",
                "Q53_MAIN_CHANGES",
                "Q53_PLAUSIBLE_CAUSE")) {
            ObjectNode coverage =
                    WritingContractTestFixtures.coverage(
                            root, requirement);
            coverage.put("status", "MET");
            replaceIds(
                    coverage,
                    "evidenceIds",
                    switch (requirement) {
                        case "Q53_DATA_2024" -> "EV_CAR";
                        case "Q53_DATA_2026" -> "EV_TRANSIT";
                        case "Q53_MAIN_CHANGES" -> "EV_BIKE";
                        case "Q53_PLAUSIBLE_CAUSE" -> "EV_CAUSE";
                        default -> "EV_FOUR_MODES";
                    });
        }
        WritingContractTestFixtures.coverage(
                        root, "Q53_LENGTH_200_300")
                .put("status", "MET");
        ObjectNode content = rubric(
                root, "W_CONTENT_TASK_ACHIEVEMENT");
        content.put("score", 12);
        replaceIds(
                content,
                "evidenceIds",
                "EV_CAR", "EV_TRANSIT", "EV_BIKE",
                "EV_CAUSE", "EV_FOUR_MODES");
        replaceIds(content, "findingIds",
                "F_CAR", "F_TRANSIT", "F_BIKE");
        ObjectNode organization = rubric(
                root, "W_ORGANIZATION_COHERENCE");
        organization.put("score", 9);
        replaceIds(organization, "evidenceIds", "EV_CONTRAST");
        replaceIds(organization, "findingIds", "F_CONTRAST");
        ObjectNode language = rubric(
                root, "W_LANGUAGE_EXPRESSION");
        language.put("score", 7);
        replaceIds(
                language, "evidenceIds", "EV_WALKING", "EV_CAUSE");
        replaceIds(
                language,
                "findingIds",
                "F_Q53_WALKING",
                "F_CAUSE");
        ObjectNode upgrade = (ObjectNode) root.path("upgradedAnswer");
        upgrade.put(
                "content",
                answer.replace(
                        "도보는 25%로 같았다",
                        "도보 이용률은 25%로 동일하게 유지되었다"));
        ObjectNode rewrite = upgrade.withArray("rewrites").addObject();
        replaceIds(rewrite, "findingIds", "F_Q53_WALKING");
        rewrite.put("evidenceId", "EV_WALKING");
        rewrite.put(
                "replacementKo",
                "도보 이용률은 25%로 동일하게 유지되었다");
        rewrite.put(
                "reasonVi",
                "Diễn đạt chính xác trạng thái tỷ lệ không đổi.");
        return root;
    }

    private ObjectNode q54MixedEnvelope(String answer) {
        ObjectNode root = zeroEnvelope(mapper, "Q54", answer);
        String thesis =
                "나는 디지털 기기를 교육 현장에서 적절히 활용해야 한다고 생각한다";
        String benefit =
                "영상, 사전, 모의 자료를 빠르게 찾아 이해하기 어려운 내용을 "
                        + "여러 방식으로 확인하게 해 준다";
        String contrast = "그러나";
        String proposal = "이런 문제를 줄이려면";
        String example =
                "예를 들어 과학 수업에서 짧은 실험 영상을 본 뒤 학생들이 직접 "
                        + "결과를 예측하고 조별로 근거를 설명하게 할 수 있다";
        String repeated = "디지털 기기";
        String conclusion = "결국 디지털 기기를 교육에 사용해야 한다";

        addEvidence(
                root, "EV_Q54_THESIS", answer, thesis,
                uniqueOffset(answer, thesis));
        addEvidence(
                root, "EV_Q54_BENEFIT", answer, benefit,
                uniqueOffset(answer, benefit));
        addEvidence(
                root, "EV_Q54_CONTRAST", answer, contrast,
                uniqueOffset(answer, contrast));
        addEvidence(
                root, "EV_Q54_PROPOSAL", answer, proposal,
                uniqueOffset(answer, proposal));
        addEvidence(
                root, "EV_Q54_EXAMPLE", answer, example,
                uniqueOffset(answer, example));
        addEvidence(
                root, "EV_Q54_REPETITION", answer, repeated,
                occurrenceOffset(answer, repeated, 4));
        addEvidence(
                root, "EV_Q54_CONCLUSION", answer, conclusion,
                uniqueOffset(answer, conclusion));

        addFinding(
                root,
                "F_Q54_CONTRAST",
                "STRENGTH",
                "KEEP",
                "W_EFFECTIVE_TRANSITIONS",
                "TRANSITION_USE",
                "W_ORGANIZATION_COHERENCE",
                "EV_Q54_CONTRAST",
                List.of(),
                "Từ nối mở quan hệ nhượng bộ rõ ràng.",
                "",
                "MODERATE");
        addFinding(
                root,
                "F_Q54_PROPOSAL",
                "STRENGTH",
                "KEEP",
                "W_EFFECTIVE_TRANSITIONS",
                "TRANSITION_USE",
                "W_ORGANIZATION_COHERENCE",
                "EV_Q54_PROPOSAL",
                List.of(),
                "Dấu hiệu chuyển sang giải pháp đúng chức năng.",
                "",
                "MODERATE");
        addFinding(
                root,
                "F_Q54_SUPPORT_GAP",
                "IMPROVEMENT",
                "MISSING",
                "W_INSUFFICIENT_IDEA_DEVELOPMENT",
                "CONTENT_DEVELOPMENT",
                "W_CONTENT_TASK_ACHIEVEMENT",
                null,
                List.of("Q54_SUPPORT"),
                "Ví dụ có liên quan nhưng chưa nêu kết quả quan sát được.",
                "",
                "MODERATE");
        addFinding(
                root,
                "F_Q54_LOGIC_GAP",
                "IMPROVEMENT",
                "MISSING",
                "W_LOGICAL_FLOW_ISSUES",
                "LOGICAL_RELATION",
                "W_ORGANIZATION_COHERENCE",
                null,
                List.of("Q54_LOGICAL_DEVELOPMENT"),
                "Đoạn kết chưa nối lại đầy đủ lợi ích, rủi ro và giải pháp.",
                "",
                "MINOR");
        ObjectNode repetition = addFinding(
                root,
                "F_Q54_REPETITION",
                "IMPROVEMENT",
                "REDUNDANT",
                "W_REPETITIVE_WORDS_EXPRESSIONS",
                "REPETITION",
                "W_LANGUAGE_EXPRESSION",
                "EV_Q54_REPETITION",
                List.of(),
                "Danh từ chủ đề lặp lại năm lần; có thể thay bằng từ quy chiếu.",
                "",
                "MINOR");
        repetition.put("frequency", 5);
        addFinding(
                root,
                "F_Q54_CONCLUSION",
                "IMPROVEMENT",
                "REPLACE",
                "W_AWKWARD_UNNATURAL_EXPRESSIONS",
                "NATURALNESS",
                "W_LANGUAGE_EXPRESSION",
                "EV_Q54_CONCLUSION",
                List.of(),
                "Kết luận còn chung chung và chưa tổng hợp điều kiện sử dụng.",
                "결국 교육 목표와 안전 기준을 먼저 세울 때 디지털 도구의 "
                        + "장점을 살릴 수 있다",
                "MODERATE");

        coverage(root, "Q54_POSITION", "MET", "EV_Q54_THESIS");
        coverage(root, "Q54_PROMPT_COVERAGE", "MET", "EV_Q54_BENEFIT");
        coverage(root, "Q54_SUPPORT", "PARTIAL", "EV_Q54_EXAMPLE");
        coverage(
                root,
                "Q54_LOGICAL_DEVELOPMENT",
                "PARTIAL",
                "EV_Q54_PROPOSAL");
        WritingContractTestFixtures.coverage(
                        root, "Q54_LENGTH_600_700")
                .put("status", "MET");

        ObjectNode content = rubric(
                root, "W_CONTENT_TASK_ACHIEVEMENT");
        content.put("score", 16);
        replaceIds(
                content,
                "evidenceIds",
                "EV_Q54_THESIS",
                "EV_Q54_BENEFIT",
                "EV_Q54_EXAMPLE");
        replaceIds(content, "findingIds", "F_Q54_SUPPORT_GAP");
        ObjectNode organization = rubric(
                root, "W_ORGANIZATION_COHERENCE");
        organization.put("score", 13);
        replaceIds(
                organization,
                "evidenceIds",
                "EV_Q54_CONTRAST",
                "EV_Q54_PROPOSAL");
        replaceIds(
                organization,
                "findingIds",
                "F_Q54_CONTRAST",
                "F_Q54_PROPOSAL",
                "F_Q54_LOGIC_GAP");
        ObjectNode language = rubric(
                root, "W_LANGUAGE_EXPRESSION");
        language.put("score", 12);
        replaceIds(
                language,
                "evidenceIds",
                "EV_Q54_REPETITION",
                "EV_Q54_CONCLUSION");
        replaceIds(
                language,
                "findingIds",
                "F_Q54_REPETITION",
                "F_Q54_CONCLUSION");

        ObjectNode upgrade =
                (ObjectNode) root.path("upgradedAnswer");
        upgrade.put(
                "content",
                answer.replace(
                                "디지털 기기는 잘 쓰면",
                                "이 도구는 잘 쓰면")
                        .replace(
                                conclusion,
                                "결국 교육 목표와 안전 기준을 먼저 세울 때 "
                                        + "디지털 도구의 장점을 살릴 수 있다"));
        rewrite(
                upgrade,
                "F_Q54_REPETITION",
                "EV_Q54_REPETITION",
                "이 도구",
                "Dùng từ quy chiếu để giảm lặp danh từ chủ đề.");
        rewrite(
                upgrade,
                "F_Q54_CONCLUSION",
                "EV_Q54_CONCLUSION",
                "결국 교육 목표와 안전 기준을 먼저 세울 때 디지털 도구의 "
                        + "장점을 살릴 수 있다",
                "Kết luận tổng hợp lại điều kiện áp dụng.");
        return root;
    }

    private ObjectNode essayPerfectEvidenceRichEnvelope(
            String taskType,
            String answer) {
        ObjectNode root = zeroEnvelope(mapper, taskType, answer);
        String contentText;
        String organizationText;
        String languageText;
        if ("Q53".equals(taskType)) {
            contentText =
                    "전체적으로 자동차와 대중교통은 줄고 자전거는 늘었으며 "
                            + "도보 이용률은 같은 수준을 유지했다";
            organizationText = "반면";
            languageText = "동일하게 유지되었다";
        } else {
            contentText =
                    "나는 디지털 기기를 교육 현장에서 적절히 활용해야 한다고 생각한다";
            organizationText = "이런 문제를 줄이려면";
            languageText = "학습 목표를 먼저 정하고 필요한 순간에만";
        }
        addEvidence(
                root, "EV_PERFECT_CONTENT", answer, contentText,
                uniqueOffset(answer, contentText));
        addEvidence(
                root, "EV_PERFECT_ORGANIZATION", answer, organizationText,
                uniqueOffset(answer, organizationText));
        addEvidence(
                root, "EV_PERFECT_LANGUAGE", answer, languageText,
                uniqueOffset(answer, languageText));
        addFinding(
                root,
                "F_PERFECT_CONTENT",
                "STRENGTH",
                "KEEP",
                "Q53".equals(taskType)
                        ? "W_ACCURATE_DATA_DESCRIPTION"
                        : "W_TOPIC_SPECIFIC_EXPRESSIONS",
                "Q53".equals(taskType)
                        ? "DATA_ACCURACY"
                        : "DOMAIN_SINO_KOREAN",
                "Q53".equals(taskType)
                        ? "W_CONTENT_TASK_ACHIEVEMENT"
                        : "W_LANGUAGE_EXPRESSION",
                "EV_PERFECT_CONTENT",
                List.of(),
                "Bằng chứng chính xác, rõ và bám nhiệm vụ.",
                "",
                "MODERATE");
        addFinding(
                root,
                "F_PERFECT_ORGANIZATION",
                "STRENGTH",
                "KEEP",
                "W_EFFECTIVE_TRANSITIONS",
                "TRANSITION_USE",
                "W_ORGANIZATION_COHERENCE",
                "EV_PERFECT_ORGANIZATION",
                List.of(),
                "Chuyển ý đúng quan hệ lập luận.",
                "",
                "MODERATE");
        addFinding(
                root,
                "F_PERFECT_LANGUAGE",
                "STRENGTH",
                "KEEP",
                "W_NATURAL_KOREAN_EXPRESSIONS",
                "NATURALNESS",
                "W_LANGUAGE_EXPRESSION",
                "EV_PERFECT_LANGUAGE",
                List.of(),
                "Diễn đạt tự nhiên và chính xác.",
                "",
                "MODERATE");

        for (WritingTaskRequirementPolicy.Requirement requirement
                : WritingTaskRequirementPolicy.requirementsFor(taskType)) {
            ObjectNode row = WritingContractTestFixtures.coverage(
                    root, requirement.requirementId());
            row.put("status", "MET");
            if (requirement.evidenceRequired()) {
                replaceIds(row, "evidenceIds", "EV_PERFECT_CONTENT");
            }
        }
        for (WritingScoringCriterion criterion
                : WritingScoringPolicy.rubricFor(taskType).criteria()) {
            ObjectNode row = rubric(root, criterion.criterionId());
            row.put("score", criterion.maxScore());
            String evidenceId;
            String findingId;
            if ("W_ORGANIZATION_COHERENCE".equals(
                    criterion.criterionId())) {
                evidenceId = "EV_PERFECT_ORGANIZATION";
                findingId = "F_PERFECT_ORGANIZATION";
            } else if ("W_LANGUAGE_EXPRESSION".equals(
                    criterion.criterionId())) {
                evidenceId = "EV_PERFECT_LANGUAGE";
                findingId = "F_PERFECT_LANGUAGE";
            } else {
                evidenceId = "EV_PERFECT_CONTENT";
                findingId = "F_PERFECT_CONTENT";
            }
            replaceIds(row, "evidenceIds", evidenceId);
            if ("Q54".equals(taskType)
                    && "W_LANGUAGE_EXPRESSION".equals(
                    criterion.criterionId())) {
                replaceIds(
                        row,
                        "findingIds",
                        "F_PERFECT_CONTENT",
                        "F_PERFECT_LANGUAGE");
            } else if (!("Q54".equals(taskType)
                    && "W_CONTENT_TASK_ACHIEVEMENT".equals(
                    criterion.criterionId()))) {
                replaceIds(row, "findingIds", findingId);
            }
        }
        return root;
    }

    private static void coverage(
            ObjectNode root,
            String requirementId,
            String status,
            String... evidenceIds) {
        ObjectNode row = WritingContractTestFixtures.coverage(
                root, requirementId);
        row.put("status", status);
        replaceIds(row, "evidenceIds", evidenceIds);
    }

    private static void rewrite(
            ObjectNode upgrade,
            String findingId,
            String evidenceId,
            String replacement,
            String reason) {
        ObjectNode row = upgrade.withArray("rewrites").addObject();
        replaceIds(row, "findingIds", findingId);
        row.put("evidenceId", evidenceId);
        row.put("replacementKo", replacement);
        row.put("reasonVi", reason);
    }

    private static int uniqueOffset(
            String source,
            String exact) {
        int start = source.indexOf(exact);
        if (start < 0
                || source.indexOf(exact, start + 1) >= 0) {
            throw new IllegalArgumentException(
                    "Fixture span must occur exactly once: " + exact);
        }
        return start;
    }

    private static int occurrenceOffset(
            String source,
            String exact,
            int occurrence) {
        int offset = -1;
        for (int index = 0; index < occurrence; index++) {
            offset = source.indexOf(exact, offset + 1);
            if (offset < 0) {
                throw new IllegalArgumentException(
                        "Fixture occurrence is missing: " + exact);
            }
        }
        return offset;
    }

    private void upsertWritingQuestion(
            Connection connection,
            long questionVersionId,
            long questionId,
            int questionNo,
            String taskType,
            String prompt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_question_versions (
                  id, published_version_id, section_version_id,
                  group_version_id, question_id, question_no,
                  question_type, prompt, options_json,
                  question_content_json, answer_key, answer_spec_json,
                  explanation,
                  explanation_strategy_registry_version,
                  explanation_strategy_code,
                  explanation_strategy_version,
                  points, display_order, writing_task_type
                ) VALUES (
                  ?, 3, 3, NULL, ?, ?, 'ESSAY', ?, NULL, ?, NULL,
                  ?, NULL, NULL, NULL, NULL, ?, ?, ?
                )
                ON DUPLICATE KEY UPDATE
                  question_id=VALUES(question_id),
                  question_no=VALUES(question_no),
                  question_type='ESSAY',
                  prompt=VALUES(prompt),
                  options_json=NULL,
                  question_content_json=VALUES(question_content_json),
                  answer_key=NULL,
                  answer_spec_json=VALUES(answer_spec_json),
                  explanation=NULL,
                  points=VALUES(points),
                  display_order=VALUES(display_order),
                  writing_task_type=VALUES(writing_task_type)
                """)) {
            statement.setLong(1, questionVersionId);
            statement.setLong(2, questionId);
            statement.setInt(3, questionNo);
            statement.setString(4, prompt);
            boolean cloze = "Q51".equals(taskType)
                    || "Q52".equals(taskType);
            statement.setString(
                    5,
                    cloze
                            ? premiumWritingQuestionContent(taskType)
                            : null);
            statement.setString(
                    6,
                    cloze
                            ? premiumWritingAnswerSpec(taskType)
                            : null);
            int points = switch (taskType) {
                case "Q51", "Q52" -> 10;
                case "Q53" -> 30;
                case "Q54" -> 50;
                default -> throw new IllegalArgumentException();
            };
            statement.setInt(7, points);
            statement.setInt(8, questionNo - 51);
            statement.setString(9, taskType);
            statement.executeUpdate();
        }
    }

    private String premiumWritingQuestionContent(
            String taskType) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "question-content-v3");
        root.putArray("options");
        root.putArray("blanks");
        root.put("languageTag", "ko");
        ObjectNode response = root.putObject("writingResponse");
        response.put(
                "responseSchemaVersion",
                WritingBlankContract.RESPONSE_SCHEMA_VERSION);
        response.put(
                "responseMode",
                WritingBlankContract.RESPONSE_MODE);
        response.put("taskType", taskType);
        ArrayNode blanks = response.putArray("blanks");
        for (int blankIndex = 1; blankIndex <= 2; blankIndex++) {
            blanks.addObject()
                    .put(
                            "blankId",
                            taskType.toLowerCase()
                                    + "-b" + blankIndex)
                    .put("ordinal", blankIndex)
                    .put(
                            "context",
                            blankIndex == 1
                                    ? "Ngữ cảnh ô thứ nhất"
                                    : "Ngữ cảnh ô thứ hai");
        }
        return mapper.writeValueAsString(root);
    }

    private String premiumWritingAnswerSpec(
            String taskType) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "answer-spec-v1");
        root.put("questionType", "ESSAY");
        root.putArray("correctOptionIds");
        root.putArray("blanks");
        root.put("scoringPolicyCode", "PROFILE_BASED");
        ObjectNode authority =
                root.putObject("writingBlankAuthority");
        authority.put(
                "contractVersion",
                WritingBlankContract.AUTHORITY_SCHEMA_VERSION);
        authority.put("taskType", taskType);
        authority.put(
                "normalization",
                WritingBlankContract.NORMALIZATION);
        authority.put(
                "whitespacePolicy",
                WritingBlankContract.WHITESPACE_POLICY);
        ArrayNode blanks = authority.putArray("blanks");
        for (int blankIndex = 1; blankIndex <= 2; blankIndex++) {
            ObjectNode blank = blanks.addObject();
            blank.put(
                    "blankId",
                    taskType.toLowerCase() + "-b" + blankIndex);
            blank.put("ordinal", blankIndex);
            blank.putArray("acceptedAnswers")
                    .addObject()
                    .put(
                            "text",
                            "모범 답안 " + blankIndex)
                    .put("equivalence", "EXACT")
                    .putArray("evidenceIds");
        }
        return mapper.writeValueAsString(root);
    }

    private void upsertWritingAttempt(
            Connection connection,
            long attemptId,
            ObjectNode answers,
            ObjectNode feedback,
            WritingCase fixtureCase) throws Exception {
        String status = switch (fixtureCase) {
            case PENDING, UNAVAILABLE -> "SUBMITTED";
            default -> "GRADED";
        };
        String analysisStatus = switch (fixtureCase) {
            case PENDING -> "QUEUED";
            case UNAVAILABLE -> "UNAVAILABLE";
            default -> "SUCCEEDED";
        };
        BigDecimal percentage = switch (fixtureCase) {
            case MIXED -> new BigDecimal("75.00");
            case NO_DIAGNOSTIC -> BigDecimal.ZERO;
            case PARTIAL -> new BigDecimal("72.00");
            case FULL -> new BigDecimal("100.00");
            case PENDING, UNAVAILABLE -> null;
        };
        String completedAt =
                fixtureCase == WritingCase.PENDING ? null : FIXTURE_TIME;
        String analysisEngine =
                fixtureCase == WritingCase.PENDING
                        ? null : "pre14-dev-test-fixture";
        String analysisErrorCode =
                fixtureCase == WritingCase.UNAVAILABLE
                        ? "DEV_TEST_PROVIDER_DISABLED" : null;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_attempts (
                  id, user_id, set_id, test_id, skill, section_id,
                  status, analysis_status, score, total_points,
                  score_unit, earned_points, score_percentage,
                  answers_json, ai_feedback_json,
                  analysis_requested_at, analysis_completed_at,
                  analysis_engine, analysis_error_code,
                  started_at, deadline_at, submitted_at, discarded_at,
                  created_at, updated_at, lock_version,
                  published_version_id, set_version_id,
                  test_version_id, section_version_id,
                  version_compatibility_status,
                  version_compatibility_note
                ) VALUES (
                  ?, 4, 3, 3, 'WRITING', 3,
                  ?, ?, ?, 100.00, 'PERCENTAGE',
                  ?, ?, ?, ?, ?, ?, ?, ?,
                  ?, '2026-07-30 07:00:00', ?, NULL,
                  ?, ?, 0, 3, 3, 3, 3, NULL,
                  'Pre-14 DEV/TEST deterministic UI acceptance seed'
                )
                ON DUPLICATE KEY UPDATE
                  status=VALUES(status),
                  analysis_status=VALUES(analysis_status),
                  score=VALUES(score), total_points=100.00,
                  score_unit='PERCENTAGE',
                  earned_points=VALUES(earned_points),
                  score_percentage=VALUES(score_percentage),
                  answers_json=VALUES(answers_json),
                  ai_feedback_json=VALUES(ai_feedback_json),
                  analysis_requested_at=VALUES(analysis_requested_at),
                  analysis_completed_at=VALUES(analysis_completed_at),
                  analysis_engine=VALUES(analysis_engine),
                  analysis_error_code=VALUES(analysis_error_code),
                  submitted_at=VALUES(submitted_at),
                  discarded_at=NULL, updated_at=VALUES(updated_at),
                  lock_version=0,
                  version_compatibility_note=VALUES(
                    version_compatibility_note)
                """)) {
            statement.setLong(1, attemptId);
            statement.setString(2, status);
            statement.setString(3, analysisStatus);
            statement.setBigDecimal(4, percentage);
            statement.setBigDecimal(5, percentage);
            statement.setBigDecimal(6, percentage);
            statement.setString(7, mapper.writeValueAsString(answers));
            statement.setString(8, mapper.writeValueAsString(feedback));
            statement.setString(9, FIXTURE_TIME);
            statement.setString(10, completedAt);
            statement.setString(11, analysisEngine);
            statement.setString(12, analysisErrorCode);
            statement.setString(13, FIXTURE_TIME);
            statement.setString(14, FIXTURE_TIME);
            statement.setString(15, FIXTURE_TIME);
            statement.setString(16, FIXTURE_TIME);
            statement.executeUpdate();
        }
    }

    private void upsertPremiumWritingAttempt(
            Connection connection,
            long attemptId,
            ObjectNode answers,
            ObjectNode feedback,
            BigDecimal percentage) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_attempts (
                  id, user_id, set_id, test_id, skill, section_id,
                  status, analysis_status, score, total_points,
                  score_unit, earned_points, score_percentage,
                  answers_json, ai_feedback_json,
                  analysis_requested_at, analysis_completed_at,
                  analysis_engine, analysis_error_code,
                  started_at, deadline_at, submitted_at, discarded_at,
                  created_at, updated_at, lock_version,
                  published_version_id, set_version_id,
                  test_version_id, section_version_id,
                  version_compatibility_status,
                  version_compatibility_note
                ) VALUES (
                  ?, 4, 3, 3, 'WRITING', 3,
                  'GRADED', 'SUCCEEDED', ?, 100.00, 'PERCENTAGE',
                  ?, ?, ?, ?, ?, ?, 'pre14-dev-test-fixture', NULL,
                  ?, '2026-07-30 07:00:00', ?, NULL,
                  ?, ?, 0, 3, 3, 3, 3, NULL,
                  'Pre-14 premium all-chip UI acceptance seed'
                )
                ON DUPLICATE KEY UPDATE
                  status='GRADED',
                  analysis_status='SUCCEEDED',
                  score=VALUES(score),
                  total_points=100.00,
                  score_unit='PERCENTAGE',
                  earned_points=VALUES(earned_points),
                  score_percentage=VALUES(score_percentage),
                  answers_json=VALUES(answers_json),
                  ai_feedback_json=VALUES(ai_feedback_json),
                  analysis_requested_at=VALUES(analysis_requested_at),
                  analysis_completed_at=VALUES(analysis_completed_at),
                  analysis_engine='pre14-dev-test-fixture',
                  analysis_error_code=NULL,
                  submitted_at=VALUES(submitted_at),
                  discarded_at=NULL,
                  updated_at=VALUES(updated_at),
                  lock_version=0,
                  version_compatibility_note=VALUES(
                    version_compatibility_note)
                """)) {
            statement.setLong(1, attemptId);
            statement.setBigDecimal(2, percentage);
            statement.setBigDecimal(3, percentage);
            statement.setBigDecimal(4, percentage);
            statement.setString(
                    5, mapper.writeValueAsString(answers));
            statement.setString(
                    6, mapper.writeValueAsString(feedback));
            statement.setString(7, FIXTURE_TIME);
            statement.setString(8, FIXTURE_TIME);
            statement.setString(9, FIXTURE_TIME);
            statement.setString(10, FIXTURE_TIME);
            statement.setString(11, FIXTURE_TIME);
            statement.setString(12, FIXTURE_TIME);
            statement.executeUpdate();
        }
    }

    private void seedSpeakingScenarios(
            Connection connection) throws Exception {
        String transcript =
                "저는 한국 드라마를 좋아해서 한국어를 공부합니다. "
                        + "앞으로 친구들과 자연스럽게 이야기하고 싶습니다.";
        upsertSpeakingQuestion(
                connection,
                14405L,
                14405L,
                2,
                "관심사와 앞으로의 목표를 말하십시오.");
        ObjectNode answers = mapper.createObjectNode();
        answers.put("5", transcript);
        answers.put("14405", transcript);

        List<SpeakingEvaluationTestFixtures.FindingFixture> findings =
                List.of(
                        speakingFinding(
                                "SF-VOCAB-STRENGTH",
                                "SEV-VOCAB-STRENGTH",
                                SpeakingRubricCriterion
                                        .VOCABULARY_EXPRESSIONS,
                                "S_VOCAB_TOPIC_WORDS",
                                "한국 드라마",
                                transcript.indexOf("한국 드라마"),
                                "strength",
                                "KEEP",
                                "VOCABULARY",
                                "LOW",
                                "Dùng đúng từ vựng chủ đề sở thích.",
                                ""),
                        speakingFinding(
                                "SF-GRAMMAR-IMPROVEMENT",
                                "SEV-GRAMMAR-IMPROVEMENT",
                                SpeakingRubricCriterion
                                        .GRAMMAR_SENTENCE_CONTROL,
                                "S_GRAMMAR_CONNECTORS",
                                "이야기하고",
                                transcript.indexOf("이야기하고"),
                                "needs_improvement",
                                "REPLACE",
                                "GRAMMAR",
                                "MEDIUM",
                                "Có thể nối mục tiêu bằng cấu trúc rõ hơn.",
                                "이야기할 수 있기를 바랍니다"));
        SpeakingEvaluationResult mixed =
                SpeakingEvaluationTestFixtures.currentResultWithFindings(
                        mapper,
                        transcript,
                        new BigDecimal("15"),
                        findings);
        SpeakingEvaluationResult noDiagnostic =
                SpeakingEvaluationTestFixtures.currentResult(
                        mapper,
                        transcript,
                        new BigDecimal("20"),
                        provider -> {
                            provider.putArray("transcript_annotations");
                            provider.withArray("rubric_scores").forEach(row -> {
                                if (row instanceof ObjectNode rubric) {
                                    rubric.set("score", rubric.get("max_score"));
                                }
                            });
                        });
        assertThat(noDiagnostic.transcriptAnnotations())
                .as("No-diagnostic scenario must keep rubric rows without inventing a strength chip")
                .isEmpty();
        assertThat(noDiagnostic.rubricScores())
                .hasSize(6);
        ObjectNode feedback = mapper.createObjectNode();
        feedback.put("_contract", "speaking_ai_v1");
        ObjectNode byQuestion = feedback.putObject(
                "speaking_feedback_by_question");
        byQuestion.set("5", mapper.valueToTree(mixed));
        byQuestion.set("14405", mapper.valueToTree(noDiagnostic));
        upsertSpeakingAttempt(
                connection, 14401L, answers, feedback);
        seedPremiumSpeakingAttempt(
                connection, 14701L, "strength", "KEEP",
                new BigDecimal("20"));
        seedPremiumSpeakingAttempt(
                connection, 14702L, "needs_improvement", "REPLACE",
                new BigDecimal("8"));
    }

    private void seedPremiumSpeakingAttempt(
            Connection connection,
            long attemptId,
            String annotationType,
            String operation,
            BigDecimal contentScore) throws Exception {
        String transcript = """
                관심사와 목표를 말씀드리겠습니다. 첫째, 한국 드라마를 통해 공부했습니다. \
                그래서 친구들과 한국어 모임에서 이야기하고 싶습니다. 시간이 있으면 연습합니다. \
                정말 꾸준히 익혀 가다 보면 정말 도움이 되다라는 점을 알 수 있습니다. \
                그 결과 자신감이 생겼고, 마지막으로 앞으로의 계획을 설명하겠습니다.
                """.strip();
        boolean strength = "strength".equals(annotationType);
        SpeakingEvaluationResult result = premiumSpeakingResult(
                attemptId,
                14405L,
                transcript,
                annotationType,
                operation,
                contentScore);
        SpeakingEvaluationResult companion = premiumSpeakingResult(
                attemptId,
                5L,
                transcript,
                strength ? "needs_improvement" : "strength",
                strength ? "REPLACE" : "KEEP",
                strength ? new BigDecimal("8") : new BigDecimal("20"));
        assertThat(result.transcriptAnnotations())
                .as("Premium Speaking attempt %s must retain every primary transcript chip",
                        attemptId)
                .hasSize(PREMIUM_SPEAKING_CHIPS.size() + 1);
        assertThat(companion.transcriptAnnotations())
                .as("Premium Speaking attempt %s must retain the opposite-polarity companion",
                        attemptId)
                .hasSize(PREMIUM_SPEAKING_CHIPS.size() + 1);
        assertThat(result.rubricScores())
                .as("Acoustic rows remain visible but unscored")
                .hasSize(6);

        ObjectNode answers = mapper.createObjectNode();
        answers.put("5", transcript);
        answers.put("14405", transcript);
        ObjectNode feedback = mapper.createObjectNode();
        feedback.put("_contract", "speaking_ai_v1");
        ObjectNode byQuestion = feedback.putObject(
                "speaking_feedback_by_question");
        ObjectNode teacherSamples = feedback.putObject(
                "speaking_teacher_samples_by_question");
        for (String questionId : List.of("5", "14405")) {
            ObjectNode teacherSample = teacherSamples.putObject(questionId);
            teacherSample.put(
                    "contractVersion",
                    "ksh-speaking-teacher-sample-v1");
            teacherSample.put("source", "TEACHER_AUTHORED");
            teacherSample.put("authorRole", "LECTURER");
            teacherSample.put(
                    "fixtureId",
                    "PRE14-SPEAKING-" + attemptId + "-Q" + questionId);
            teacherSample.put(
                    "content",
                    "저는 한국어를 꾸준히 연습한 경험과 앞으로의 목표를 "
                            + "차례대로 설명하겠습니다.");
        }
        byQuestion.set("14405", mapper.valueToTree(result));
        byQuestion.set("5", mapper.valueToTree(companion));
        upsertSpeakingAttempt(
                connection, attemptId, answers, feedback);
    }

    private SpeakingEvaluationResult premiumSpeakingResult(
            long attemptId,
            long questionId,
            String transcript,
            String annotationType,
            String operation,
            BigDecimal contentScore) {
        boolean strength = "strength".equals(annotationType);
        List<SpeakingEvaluationTestFixtures.FindingFixture> findings =
                new ArrayList<>();
        for (int index = 0;
             index < PREMIUM_SPEAKING_CHIPS.size();
             index++) {
            SpeakingChipDescriptor descriptor =
                    PREMIUM_SPEAKING_CHIPS.get(index);
            String suffix = strength ? "STRENGTH" : "IMPROVEMENT";
            findings.add(speakingFinding(
                    "SP-" + attemptId + "-Q" + questionId + "-" + (index + 1)
                            + "-" + suffix,
                    "SPE-" + attemptId + "-Q" + questionId + "-" + (index + 1),
                    descriptor.criterion(),
                    descriptor.subcriterionId(),
                    descriptor.exactText(),
                    transcript.indexOf(descriptor.exactText()),
                    annotationType,
                    operation,
                    descriptor.category(),
                    strength ? "LOW" : "MEDIUM",
                    strength
                            ? "Bằng chứng bản chép lời xác nhận "
                            + descriptor.labelVi() + "."
                            : "Cần điều chỉnh "
                            + descriptor.labelVi()
                            + " trong bản chép lời.",
                    strength
                            ? ""
                            : "표현을 더 정확하고 자연스럽게 고쳐 보세요."));
            if ("S_VOCAB_REPETITION_CONTROL".equals(
                    descriptor.subcriterionId())) {
                int repeatedOffset = transcript.indexOf(
                        descriptor.exactText(),
                        transcript.indexOf(descriptor.exactText())
                                + descriptor.exactText().length());
                findings.add(speakingFinding(
                        "SP-" + attemptId + "-Q" + questionId + "-" + (index + 1)
                                + "B-" + suffix,
                        "SPE-" + attemptId + "-Q" + questionId + "-"
                                + (index + 1) + "B",
                        descriptor.criterion(),
                        descriptor.subcriterionId(),
                        descriptor.exactText(),
                        repeatedOffset,
                        annotationType,
                        operation,
                        descriptor.category(),
                        strength ? "LOW" : "MEDIUM",
                        strength
                                ? "Lần xuất hiện thứ hai tiếp tục xác nhận "
                                + descriptor.labelVi() + "."
                                : "Lần xuất hiện thứ hai cần điều chỉnh "
                                + descriptor.labelVi() + ".",
                        strength
                                ? ""
                                : "같은 표현의 반복을 줄여 보세요."));
            }
        }
        SpeakingEvaluationResult result =
                SpeakingEvaluationTestFixtures.currentResultWithFindings(
                        mapper, transcript, contentScore, findings);
        return result;
    }

    private void seedLecturerAuthoringScenarios(
            Connection connection) throws Exception {
        upsertLecturerDraft(
                connection,
                14501L,
                "READING",
                "R1",
                "Đọc — chiến lược giải thích typed",
                "READING_PASSAGE",
                "민수는 도서관에서 한국어를 공부합니다.",
                null);
        upsertLecturerDraft(
                connection,
                14502L,
                "LISTENING",
                "L1",
                "Nghe — chiến lược giải thích typed",
                "LISTENING_AUDIO",
                null,
                "여자는 도서관에서 발표 자료를 찾습니다.");
    }

    private void upsertLecturerDraft(
            Connection connection,
            long draftId,
            String skill,
            String lessonCode,
            String title,
            String stimulusType,
            String passageText,
            String transcriptText) throws Exception {
        String draftJson = lecturerDraftJson(
                skill,
                lessonCode,
                title,
                stimulusType,
                passageText,
                transcriptText);
        PracticeDraftValidator.ValidationResult validation =
                new PracticeDraftValidator(mapper).validate(draftJson);
        assertThat(validation.hasBlocking())
                .as("Lecturer UI acceptance draft must remain structurally valid: %s",
                        validation.messages())
                .isFalse();

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_drafts (
                  id, title, description, scope, class_id, status,
                  owner_id, draft_json, version, created_at, updated_at,
                  published_set_id, creation_method, draft_schema_version
                ) VALUES (
                  ?, ?, 'Pre-14 DEV/TEST-only lecturer acceptance scenario',
                  'GLOBAL', NULL, 'DRAFT', 2, ?, 0, ?, ?,
                  NULL, 'MANUAL', 'practice-draft-v3'
                )
                ON DUPLICATE KEY UPDATE
                  title=VALUES(title),
                  description=VALUES(description),
                  scope='GLOBAL',
                  class_id=NULL,
                  status='DRAFT',
                  owner_id=2,
                  draft_json=VALUES(draft_json),
                  version=0,
                  updated_at=VALUES(updated_at),
                  published_set_id=NULL,
                  creation_method='MANUAL',
                  draft_schema_version='practice-draft-v3'
                """)) {
            statement.setLong(1, draftId);
            statement.setString(2, title);
            statement.setString(3, draftJson);
            statement.setString(4, FIXTURE_TIME);
            statement.setString(5, FIXTURE_TIME);
            statement.executeUpdate();
        }
    }

    private String lecturerDraftJson(
            String skill,
            String lessonCode,
            String title,
            String stimulusType,
            String passageText,
            String transcriptText) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode document = root.putObject("document");
        document.put("detectedCategory", "KSH_PRACTICE");
        document.put("creationMethod", "MANUAL");
        root.putArray("tests")
                .addObject()
                .put("clientId", "pre14-" + skill.toLowerCase())
                .put("testNo", 1)
                .put("title", title);
        ObjectNode section = root.putArray("sections").addObject();
        section.put("clientId", "pre14-section-" + skill.toLowerCase());
        section.put("title", title);
        section.put("skill", skill);
        section.put("testNo", 1);
        section.put("testClientId", "pre14-" + skill.toLowerCase());
        section.put("lessonCode", lessonCode);
        section.put("durationMinutes", 40);
        if ("LISTENING".equals(skill)) {
            ObjectNode delivery = section.putObject("sectionDelivery");
            delivery.put("schemaVersion", "practice-section-delivery-v1");
            delivery.putObject("listeningDelivery")
                    .put(
                            "checkAudioReference",
                            "/practice/materials/1/content");
        }
        ObjectNode group = section.putArray("groups").addObject();
        group.put("clientId", "pre14-group-" + skill.toLowerCase());
        group.put("groupCode", lessonCode + ".1");
        group.put("label", "Nguồn và mười một strategy typed v2");
        group.put("instruction", "Đối chiếu nguồn immutable trước khi chọn đáp án.");
        group.put("questionFrom", 1);
        group.put("questionTo", 12);
        group.put("stimulusKind",
                "READING".equals(skill) ? "PASSAGE" : "TRANSCRIPT");
        group.put("passageText",
                "READING".equals(skill) ? passageText : transcriptText);
        group.put("transcriptText",
                transcriptText == null ? "" : transcriptText);
        if ("LISTENING".equals(skill)) {
            group.put("audioUrl", "/practice/materials/1/content");
        }
        ObjectNode stimulus = group.putObject("stimulus");
        stimulus.put("schemaVersion", "practice-stimulus-v1");
        stimulus.put("type", stimulusType);
        stimulus.put("instruction", group.path("instruction").asText());
        if (passageText == null) {
            stimulus.putNull("passageText");
        } else {
            stimulus.put("passageText", passageText);
        }
        if (transcriptText == null) {
            stimulus.putNull("transcriptText");
        } else {
            stimulus.put("transcriptText", transcriptText);
        }
        stimulus.put(
                "mediaReference",
                "LISTENING".equals(skill)
                        ? "/practice/materials/1/content" : null);
        stimulus.putNull("imageReference");
        ObjectNode provenance = stimulus.putObject("provenance");
        provenance.put("source", "MANUAL");
        provenance.put("approved", true);
        provenance.putArray("sourceRegionIds");

        ArrayNode questions = group.putArray("questions");
        for (int index = 0; index < LECTURER_STRATEGIES.size(); index++) {
            StrategyFixture strategy = LECTURER_STRATEGIES.get(index);
            CanonicalQuestionType type = strategy.questionType();
            ObjectNode question = questions.addObject();
            question.put(
                    "clientId",
                    "pre14-" + skill.toLowerCase() + "-q-" + (index + 1));
            question.put("questionNo", index + 1);
            question.put("questionType", type.name());
            question.put(
                    "prompt",
                    type == CanonicalQuestionType.FILL_BLANK
                            ? ("READING".equals(skill)
                            ? "민수는 {{blank:blank_1}}에서 공부합니다."
                            : "여자는 {{blank:blank_1}}에서 발표 자료를 찾습니다.")
                            : type == CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN
                            ? ("READING".equals(skill)
                            ? "민수는 도서관에서 공부합니다."
                            : "여자는 도서관에서 발표 자료를 찾습니다.")
                            : type == CanonicalQuestionType.MULTIPLE_ANSWER
                            ? "자료를 찾을 수 있는 두 곳을 모두 고르십시오."
                            : type == CanonicalQuestionType.MATCHING
                            ? "다음 문장 1–4와 알맞은 정보 A–H를 연결하십시오."
                            : ("READING".equals(skill)
                            ? "민수는 어디에서 공부합니까?"
                            : "여자는 어디에서 자료를 찾습니까?"));
            question.put("points", 1);
            question.put("explanationVi",
                    "Giảng viên đã chọn một strategy typed cho version này.");
            QuestionContent typedContent = content(type, skill);
            AnswerSpec typedAnswer = answerSpec(
                    type, strategy.code(), skill);
            question.set("questionContent", mapper.valueToTree(typedContent));
            question.set("answerSpec", mapper.valueToTree(typedAnswer));
            if (type == CanonicalQuestionType.SINGLE_CHOICE
                    || type == CanonicalQuestionType.MULTIPLE_ANSWER
                    || type == CanonicalQuestionType.MATCHING) {
                ArrayNode options = question.putArray("options");
                typedContent.options().forEach(option -> options.addObject()
                        .put("id", option.id())
                        .put("text", option.text()));
                if (type == CanonicalQuestionType.SINGLE_CHOICE) {
                    question.putObject("answer")
                            .put("type", "SINGLE")
                            .put("value", "1");
                } else if (type == CanonicalQuestionType.MULTIPLE_ANSWER) {
                    question.putObject("answer")
                            .put("type", "MULTIPLE")
                            .put("value", "1,4");
                } else {
                    ArrayNode targets = question.putArray("matchingTargets");
                    for (int targetIndex = 0;
                         targetIndex < typedContent.blanks().size();
                         targetIndex++) {
                        QuestionContent.Blank target =
                                typedContent.blanks().get(targetIndex);
                        targets.addObject()
                                .put("id", target.id())
                                .put("prompt", target.prompt())
                                .put(
                                        "candidateOptionId",
                                        typedAnswer.blanks().get(targetIndex)
                                                .acceptedValues().get(0));
                    }
                    question.putObject("answer")
                            .put("type", "MATCHING")
                            .put("value", "");
                }
            } else if (type == CanonicalQuestionType.TRUE_FALSE_NOT_GIVEN) {
                question.putObject("answer").put(
                        "value",
                        "NOT_GIVEN_BOUNDARY".equals(strategy.code())
                                ? "NOT_GIVEN"
                                : "TRUE");
            } else if (type == CanonicalQuestionType.FILL_BLANK) {
                ObjectNode blank = question.putArray("fillBlanks")
                        .addObject();
                blank.put("id", "blank_1");
                blank.put("prompt", "Địa điểm");
                blank.putArray("acceptedValues").add("도서관");
                question.putObject("answer").put("value", "도서관");
            } else {
                throw new IllegalArgumentException(
                        "Unsupported objective authoring fixture type: " + type);
            }
            ObjectNode strategyNode =
                    question.putObject("explanationStrategy");
            strategyNode.put(
                    "registryVersion",
                    ObjectiveExplanationStrategyRegistry
                            .CURRENT_REGISTRY_VERSION);
            strategyNode.put("strategyCode", strategy.code());
            strategyNode.put(
                    "strategyVersion",
                    ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION);
        }
        return mapper.writeValueAsString(root);
    }

    private static SpeakingEvaluationTestFixtures.FindingFixture
            speakingFinding(
            String findingId,
            String evidenceId,
            SpeakingRubricCriterion criterion,
            String subcriterionId,
            String exactText,
            int startOffset,
            String annotationType,
            String operation,
            String category,
            String severity,
            String explanationVi,
            String suggestionKo) {
        return new SpeakingEvaluationTestFixtures.FindingFixture(
                findingId,
                evidenceId,
                criterion,
                subcriterionId,
                exactText,
                startOffset,
                annotationType,
                operation,
                category,
                severity,
                new BigDecimal("0.91"),
                explanationVi,
                suggestionKo);
    }

    private static void upsertSpeakingQuestion(
            Connection connection,
            long questionVersionId,
            long questionId,
            int questionNo,
            String prompt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_question_versions (
                  id, published_version_id, section_version_id,
                  group_version_id, question_id, question_no,
                  question_type, prompt, options_json,
                  question_content_json, answer_key, answer_spec_json,
                  explanation,
                  explanation_strategy_registry_version,
                  explanation_strategy_code,
                  explanation_strategy_version,
                  points, display_order, writing_task_type
                ) VALUES (
                  ?, 4, 4, NULL, ?, ?, 'SPEAKING', ?, NULL, NULL, NULL,
                  NULL, NULL, NULL, NULL, NULL, 100.00, ?, NULL
                )
                ON DUPLICATE KEY UPDATE
                  question_id=VALUES(question_id),
                  question_no=VALUES(question_no),
                  question_type='SPEAKING',
                  prompt=VALUES(prompt),
                  display_order=VALUES(display_order)
                """)) {
            statement.setLong(1, questionVersionId);
            statement.setLong(2, questionId);
            statement.setInt(3, questionNo);
            statement.setString(4, prompt);
            statement.setInt(5, questionNo - 1);
            statement.executeUpdate();
        }
    }

    private void upsertSpeakingAttempt(
            Connection connection,
            long attemptId,
            ObjectNode answers,
            ObjectNode feedback) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO practice_attempts (
                  id, user_id, set_id, test_id, skill, section_id,
                  status, analysis_status, score, total_points,
                  score_unit, earned_points, score_percentage,
                  answers_json, ai_feedback_json,
                  analysis_requested_at, analysis_completed_at,
                  analysis_engine, analysis_error_code,
                  started_at, deadline_at, submitted_at, discarded_at,
                  created_at, updated_at, lock_version,
                  published_version_id, set_version_id,
                  test_version_id, section_version_id,
                  version_compatibility_status,
                  version_compatibility_note
                ) VALUES (
                  ?, 4, 4, 4, 'SPEAKING', 4,
                  'GRADED', 'SUCCEEDED', NULL, NULL, NULL, NULL, NULL,
                  ?, ?, ?, ?, 'pre14-dev-test-fixture', NULL,
                  ?, '2026-07-30 07:00:00', ?, NULL,
                  ?, ?, 0, 4, 4, 4, 4, NULL,
                  'Pre-14 transcript-only UI acceptance seed'
                )
                ON DUPLICATE KEY UPDATE
                  status='GRADED', analysis_status='SUCCEEDED',
                  score=NULL, total_points=NULL, score_unit=NULL,
                  earned_points=NULL, score_percentage=NULL,
                  answers_json=VALUES(answers_json),
                  ai_feedback_json=VALUES(ai_feedback_json),
                  analysis_requested_at=VALUES(analysis_requested_at),
                  analysis_completed_at=VALUES(analysis_completed_at),
                  analysis_engine=VALUES(analysis_engine),
                  analysis_error_code=NULL,
                  submitted_at=VALUES(submitted_at),
                  discarded_at=NULL, updated_at=VALUES(updated_at),
                  lock_version=0,
                  version_compatibility_note=VALUES(
                    version_compatibility_note)
                """)) {
            statement.setLong(1, attemptId);
            statement.setString(2, mapper.writeValueAsString(answers));
            statement.setString(3, mapper.writeValueAsString(feedback));
            statement.setString(4, FIXTURE_TIME);
            statement.setString(5, FIXTURE_TIME);
            statement.setString(6, FIXTURE_TIME);
            statement.setString(7, FIXTURE_TIME);
            statement.setString(8, FIXTURE_TIME);
            statement.setString(9, FIXTURE_TIME);
            statement.executeUpdate();
        }
    }

    private static void verifySeed(
            Connection connection) throws Exception {
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_question_versions "
                        + "WHERE id BETWEEN 14101 AND 14114 "
                        + "OR id BETWEEN 14201 AND 14214"))
                .isEqualTo(28);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM question_explanation_artifacts "
                        + "WHERE provider_model='pre14-dev-test-fixture' "
                        + "AND status='READY'"))
                .isEqualTo(28);
        assertThat(count(
                connection,
                "SELECT COUNT(DISTINCT explanation_strategy_code) "
                        + "FROM practice_question_versions "
                        + "WHERE id BETWEEN 14101 AND 14114 "
                        + "OR id BETWEEN 14201 AND 14214"))
                .isEqualTo(11);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_question_versions "
                        + "WHERE (section_version_id=141 "
                        + "AND id BETWEEN 14101 AND 14114) "
                        + "OR (section_version_id=142 "
                        + "AND id BETWEEN 14201 AND 14214)"))
                .isEqualTo(28);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_question_versions "
                        + "WHERE section_version_id IN (141,142) "
                        + "AND NOT ((id BETWEEN 14101 AND 14114) "
                        + "OR (id BETWEEN 14201 AND 14214))"))
                .isZero();
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE (id=14100 AND published_version_id=141 "
                        + "AND section_id=1 "
                        + "AND set_version_id=141 "
                        + "AND test_version_id=141 "
                        + "AND section_version_id=141) "
                        + "OR (id=14200 AND published_version_id=142 "
                        + "AND section_id=2 "
                        + "AND set_version_id=142 "
                        + "AND test_version_id=142 "
                        + "AND section_version_id=142)"))
                .isEqualTo(2);
        assertThat(count(
                connection,
                "SELECT COUNT(*) "
                        + "FROM practice_question_versions q "
                        + "JOIN question_version_explanation_bindings b "
                        + "ON b.question_version_id=q.id "
                        + "AND b.explanation_language='vi' "
                        + "AND b.binding_status='ACTIVE' "
                        + "JOIN question_explanation_artifacts a "
                        + "ON a.id=b.artifact_id "
                        + "WHERE ((q.id BETWEEN 14101 AND 14114) "
                        + "OR (q.id BETWEEN 14201 AND 14214)) "
                        + "AND a.status='READY' "
                        + "AND ((q.question_type='FILL_BLANK' "
                        + "AND JSON_CONTAINS_PATH(a.explanation_json,'one',"
                        + "'$.explanation.strategyBlock.blankExplanations')) "
                        + "OR (q.question_type='TRUE_FALSE_NOT_GIVEN' "
                        + "AND JSON_CONTAINS_PATH(a.explanation_json,'all',"
                        + "'$.explanation.strategyBlock.claim',"
                        + "'$.explanation.strategyBlock.whyTrue',"
                        + "'$.explanation.strategyBlock.whyFalse',"
                        + "'$.explanation.strategyBlock.whyNotGiven',"
                        + "'$.explanation.strategyBlock.missingInformation'))"
                        + ")"))
                .isEqualTo(12);
        assertThat(count(
                connection,
                "SELECT COUNT(*) "
                        + "FROM practice_question_versions q "
                        + "JOIN question_version_explanation_bindings b "
                        + "ON b.question_version_id=q.id "
                        + "AND b.explanation_language='vi' "
                        + "AND b.binding_status='ACTIVE' "
                        + "JOIN question_explanation_artifacts a "
                        + "ON a.id=b.artifact_id "
                        + "WHERE q.id IN (14113,14213) "
                        + "AND q.question_type='MATCHING' "
                        + "AND JSON_LENGTH(JSON_EXTRACT(a.explanation_json,"
                        + "'$.explanation.strategyBlock.targetExplanations'))=4"))
                .isEqualTo(2);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_question_versions "
                        + "WHERE id IN (14114,14214) "
                        + "AND question_type='MULTIPLE_ANSWER' "
                        + "AND JSON_LENGTH(JSON_EXTRACT(answer_spec_json,"
                        + "'$.correctOptionIds'))=2"))
                .isEqualTo(2);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_question_versions "
                        + "WHERE id IN (14351,14352,4,14354) "
                        + "AND question_type='ESSAY'"))
                .isEqualTo(4);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE id BETWEEN 14301 AND 14304 "
                        + "AND analysis_status='SUCCEEDED'"))
                .isEqualTo(4);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE id=14305 AND status='SUBMITTED' "
                        + "AND analysis_status='QUEUED' "
                        + "AND score IS NULL "
                        + "AND score_percentage IS NULL"))
                .isEqualTo(1);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE id=14306 AND status='SUBMITTED' "
                        + "AND analysis_status='UNAVAILABLE' "
                        + "AND score IS NULL "
                        + "AND score_percentage IS NULL "
                        + "AND analysis_error_code="
                        + "'DEV_TEST_PROVIDER_DISABLED'"))
                .isEqualTo(1);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE id IN (14100,14200,14401,14701,14702,14800)"))
                .isEqualTo(6);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE id=14800 AND user_id=4 "
                        + "AND status='IN_PROGRESS' "
                        + "AND published_version_id=141 "
                        + "AND JSON_LENGTH(answers_json)=0"))
                .isEqualTo(1);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE id BETWEEN 14601 AND 14606 "
                        + "AND analysis_engine='pre14-dev-test-fixture'"))
                .isEqualTo(6);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE id=14604 "
                        + "AND JSON_LENGTH(JSON_EXTRACT(ai_feedback_json,"
                        + "'$.\"14351\".strengths'))=14 "
                        + "AND JSON_LENGTH(JSON_EXTRACT(ai_feedback_json,"
                        + "'$.\"14352\".needs_improvement'))=18 "
                        + "AND JSON_LENGTH(JSON_EXTRACT(ai_feedback_json,"
                        + "'$.\"4\".strengths'))=12 "
                        + "AND JSON_LENGTH(JSON_EXTRACT(ai_feedback_json,"
                        + "'$.\"14354\".needs_improvement'))=14"))
                .as("canonical premium Writing attempt exposes all four pair-wise task contracts")
                .isEqualTo(1);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_attempts "
                        + "WHERE id BETWEEN 14601 AND 14606 "
                        + "AND score_percentage <> CASE id "
                        + "WHEN 14601 THEN 100 WHEN 14602 THEN 90 "
                        + "WHEN 14603 THEN 100 WHEN 14604 THEN 43 "
                        + "WHEN 14605 THEN 100 WHEN 14606 THEN 72 END"))
                .isZero();
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_question_versions "
                        + "WHERE published_version_id=3 "
                        + "AND question_id IN (14351,14352) "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT("
                        + "question_content_json,"
                        + "'$.writingResponse.responseMode'))="
                        + "'STRUCTURED_BLANKS' "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT("
                        + "answer_spec_json,"
                        + "'$.writingBlankAuthority.contractVersion'))="
                        + "'writing-blank-authority.v1'"))
                .isEqualTo(2);
        assertThat(count(
                connection,
                "SELECT COUNT(*) FROM practice_drafts "
                        + "WHERE id IN (14501,14502) "
                        + "AND owner_id=2 AND status='DRAFT' "
                        + "AND draft_schema_version='practice-draft-v3'"))
                .isEqualTo(2);
    }

    private static long count(
            Connection connection,
            String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required when the seed is enabled");
        }
        return value;
    }

    private static String requiredEnvPresent(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException(
                    name + " is required when the seed is enabled");
        }
        return value;
    }

    private static String safeCatalog(String url) {
        Matcher matcher = SAFE_URL.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Seed JDBC URL must target localhost catalog "
                            + "ksh_test_pre14_ui_*");
        }
        return matcher.group(1);
    }

    private static String sha256(String material) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(material.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static StrategyFixture strategy(
            String code,
            CanonicalQuestionType questionType) {
        ObjectiveExplanationStrategyRegistry.requireSelection(
                questionType,
                ObjectiveExplanationStrategyRegistry
                        .CURRENT_REGISTRY_VERSION,
                code,
                ObjectiveExplanationStrategyRegistry.STRATEGY_VERSION);
        return new StrategyFixture(code, questionType);
    }

    private static SpeakingChipDescriptor speakingChip(
            SpeakingRubricCriterion criterion,
            String subcriterionId,
            String exactText,
            String category,
            String labelVi) {
        if (!criterion.ownsSubcriterion(subcriterionId)) {
            throw new IllegalArgumentException(
                    "Speaking chip must use a KSH-owned subcriterion: "
                            + subcriterionId);
        }
        return new SpeakingChipDescriptor(
                criterion, subcriterionId, exactText, category, labelVi);
    }

    private record ObjectiveScenario(
            String skill,
            String sourceMode,
            CanonicalQuestionType type,
            String strategyCode,
            String answerState,
            long questionVersionId,
            long questionId,
            long groupVersionId,
            int questionNo,
            String prompt,
            String source,
            String quote,
            String questionContentJson,
            String answerSpecJson,
            String rawAnswer) {
    }

    private record StrategyFixture(
            String code,
            CanonicalQuestionType questionType) {
    }

    private record SpeakingChipDescriptor(
            SpeakingRubricCriterion criterion,
            String subcriterionId,
            String exactText,
            String category,
            String labelVi) {
    }

    private record WritingQuestion(
            long questionId,
            String taskType) {
    }

    private enum WritingCase {
        MIXED,
        NO_DIAGNOSTIC,
        PARTIAL,
        FULL,
        PENDING,
        UNAVAILABLE
    }

    private enum PremiumWritingCase {
        Q51_ALL_STRENGTHS("Q51"),
        Q52_ALL_IMPROVEMENTS("Q52"),
        Q53_ALL_STRENGTHS("Q53"),
        Q54_ALL_IMPROVEMENTS("Q54"),
        Q54_UNIQUE_STRENGTHS("Q54"),
        Q53_UNIQUE_IMPROVEMENTS("Q53");

        private final String taskType;

        PremiumWritingCase(String taskType) {
            this.taskType = taskType;
        }

        String taskType() {
            return taskType;
        }
    }

    private static final String Q53_CANONICAL_ANSWER =
            "자료는 2024년과 2026년의 네 가지 교통수단 이용률을 비교한다. "
                    + "전체적으로 자동차와 대중교통은 줄고 자전거는 늘었으며 "
                    + "도보는 같은 수준을 유지했다. "
                    + "승용차 이용률은 45%에서 35%로 감소했고, "
                    + "대중교통은 10%에서 5%로 줄었다. "
                    + "자전거는 20%에서 35%로 크게 증가했으며, "
                    + "반면 도보는 25%로 같았다. "
                    + "이는 건강에 관심이 높아지고 친환경 이동을 선호하는 "
                    + "사람이 늘었기 때문이라고 볼 수 있다.";

    private static final String Q53_PERFECT_ANSWER =
            "자료는 2024년과 2026년의 네 가지 교통수단 이용률을 비교한다. "
                    + "전체적으로 자동차와 대중교통은 줄고 자전거는 늘었으며 "
                    + "도보 이용률은 같은 수준을 유지했다. "
                    + "승용차 이용률은 45%에서 35%로 감소했고, "
                    + "대중교통은 10%에서 5%로 줄었다. "
                    + "자전거는 20%에서 35%로 크게 증가했으며, "
                    + "반면 도보 이용률은 25%로 동일하게 유지되었다. "
                    + "이러한 변화는 건강과 환경을 중시하는 생활 방식이 "
                    + "확산되고 가까운 거리를 직접 이동하려는 사람이 늘었기 "
                    + "때문이라고 볼 수 있다.";

    private static final String Q54_MIXED_ANSWER =
            "나는 디지털 기기를 교육 현장에서 적절히 활용해야 한다고 생각한다. "
                    + "디지털 기기는 학습 자료에 빠르게 접근하게 하며, 영상, 사전, "
                    + "모의 자료를 빠르게 찾아 이해하기 어려운 내용을 여러 방식으로 "
                    + "확인하게 해 준다. 학생은 모르는 표현을 바로 검색하고 교사는 "
                    + "수준에 맞는 자료를 골라 수업 흐름을 조절할 수 있다. "
                    + "디지털 기기는 잘 쓰면 학생의 참여를 높이고 스스로 질문을 "
                    + "만들게 하는 도구가 된다. 그러나 화면에 오래 집중하면 눈이 "
                    + "피로해지고, 알림이나 게임 때문에 수업의 핵심에서 벗어날 "
                    + "위험도 있다. 정보를 그대로 복사하면 자신의 생각을 정리하는 "
                    + "힘이 약해질 수도 있다. 예를 들어 과학 수업에서 짧은 실험 "
                    + "영상을 본 뒤 학생들이 직접 결과를 예측하고 조별로 근거를 "
                    + "설명하게 할 수 있다. 이 활동은 디지털 기기에서 본 내용을 "
                    + "대화와 실험으로 연결한다. 이런 문제를 줄이려면 교사는 사용 "
                    + "시간과 목적을 미리 알리고, 학생은 필요한 자료만 열어야 한다. "
                    + "학교는 개인정보 보호 기준과 인용 방법도 함께 가르쳐야 한다. "
                    + "디지털 기기는 종이 교재를 완전히 대신하기보다 토론, 필기, "
                    + "실습과 균형을 이루어야 한다. 또한 수업이 끝난 뒤 학생이 어떤 "
                    + "자료를 왜 사용했는지 짧게 기록하게 하면 무분별한 사용을 줄일 "
                    + "수 있다. 교사는 결과만 확인하지 말고 탐색 과정과 근거의 질도 "
                    + "평가해야 한다. 결국 디지털 기기를 교육에 사용해야 한다.";

    private static final String Q54_PERFECT_ANSWER =
            "나는 디지털 기기를 교육 현장에서 적절히 활용해야 한다고 생각한다. "
                    + "디지털 도구는 학생이 필요한 자료를 빠르게 찾고, 글과 영상과 "
                    + "음성을 비교하며 어려운 개념을 여러 방식으로 이해하도록 돕는다. "
                    + "교사는 학습 기록을 바탕으로 학생별 부족한 부분을 확인하고 "
                    + "적절한 연습을 제시할 수 있다. 특히 과학 수업에서는 짧은 실험 "
                    + "영상을 본 뒤 학생들이 결과를 예측하고 조별로 근거를 설명하게 "
                    + "하면 관찰과 토론을 자연스럽게 연결할 수 있다. 반면 화면을 오래 "
                    + "보거나 알림에 주의를 빼앗기면 집중력이 낮아지고, 출처를 확인하지 "
                    + "않은 정보를 그대로 믿을 위험이 있다. 개인정보가 불필요하게 "
                    + "수집되거나 다른 사람의 글을 복사하는 문제도 생길 수 있다. "
                    + "이런 문제를 줄이려면 학교는 학습 목표를 먼저 정하고 필요한 순간에만 "
                    + "기기를 사용하도록 해야 한다. 교사는 사용 시간, 허용 앱, 자료의 "
                    + "출처와 인용 방법을 명확히 안내하고, 학생이 선택한 정보의 근거를 "
                    + "설명하게 해야 한다. 기기를 사용한 뒤에는 종이에 핵심을 정리하거나 "
                    + "친구와 토론하게 하여 화면 활동을 실제 사고와 표현으로 이어 가는 "
                    + "것도 중요하다. 분명한 기준 아래에서 활용한다면 디지털 도구는 "
                    + "교사를 대신하지 않으면서도 학생의 탐구와 협력을 넓혀 줄 수 있다. "
                    + "결국 교육의 목적과 안전 기준을 먼저 세우고 사람 중심의 수업 안에서 "
                    + "도구를 선택적으로 사용하는 것이 가장 바람직하다.";
}
