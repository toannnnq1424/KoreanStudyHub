package com.ksh.features.practice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PracticePhase13GPerformanceContractTest {

    private static final Path V56 = Path.of(
            "src/main/resources/db/migration/"
                    + "V56__practice_phase13g_catalog_progress_indexes.sql");
    private static final Path CATALOG_JS = Path.of(
            "src/main/resources/static/js/practice/practice-catalog.js");
    private static final Path CATALOG_TEMPLATE = Path.of(
            "src/main/resources/templates/practice/index.html");
    private static final Path ATTEMPT_REPOSITORY = Path.of(
            "src/main/java/com/ksh/features/practice/repository/"
                    + "PracticeAttemptRepository.java");
    private static final Path CATALOG_SERVICE = Path.of(
            "src/main/java/com/ksh/features/practice/service/"
                    + "PracticeCatalogService.java");

    @Test
    void forwardMigrationAddsIndexableActivityOrderAndOnlyAuditedStructures()
            throws Exception {
        assertThat(normalizedExecutableSqlStatements(V56)).containsExactly(
                "alter table practice_attempts add column activity_at datetime "
                        + "generated always as ( coalesce(submitted_at, updated_at, created_at) ) stored",
                "create index idx_practice_sets_catalog_page "
                        + "on practice_sets (status, is_deleted, created_at, id)",
                "create index idx_practice_sets_catalog_class_page "
                        + "on practice_sets (status, is_deleted, scope, class_id, created_at, id)",
                "create index idx_practice_sets_catalog_owner_page "
                        + "on practice_sets (status, is_deleted, created_by, created_at, id)",
                "create index idx_practice_questions_set_writing_task "
                        + "on practice_questions (set_id, writing_task_type)",
                "create index idx_practice_attempts_user_writing_activity "
                        + "on practice_attempts "
                        + "( user_id, skill, activity_at desc, id desc, status )",
                "create index idx_practice_attempts_user_set_activity "
                        + "on practice_attempts "
                        + "( user_id, set_id, activity_at desc, id desc, status )",
                "create index idx_practice_attempts_user_section_status "
                        + "on practice_attempts (user_id, section_id, status)");
    }

    @Test
    void repositoryReturnsPageBoundedCatalogEvidenceAndIndexOrderedWritingRows()
            throws Exception {
        String repository = Files.readString(ATTEMPT_REPOSITORY);
        String completedSectionsQuery = nativeQueryForMethod(
                repository, "CatalogCompletedSectionProjection",
                "findCatalogCompletedSections");
        String stateCandidateQuery = nativeQueryForMethod(
                repository, "CatalogAttemptStateProjection",
                "findCatalogAttemptStateCandidates");
        String writingQuery = nativeQueryForMethod(
                repository, "PracticeAttempt",
                "findProgressWritingAttempts");

        assertThat(completedSectionsQuery).contains(
                "select distinct",
                "a.section_id in (:sectionids)",
                "a.status in ('submitted', 'graded')");
        assertThat(stateCandidateQuery).contains(
                "row_number() over",
                "partition by candidate.set_id",
                "where ranked.candidate_row = 1");
        assertThat(writingQuery).contains(
                "a.skill = 'writing'",
                "order by a.activity_at desc, a.id desc");
        assertThat(Files.readString(CATALOG_SERVICE)).doesNotContain(
                "findByUserIdAndSetIdInAndStatusNotOrderByCreatedAtDescIdDesc",
                "findCoherentAttemptIdentityIds(");
    }

    @Test
    void learnerCatalogUsesRealServerPagesAndNeverAppendsAnUnboundedDom()
            throws Exception {
        String script = Files.readString(CATALOG_JS);
        String template = Files.readString(CATALOG_TEMPLATE);

        assertThat(template).contains(
                "catalog.previousBatch()",
                "catalog.nextBatch()",
                "catalog.firstItemNumber()",
                "catalog.lastItemNumber()",
                "rel=\"prev\"",
                "rel=\"next\"");
        assertThat(script).doesNotContain(
                "IntersectionObserver",
                "grid.appendChild",
                "fetch(",
                "DOMParser");
    }

    private List<String> normalizedExecutableSqlStatements(Path path)
            throws Exception {
        String withoutLineComments = Files.readAllLines(path).stream()
                .map(line -> {
                    int comment = line.indexOf("--");
                    return comment >= 0 ? line.substring(0, comment) : line;
                })
                .reduce("", (left, right) -> left + " " + right);
        return Pattern.compile(";")
                .splitAsStream(withoutLineComments)
                .map(this::normalizedSql)
                .filter(statement -> !statement.isBlank())
                .toList();
    }

    private String nativeQueryForMethod(
            String repository,
            String projectionType,
            String methodName
    ) {
        Pattern methodQuery = Pattern.compile(
                "@Query\\(value\\s*=\\s*\"\"\"(?<sql>.*?)\"\"\"\\s*,"
                        + "\\s*nativeQuery\\s*=\\s*true\\)\\s*"
                        + "List<" + Pattern.quote(projectionType) + ">\\s+"
                        + Pattern.quote(methodName) + "\\s*\\(",
                Pattern.DOTALL);
        Matcher matcher = methodQuery.matcher(repository);
        assertThat(matcher.find())
                .as("compiled repository method %s owns one native query",
                        methodName)
                .isTrue();
        return normalizedSql(matcher.group("sql"));
    }

    private String normalizedSql(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
