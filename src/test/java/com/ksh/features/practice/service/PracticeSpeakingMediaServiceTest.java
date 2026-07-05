package com.ksh.features.practice.service;

import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSpeakingMedia;
import com.ksh.entities.PracticeSpeakingMediaStatus;
import com.ksh.entities.PracticeSpeakingStorageProvider;
import com.ksh.entities.PracticeTest;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.repository.PracticeAttemptRepository;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSectionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSpeakingMediaRepository;
import com.ksh.features.practice.repository.PracticeTestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PracticeSpeakingMediaServiceTest {

    @Autowired
    private PracticeSpeakingMediaService service;

    @Autowired
    private PracticeSpeakingMediaRepository mediaRepository;

    @Autowired
    private PracticeSetRepository setRepository;

    @Autowired
    private PracticeTestRepository testRepository;

    @Autowired
    private PracticeSectionRepository sectionRepository;

    @Autowired
    private PracticeQuestionGroupRepository groupRepository;

    @Autowired
    private PracticeQuestionRepository questionRepository;

    @Autowired
    private PracticeAttemptRepository attemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanupSyntheticSpeakingMedia() {
        jdbcTemplate.update("DELETE FROM practice_speaking_media WHERE storage_key LIKE 'learner-speaking/%'");
    }

    @Test
    void migrationCreatesExpectedTableColumnsAndIndexes() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_speaking_media'
                """, String.class);

        assertThat(columns).contains(
                "id",
                "attempt_id",
                "question_id",
                "storage_provider",
                "storage_key",
                "mime_type",
                "container",
                "codec",
                "byte_size",
                "duration_ms",
                "content_hash",
                "status",
                "lock_version",
                "created_at",
                "updated_at",
                "deleted_at");
        assertThat(columns).doesNotContain("user_id", "original_filename", "public_url", "audio_url");

        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'practice_speaking_media'
                """, String.class);

        assertThat(indexes).contains(
                "PRIMARY",
                "uk_psm_storage",
                "idx_psm_attempt_question_status",
                "idx_psm_attempt_status");
    }

    @Test
    void entityPersistsReadyMetadataAndUsesStringEnumsAndOptimisticVersion() {
        Fixture fixture = createSpeakingFixture("entity");

        PracticeSpeakingMedia media = mediaRepository.saveAndFlush(PracticeSpeakingMedia.ready(
                fixture.attemptId(),
                fixture.speakingQuestionId(),
                PracticeSpeakingStorageProvider.LOCAL,
                key("entity-a.webm"),
                "audio/webm",
                "webm",
                "opus",
                1234L,
                5000L,
                hash("entity-a")));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT storage_provider, status, created_at, updated_at, deleted_at
                FROM practice_speaking_media
                WHERE id = ?
                """, media.getId());
        assertThat(row.get("storage_provider")).isEqualTo("LOCAL");
        assertThat(row.get("status")).isEqualTo("READY");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
        assertThat(row.get("deleted_at")).isNull();
        assertThat(media.getLockVersion()).isNotNull();

        Long before = media.getLockVersion();
        media.markDeleted();
        PracticeSpeakingMedia deleted = mediaRepository.saveAndFlush(media);
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getLockVersion()).isGreaterThan(before);
    }

    @Test
    void entityRejectsReverseTransitions() {
        Fixture fixture = createSpeakingFixture("transition");
        PracticeSpeakingMedia media = mediaRepository.saveAndFlush(readyMedia(fixture, "transition-a.webm"));

        media.markSuperseded();
        assertThatThrownBy(media::markSuperseded)
                .isInstanceOf(IllegalStateException.class);
        media.markDeleted();
        media.markDeleted();
        assertThat(media.getStatus()).isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThatThrownBy(media::markSuperseded)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uniqueProviderAndStorageKeyIsEnforced() {
        Fixture fixture = createSpeakingFixture("unique");
        String storageKey = key("unique-a.webm");

        mediaRepository.saveAndFlush(PracticeSpeakingMedia.ready(
                fixture.attemptId(),
                fixture.speakingQuestionId(),
                PracticeSpeakingStorageProvider.LOCAL,
                storageKey,
                "audio/webm",
                "webm",
                "opus",
                1234L,
                5000L,
                hash("unique-a")));

        assertThatThrownBy(() -> mediaRepository.saveAndFlush(PracticeSpeakingMedia.ready(
                fixture.attemptId(),
                fixture.speakingQuestionId(),
                PracticeSpeakingStorageProvider.LOCAL,
                storageKey,
                "audio/webm",
                "webm",
                "opus",
                2345L,
                7000L,
                hash("unique-b"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void descriptorCanonicalizesStorageKeyAndEnforcesBoundaries() {
        String maxStorageKey = "a".repeat(508) + ".web";
        String maxMime = "a".repeat(122) + "/webm1";
        String maxContainer = "w".repeat(32);
        String maxCodec = "o".repeat(64);
        String lowercaseHash = "a".repeat(64);

        ValidatedSpeakingMediaDescriptor descriptor = new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL,
                "Learner-Speaking/ABC/Take.WEBM",
                maxMime,
                maxContainer,
                maxCodec,
                1L,
                1L,
                lowercaseHash);

        assertThat(descriptor.storageKey()).isEqualTo("learner-speaking/abc/take.webm");
        assertThat(new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, maxStorageKey, maxMime, maxContainer, maxCodec, 1L, 1L,
                lowercaseHash).storageKey()).hasSize(512);

        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, maxStorageKey + "x", maxMime, maxContainer, maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "valid.webm", maxMime + "x", maxContainer, maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "valid.webm", maxMime, maxContainer + "x", maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "valid.webm", maxMime, maxContainer, maxCodec + "x", 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                null, "valid.webm", maxMime, maxContainer, maxCodec, 1L, 1L, lowercaseHash))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, " ", maxMime, maxContainer, maxCodec, 1L, 1L, lowercaseHash))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "bad\u0000key.webm", maxMime, maxContainer, maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "/absolute.webm", maxMime, maxContainer, maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "\\absolute.webm", maxMime, maxContainer, maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "C:\\audio.webm", maxMime, maxContainer, maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "\\\\server\\share\\audio.webm", maxMime, maxContainer, maxCodec,
                1L, 1L, lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "a/../b.webm", maxMime, maxContainer, maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "a\\..\\b.webm", maxMime, maxContainer, maxCodec, 1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "..", maxMime, maxContainer, maxCodec, 1L, 1L, lowercaseHash))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "valid.webm", maxMime, maxContainer, maxCodec, -1L, 1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "valid.webm", maxMime, maxContainer, maxCodec, 1L, -1L,
                lowercaseHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "valid.webm", maxMime, maxContainer, maxCodec, 1L, 1L,
                "A".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, "valid.webm", maxMime, maxContainer, maxCodec, 1L, 1L,
                "g".repeat(64))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void firstAndSecondActivationMaintainOneReadyMediaPerQuestion() {
        Fixture fixture = createSpeakingFixture("replace");

        SpeakingMediaIdentity first = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("replace-a.webm"));
        SpeakingMediaIdentity second = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("replace-b.webm"));

        assertThat(first.mediaId()).isNotEqualTo(second.mediaId());
        List<PracticeSpeakingMedia> ready = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.READY);
        List<PracticeSpeakingMedia> superseded = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.SUPERSEDED);
        assertThat(ready).hasSize(1);
        assertThat(ready.get(0).getId()).isEqualTo(second.mediaId());
        assertThat(superseded).hasSize(1);
        assertThat(superseded.get(0).getId()).isEqualTo(first.mediaId());
        assertThat(service.findReadyMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId())).contains(second);
    }

    @Test
    void duplicateStorageIdentityFailureLeavesExistingReadyRowUntouched() {
        Fixture fixture = createSpeakingFixture("rollback");
        ValidatedSpeakingMediaDescriptor descriptor = descriptor("rollback-a.webm");
        SpeakingMediaIdentity first = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor);

        assertThatThrownBy(() -> service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("storage identity");

        List<PracticeSpeakingMedia> ready = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.READY);
        List<PracticeSpeakingMedia> superseded = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.SUPERSEDED);
        assertThat(ready).hasSize(1);
        assertThat(ready.get(0).getId()).isEqualTo(first.mediaId());
        assertThat(superseded).isEmpty();
    }

    @Test
    void validationFailureLeavesExistingReadyRowUntouched() {
        Fixture fixture = createSpeakingFixture("validationrollback");
        SpeakingMediaIdentity first = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("validationrollback-a.webm"));
        PracticeQuestion essay = new PracticeQuestion(
                fixture.setId(), 9, PracticeQuestion.TYPE_ESSAY, "Essay", "[]", "", "Explain", BigDecimal.TEN, 9);
        essay.setGroupId(fixture.groupId());
        questionRepository.saveAndFlush(essay);

        assertThatThrownBy(() -> service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), essay.getId(), descriptor("validationrollback-b.webm")))
                .isInstanceOf(IllegalStateException.class);

        List<PracticeSpeakingMedia> ready = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.READY);
        assertThat(ready).hasSize(1);
        assertThat(ready.get(0).getId()).isEqualTo(first.mediaId());
    }

    @Test
    void differentQuestionsCanEachHaveOneReadyMedia() {
        Fixture fixture = createSpeakingFixture("twoquestions");
        PracticeQuestion secondQuestion = speakingQuestion(fixture.setId(), fixture.groupId(), 2);
        questionRepository.saveAndFlush(secondQuestion);

        service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("twoquestions-a.webm"));
        service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), secondQuestion.getId(), descriptor("twoquestions-b.webm"));

        assertThat(mediaRepository.findByAttemptIdAndStatus(fixture.attemptId(), PracticeSpeakingMediaStatus.READY))
                .hasSize(2);
    }

    @Test
    void ownershipSkillStatusQuestionScopeAndDescriptorAreValidated() {
        Fixture fixture = createSpeakingFixture("validation");
        User outsider = userRepository.findByEmailIgnoreCase("sv01@ksh.edu.vn").orElseThrow();
        Fixture otherFixture = createSpeakingFixture("validation-other");

        assertThatThrownBy(() -> service.activateValidatedMediaForOwner(
                outsider.getId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("wrong-user.webm")))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);

        PracticeAttempt readingAttempt = attemptRepository.saveAndFlush(new PracticeAttempt(
                fixture.userId(), fixture.setId(), fixture.testId(), "READING", fixture.sectionId()));
        assertThatThrownBy(() -> service.activateValidatedMediaForOwner(
                fixture.userId(), readingAttempt.getId(), fixture.speakingQuestionId(), descriptor("reading.webm")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only SPEAKING");

        PracticeAttempt submitted = attemptRepository.saveAndFlush(new PracticeAttempt(
                fixture.userId(), fixture.setId(), fixture.testId(), "SPEAKING", fixture.sectionId()));
        submitted.setStatus(PracticeAttempt.STATUS_SUBMITTED);
        attemptRepository.saveAndFlush(submitted);
        assertThatThrownBy(() -> service.activateValidatedMediaForOwner(
                fixture.userId(), submitted.getId(), fixture.speakingQuestionId(), descriptor("submitted.webm")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before submit");

        assertThatThrownBy(() -> service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), otherFixture.speakingQuestionId(), descriptor("outside.webm")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attempt set");

        PracticeQuestion essay = new PracticeQuestion(
                fixture.setId(), 9, PracticeQuestion.TYPE_ESSAY, "Essay", "[]", "", "Explain", BigDecimal.TEN, 9);
        essay.setGroupId(fixture.groupId());
        questionRepository.saveAndFlush(essay);
        assertThatThrownBy(() -> service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), essay.getId(), descriptor("essay.webm")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPEAKING questions");

        assertThatThrownBy(() -> descriptor("../secret.webm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, key("zero.webm"), "audio/webm", "webm", "opus", 0L, 1L, hash("zero")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL, key("bad.webm"), "audio/webm", "webm", "opus", 1L, 1L, "not-a-hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markDeleteSetsDeletedAtAndReadyLookupExcludesDeletedAndSuperseded() {
        Fixture fixture = createSpeakingFixture("delete");
        SpeakingMediaIdentity first = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("delete-a.webm"));
        service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("delete-b.webm"));

        assertThat(mediaRepository.findById(first.mediaId()).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaStatus.SUPERSEDED);

        service.markDeletedForOwner(fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId());

        List<PracticeSpeakingMedia> ready = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.READY);
        List<PracticeSpeakingMedia> deleted = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.DELETED);
        assertThat(ready).isEmpty();
        assertThat(deleted).hasSize(2);
        assertThat(deleted).allSatisfy(media -> assertThat(media.getDeletedAt()).isNotNull());
        assertThat(service.findReadyMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId())).isEmpty();
    }

    @Test
    void attemptAndQuestionHardDeletesAreRestrictedWhenMediaExists() {
        Fixture attemptFixture = createSpeakingFixture("restrict-attempt");
        service.activateValidatedMediaForOwner(
                attemptFixture.userId(), attemptFixture.attemptId(), attemptFixture.speakingQuestionId(),
                descriptor("restrict-attempt-a.webm"));

        assertThatThrownBy(() -> attemptRepository.deleteById(attemptFixture.attemptId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        Fixture questionFixture = createSpeakingFixture("restrict-question");
        service.activateValidatedMediaForOwner(
                questionFixture.userId(), questionFixture.attemptId(), questionFixture.speakingQuestionId(),
                descriptor("restrict-question-a.webm"));

        assertThatThrownBy(() -> questionRepository.deleteById(questionFixture.speakingQuestionId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleReadyCorruptionFailsWithoutChoosingRandomRow() {
        Fixture fixture = createSpeakingFixture("corruption");
        mediaRepository.saveAndFlush(readyMedia(fixture, "corruption-a.webm"));
        mediaRepository.saveAndFlush(PracticeSpeakingMedia.ready(
                fixture.attemptId(),
                fixture.speakingQuestionId(),
                PracticeSpeakingStorageProvider.LOCAL,
                key("corruption-b.webm"),
                "audio/webm",
                "webm",
                "opus",
                1234L,
                5000L,
                hash("corruption-b")));

        assertThatThrownBy(() -> service.findReadyMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple READY");
        assertThatThrownBy(() -> service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("corruption-c.webm")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple READY");
        assertThat(mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.READY))
                .hasSize(2);
    }

    @Test
    void concurrentReplacementLeavesOneReadyRow() throws Exception {
        Fixture fixture = createSpeakingFixture("concurrent");
        service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("concurrent-initial.webm"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<?> first = executor.submit(() -> {
            await(barrier);
            service.activateValidatedMediaForOwner(
                    fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("concurrent-a.webm"));
        });
        Future<?> second = executor.submit(() -> {
            await(barrier);
            service.activateValidatedMediaForOwner(
                    fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("concurrent-b.webm"));
        });

        first.get(20, TimeUnit.SECONDS);
        second.get(20, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.READY))
                .hasSize(1);
        assertThat(mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.SUPERSEDED))
                .hasSize(2);
    }

    @Test
    void existingTextSpeakingAttemptFieldsRemainUntouchedByMediaFoundation() {
        Fixture fixture = createSpeakingFixture("regression");

        service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("regression.webm"));

        PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
        assertThat(attempt.getAnswersJson()).isNull();
        assertThat(attempt.getAiFeedbackJson()).isNull();
    }

    private Fixture createSpeakingFixture(String label) {
        User student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();
        String suffix = label + "-" + System.nanoTime();
        PracticeSet set = setRepository.saveAndFlush(new PracticeSet(
                "Speaking Media " + suffix,
                "Desc",
                "SPEAKING",
                "TOPIK_II",
                "GLOBAL",
                null,
                null,
                null,
                "PUBLISHED",
                student.getId()));
        PracticeTest test = testRepository.saveAndFlush(new PracticeTest(
                set.getId(), "Test " + suffix, "Desc", 1, 40));
        PracticeSection section = new PracticeSection(
                set.getId(), "Speaking Section " + suffix, "SPEAKING", "ORAL", "Instructions", 30, BigDecimal.TEN, 1);
        section.setTestId(test.getId());
        section = sectionRepository.saveAndFlush(section);
        PracticeQuestionGroup group = new PracticeQuestionGroup(
                set.getId(), "1", 1, 1, "Instruction", null, null, 1);
        group.setSectionId(section.getId());
        group = groupRepository.saveAndFlush(group);
        PracticeQuestion question = speakingQuestion(set.getId(), group.getId(), 1);
        question = questionRepository.saveAndFlush(question);
        PracticeAttempt attempt = attemptRepository.saveAndFlush(new PracticeAttempt(
                student.getId(), set.getId(), test.getId(), "SPEAKING", section.getId()));
        return new Fixture(student.getId(), set.getId(), test.getId(), section.getId(), group.getId(), question.getId(), attempt.getId());
    }

    private PracticeQuestion speakingQuestion(Long setId, Long groupId, int number) {
        PracticeQuestion question = new PracticeQuestion(
                setId,
                number,
                PracticeQuestion.TYPE_SPEAKING,
                "Speaking prompt " + number,
                "[]",
                "",
                "Explain",
                BigDecimal.TEN,
                number);
        question.setGroupId(groupId);
        return question;
    }

    private PracticeSpeakingMedia readyMedia(Fixture fixture, String keySuffix) {
        return PracticeSpeakingMedia.ready(
                fixture.attemptId(),
                fixture.speakingQuestionId(),
                PracticeSpeakingStorageProvider.LOCAL,
                key(keySuffix),
                "audio/webm",
                "webm",
                "opus",
                1234L,
                5000L,
                hash(keySuffix));
    }

    private ValidatedSpeakingMediaDescriptor descriptor(String keySuffix) {
        return new ValidatedSpeakingMediaDescriptor(
                PracticeSpeakingStorageProvider.LOCAL,
                key(keySuffix),
                "audio/webm",
                "webm",
                "opus",
                1234L,
                5000L,
                hash(keySuffix));
    }

    private String key(String name) {
        return "learner-speaking/" + System.nanoTime() + "/" + name;
    }

    private String hash(String seed) {
        return String.format(Locale.ROOT, "%064x", Math.abs(seed.hashCode()) + 1L);
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record Fixture(
            Long userId,
            Long setId,
            Long testId,
            Long sectionId,
            Long groupId,
            Long speakingQuestionId,
            Long attemptId
    ) {}
}
