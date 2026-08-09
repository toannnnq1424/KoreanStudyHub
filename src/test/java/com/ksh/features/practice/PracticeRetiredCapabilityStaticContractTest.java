package com.ksh.features.practice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeRetiredCapabilityStaticContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void collaborationAndDarkReviewerRuntimeAreFullyRetired() throws Exception {
        List<String> retiredJava = List.of(
                "entities/PracticeAuthoringCollaboration.java",
                "features/practice/repository/PracticeAuthoringCollaborationRepository.java",
                "features/practice/governance/PracticeCollaborationService.java",
                "features/practice/ai/speaking/acoustic/DirectAudioDarkObservationCoordinator.java",
                "features/practice/ai/speaking/acoustic/DirectAudioDarkObservationJdbcStore.java",
                "features/practice/ai/speaking/acoustic/DirectAudioDarkObservationService.java",
                "features/practice/controller/DirectAudioReviewerInspectionController.java",
                "features/practice/controller/DirectAudioReviewerPageController.java",
                "features/practice/controller/DirectAudioReviewerPlaybackController.java",
                "features/practice/service/DirectAudioReviewerAccessAudit.java",
                "features/practice/service/DirectAudioReviewerAccessAuditRetention.java",
                "features/practice/service/DirectAudioReviewerAccessAuditRetentionWorker.java",
                "features/practice/service/DirectAudioReviewerPlaybackService.java",
                "features/practice/service/DirectAudioReviewerPlaybackStore.java");
        assertThat(retiredJava).allSatisfy(relative ->
                assertThat(Files.exists(ROOT.resolve("src/main/java/com/ksh/" + relative)))
                        .as(relative).isFalse());

        assertThat(ROOT.resolve(
                "src/main/resources/templates/practice/direct-audio-reviewer.html"))
                .doesNotExist();
        assertThat(ROOT.resolve(
                "src/main/resources/static/css/direct-audio-reviewer.css"))
                .doesNotExist();

        String runtime = runtimeSources();
        assertThat(runtime).doesNotContain(
                "PracticeAuthoringCollaboration",
                "PracticeCollaborationService",
                "practice_authoring_collaborations",
                "DirectAudioDarkObservation",
                "practice_speaking_direct_audio_dark_observations",
                "practice_speaking_audio_reviewer_access_events",
                "/direct-audio/review/");
    }

    @Test
    void forwardMigrationDropsOnlyTheApprovedPracticeObjects() throws Exception {
        String migration = Files.readString(ROOT.resolve(
                "src/main/resources/db/migration/"
                        + "V114__retire_practice_collaboration_and_dark_audio_review.sql"));
        assertThat(migration).contains(
                "DROP TABLE IF EXISTS practice_authoring_collaborations",
                "DROP TABLE IF EXISTS practice_speaking_direct_audio_dark_observations",
                "DROP TABLE IF EXISTS practice_speaking_audio_reviewer_access_events",
                "DROP COLUMN owner_locked",
                "DROP COLUMN locked_by",
                "DROP COLUMN locked_at",
                "p.feature_key = 'practice.lock'")
                .doesNotContain(
                        "DROP TABLE IF EXISTS practice_speaking_media",
                        "DROP TABLE IF EXISTS practice_speaking_audio_consent_events",
                        "DROP TABLE IF EXISTS practice_speaking_audio_reviewer_grants",
                        "DROP TABLE IF EXISTS practice_speaking_audio_grant_manager_events",
                        "DROP TABLE IF EXISTS practice_edit_logs",
                        "DROP TABLE IF EXISTS practice_published_versions");
    }

    @Test
    void globalCatalogAndSpeakingAudioAiPipelineRemainAvailable() throws Exception {
        String catalog = read(
                "src/main/java/com/ksh/features/practice/service/PracticeCatalogService.java");
        String repository = read(
                "src/main/java/com/ksh/features/practice/repository/PracticeSetRepository.java");
        assertThat(catalog).contains(
                "findPublishedGlobalCatalog",
                "PracticeSet.STATUS_PUBLISHED",
                "PracticeSet.SCOPE_GLOBAL");
        assertThat(repository).contains("findPublishedGlobalCatalog");

        assertThat(ROOT.resolve(
                "src/main/java/com/ksh/features/practice/ai/speaking/"
                        + "DirectAudioSpeakingEvaluationService.java"))
                .exists();
        assertThat(ROOT.resolve(
                "src/main/java/com/ksh/features/practice/ai/speaking/"
                        + "DirectAudioSpeakingEvaluationPort.java"))
                .exists();
        assertThat(ROOT.resolve(
                "src/main/java/com/ksh/features/practice/ai/speaking/enterprise/"
                        + "GeminiEnterpriseDirectAudioEvaluationAdapter.java"))
                .exists();

        String mediaRoutes = read(
                "src/main/java/com/ksh/features/practice/web/PracticeMediaRoutes.java");
        assertThat(mediaRoutes).contains(
                "SPEAKING_MEDIA",
                "SPEAKING_MEDIA_CONTENT")
                .doesNotContain("DIRECT_AUDIO_REVIEW");

        String purposes = read(
                "src/main/java/com/ksh/features/practice/ai/controlplane/PracticeAiPurpose.java");
        assertThat(purposes).contains("PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION");
    }

    private static String runtimeSources() throws Exception {
        StringBuilder source = new StringBuilder();
        for (Path root : List.of(
                ROOT.resolve("src/main/java"),
                ROOT.resolve("src/main/resources/templates"),
                ROOT.resolve("src/main/resources/static"))) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(PracticeRetiredCapabilityStaticContractTest::isTextSource)
                        .toList()) {
                    source.append(Files.readString(path));
                }
            }
        }
        source.append(read("src/main/resources/application.properties"));
        return source.toString();
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }

    private static boolean isTextSource(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".java")
                || name.endsWith(".html")
                || name.endsWith(".css")
                || name.endsWith(".js")
                || name.endsWith(".json")
                || name.endsWith(".xml")
                || name.endsWith(".properties");
    }
}
