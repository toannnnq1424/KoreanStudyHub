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
import com.ksh.features.practice.service.audio.PreparedSpeakingAudio;
import com.ksh.features.practice.service.audio.SpeakingAudioPreparationService;
import com.ksh.features.practice.service.audio.SpeakingAudioStorage;
import com.ksh.features.practice.service.audio.StoredSpeakingAudioObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
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
    void preflightValidatesOwnerSkillStatusAndQuestionTypeWithoutMutation() {
        Fixture fixture = createSpeakingFixture("preflight");
        User outsider = userRepository.findByEmailIgnoreCase("sv01@ksh.edu.vn").orElseThrow();

        service.validateUploadTargetForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId());

        assertThat(mediaRepository.findByAttemptIdAndStatus(
                fixture.attemptId(), PracticeSpeakingMediaStatus.READY)).isEmpty();
        assertThatThrownBy(() -> service.validateUploadTargetForOwner(
                outsider.getId(), fixture.attemptId(), fixture.speakingQuestionId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessage("Speaking media target not found.");

        PracticeAttempt readingAttempt = attemptRepository.saveAndFlush(new PracticeAttempt(
                fixture.userId(), fixture.setId(), fixture.testId(), "READING", fixture.sectionId()));
        assertThatThrownBy(() -> service.validateUploadTargetForOwner(
                fixture.userId(), readingAttempt.getId(), fixture.speakingQuestionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only SPEAKING");

        PracticeAttempt submitted = attemptRepository.saveAndFlush(new PracticeAttempt(
                fixture.userId(), fixture.setId(), fixture.testId(), "SPEAKING", fixture.sectionId()));
        submitted.setStatus(PracticeAttempt.STATUS_SUBMITTED);
        attemptRepository.saveAndFlush(submitted);
        assertThatThrownBy(() -> service.validateUploadTargetForOwner(
                fixture.userId(), submitted.getId(), fixture.speakingQuestionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before submit");

        PracticeQuestion essay = new PracticeQuestion(
                fixture.setId(), 9, PracticeQuestion.TYPE_ESSAY, "Essay", "[]", "", "Explain", BigDecimal.TEN, 9);
        essay.setGroupId(fixture.groupId());
        essay = questionRepository.saveAndFlush(essay);
        Long essayId = essay.getId();
        assertThatThrownBy(() -> service.validateUploadTargetForOwner(
                fixture.userId(), fixture.attemptId(), essayId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPEAKING questions");
    }

    @Test
    void preflightRejectsQuestionOutsideExactSetAndSectionAsNotFound() {
        Fixture fixture = createSpeakingFixture("preflight-scope");
        Fixture otherSet = createSpeakingFixture("preflight-other-set");

        assertThatThrownBy(() -> service.validateUploadTargetForOwner(
                fixture.userId(), fixture.attemptId(), otherSet.speakingQuestionId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessage("Speaking media target not found.");

        PracticeSection secondSection = new PracticeSection(
                fixture.setId(), "Other section", "SPEAKING", "ORAL", "Instructions", 30, BigDecimal.TEN, 2);
        secondSection.setTestId(fixture.testId());
        secondSection = sectionRepository.saveAndFlush(secondSection);
        PracticeQuestionGroup otherGroup = new PracticeQuestionGroup(
                fixture.setId(), "2", 2, 2, "Instruction", null, null, 2);
        otherGroup.setSectionId(secondSection.getId());
        otherGroup = groupRepository.saveAndFlush(otherGroup);
        PracticeQuestion otherSectionQuestion = speakingQuestion(fixture.setId(), otherGroup.getId(), 2);
        otherSectionQuestion = questionRepository.saveAndFlush(otherSectionQuestion);
        Long otherSectionQuestionId = otherSectionQuestion.getId();

        assertThatThrownBy(() -> service.validateUploadTargetForOwner(
                fixture.userId(), fixture.attemptId(), otherSectionQuestionId))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessage("Speaking media target not found.");
    }

    @Test
    void firstAndSecondActivationMaintainOneReadyMediaPerQuestion() {
        Fixture fixture = createSpeakingFixture("replace");
        ValidatedSpeakingMediaDescriptor firstDescriptor = descriptor("replace-a.webm");
        ValidatedSpeakingMediaDescriptor secondDescriptor = descriptor("replace-b.webm");

        SpeakingMediaActivationResult first = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), firstDescriptor);
        SpeakingMediaActivationResult second = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), secondDescriptor);

        assertThat(first.mediaId()).isNotEqualTo(second.mediaId());
        assertThat(first.status()).isEqualTo(PracticeSpeakingMediaStatus.READY);
        assertThat(first.supersededCleanup()).isEmpty();
        assertThat(second.supersededCleanup()).hasValueSatisfying(cleanup -> {
            assertThat(cleanup.mediaId()).isEqualTo(first.mediaId());
            assertThat(cleanup.storageProvider()).isEqualTo(PracticeSpeakingStorageProvider.LOCAL);
            assertThat(cleanup.storageKey()).isEqualTo(firstDescriptor.storageKey());
            assertThat(cleanup.storageKey()).isNotEqualTo(secondDescriptor.storageKey());
            assertThat(cleanup.toString()).doesNotContain(cleanup.storageKey());
        });
        assertThat(second.toString())
                .doesNotContain(firstDescriptor.storageKey())
                .doesNotContain(secondDescriptor.storageKey())
                .doesNotContain(firstDescriptor.contentHash())
                .doesNotContain(secondDescriptor.contentHash());
        List<PracticeSpeakingMedia> ready = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.READY);
        List<PracticeSpeakingMedia> superseded = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.SUPERSEDED);
        assertThat(ready).hasSize(1);
        assertThat(ready.get(0).getId()).isEqualTo(second.mediaId());
        assertThat(superseded).hasSize(1);
        assertThat(superseded.get(0).getId()).isEqualTo(first.mediaId());
        assertThat(service.findReadyMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId()))
                .hasValueSatisfying(identity -> assertThat(identity.mediaId()).isEqualTo(second.mediaId()));
    }

    @Test
    void duplicateStorageIdentityFailureLeavesExistingReadyRowUntouched() {
        Fixture fixture = createSpeakingFixture("rollback");
        ValidatedSpeakingMediaDescriptor descriptor = descriptor("rollback-a.webm");
        SpeakingMediaActivationResult first = service.activateValidatedMediaForOwner(
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
        SpeakingMediaActivationResult first = service.activateValidatedMediaForOwner(
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
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessage("Speaking media target not found.");

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
    void exactMediaDeleteHandlesReadySupersededAndAlreadyDeletedIdempotently() {
        Fixture fixture = createSpeakingFixture("delete");
        ValidatedSpeakingMediaDescriptor firstDescriptor = descriptor("delete-a.webm");
        ValidatedSpeakingMediaDescriptor secondDescriptor = descriptor("delete-b.webm");
        SpeakingMediaActivationResult first = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), firstDescriptor);
        SpeakingMediaActivationResult second = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), secondDescriptor);

        SpeakingMediaDeletionResult supersededDelete = service.markDeletedForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), first.mediaId());
        SpeakingMediaDeletionResult repeatedDelete = service.markDeletedForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), first.mediaId());
        SpeakingMediaDeletionResult readyDelete = service.markDeletedForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), second.mediaId());

        assertThat(supersededDelete.status()).isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThat(repeatedDelete.status()).isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThat(readyDelete.status()).isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThat(supersededDelete.cleanup()).hasValueSatisfying(cleanup ->
                assertThat(cleanup.storageKey()).isEqualTo(firstDescriptor.storageKey()));
        assertThat(repeatedDelete.cleanup()).hasValueSatisfying(cleanup ->
                assertThat(cleanup.storageKey()).isEqualTo(firstDescriptor.storageKey()));
        assertThat(readyDelete.cleanup()).hasValueSatisfying(cleanup ->
                assertThat(cleanup.storageKey()).isEqualTo(secondDescriptor.storageKey()));
        assertThat(repeatedDelete.toString()).doesNotContain(firstDescriptor.storageKey());

        List<PracticeSpeakingMedia> deleted = mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.DELETED);
        assertThat(deleted).hasSize(2);
        assertThat(deleted).allSatisfy(media -> assertThat(media.getDeletedAt()).isNotNull());
        assertThat(service.findReadyMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId())).isEmpty();
    }

    @Test
    void exactMediaDeleteRejectsWrongIdentityOwnerScopeAndCompletedAttempt() {
        Fixture fixture = createSpeakingFixture("delete-scope");
        Fixture other = createSpeakingFixture("delete-scope-other");
        User outsider = userRepository.findByEmailIgnoreCase("sv01@ksh.edu.vn").orElseThrow();
        SpeakingMediaActivationResult media = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("delete-a.webm"));

        assertThatThrownBy(() -> service.markDeletedForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), Long.MAX_VALUE))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThatThrownBy(() -> service.markDeletedForOwner(
                fixture.userId(), other.attemptId(), other.speakingQuestionId(), media.mediaId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThatThrownBy(() -> service.markDeletedForOwner(
                outsider.getId(), fixture.attemptId(), fixture.speakingQuestionId(), media.mediaId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);

        PracticeQuestion secondQuestion = speakingQuestion(fixture.setId(), fixture.groupId(), 2);
        secondQuestion = questionRepository.saveAndFlush(secondQuestion);
        Long wrongQuestionId = secondQuestion.getId();
        assertThatThrownBy(() -> service.markDeletedForOwner(
                fixture.userId(), fixture.attemptId(), wrongQuestionId, media.mediaId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);

        PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
        attempt.setStatus(PracticeAttempt.STATUS_SUBMITTED);
        attemptRepository.saveAndFlush(attempt);
        assertThatThrownBy(() -> service.markDeletedForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), media.mediaId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before submit");
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaStatus.READY);
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

    @Test
    void orchestrationCrossesSpringTransactionBoundariesBeforePreparationAndPhysicalDelete() {
        Fixture fixture = createSpeakingFixture("transaction-boundary");
        TransactionObservingStorage storage = new TransactionObservingStorage();
        TransactionObservingPreparation preparation = new TransactionObservingPreparation(
                storage,
                prepared("learner-speaking/ready/transaction-boundary", "transaction-boundary"),
                () -> {});
        SpeakingAudioUploadService uploadService = new SpeakingAudioUploadService(preparation, service, storage);

        assertThat(AopUtils.isAopProxy(service)).isTrue();
        SpeakingAudioUploadService.SpeakingAudioUploadResult uploaded = uploadService.uploadOrReplaceForOwner(
                fixture.userId(),
                fixture.attemptId(),
                fixture.speakingQuestionId(),
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "audio/webm");
        SpeakingAudioUploadService.SpeakingAudioDeletionResult deleted = uploadService.deleteForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), uploaded.mediaId());

        assertThat(preparation.transactionActiveDuringPrepare).isFalse();
        assertThat(storage.transactionActiveDuringDeletes).containsExactly(false);
        assertThat(deleted.status()).isEqualTo(PracticeSpeakingMediaStatus.DELETED);
        assertThat(mediaRepository.findById(uploaded.mediaId()).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaStatus.DELETED);
    }

    @Test
    void statusChangeAfterPreflightRejectsActivationCompensatesNewObjectAndPreservesOldReady() {
        Fixture fixture = createSpeakingFixture("status-race-integration");
        SpeakingMediaActivationResult oldReady = service.activateValidatedMediaForOwner(
                fixture.userId(), fixture.attemptId(), fixture.speakingQuestionId(), descriptor("status-race-old"));
        String newKey = key("status-race-new");
        TransactionObservingStorage storage = new TransactionObservingStorage();
        TransactionObservingPreparation preparation = new TransactionObservingPreparation(
                storage,
                prepared(newKey, "status-race-new"),
                () -> {
                    PracticeAttempt attempt = attemptRepository.findById(fixture.attemptId()).orElseThrow();
                    attempt.setStatus(PracticeAttempt.STATUS_SUBMITTED);
                    attemptRepository.saveAndFlush(attempt);
                });
        SpeakingAudioUploadService uploadService = new SpeakingAudioUploadService(preparation, service, storage);

        assertThatThrownBy(() -> uploadService.uploadOrReplaceForOwner(
                fixture.userId(),
                fixture.attemptId(),
                fixture.speakingQuestionId(),
                new ByteArrayInputStream(new byte[]{1}),
                1L,
                "audio/webm"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before submit");

        assertThat(preparation.transactionActiveDuringPrepare).isFalse();
        assertThat(storage.deletedKeys).containsExactly(newKey);
        assertThat(storage.transactionActiveDuringDeletes).containsExactly(false);
        assertThat(mediaRepository.findById(oldReady.mediaId()).orElseThrow().getStatus())
                .isEqualTo(PracticeSpeakingMediaStatus.READY);
        assertThat(mediaRepository.findByAttemptIdAndQuestionIdAndStatus(
                fixture.attemptId(), fixture.speakingQuestionId(), PracticeSpeakingMediaStatus.READY))
                .extracting(PracticeSpeakingMedia::getId)
                .containsExactly(oldReady.mediaId());
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

    private PreparedSpeakingAudio prepared(String storageKey, String hashSeed) {
        return new PreparedSpeakingAudio(
                PracticeSpeakingStorageProvider.LOCAL,
                storageKey,
                "audio/webm",
                "webm",
                "opus",
                1L,
                1000L,
                hash(hashSeed));
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

    private static final class TransactionObservingPreparation extends SpeakingAudioPreparationService {
        private final PreparedSpeakingAudio prepared;
        private final Runnable beforeReturn;
        private boolean transactionActiveDuringPrepare;

        private TransactionObservingPreparation(
                SpeakingAudioStorage storage,
                PreparedSpeakingAudio prepared,
                Runnable beforeReturn) {
            super(storage, privateMediaPath -> {
                throw new AssertionError("Inspector must not be called by the preparation test double");
            });
            this.prepared = prepared;
            this.beforeReturn = beforeReturn;
        }

        @Override
        public PreparedSpeakingAudio prepare(
                InputStream content, Long declaredContentLength, String clientMimeType) {
            transactionActiveDuringPrepare = TransactionSynchronizationManager.isActualTransactionActive();
            beforeReturn.run();
            return prepared;
        }
    }

    private static final class TransactionObservingStorage implements SpeakingAudioStorage {
        private final List<String> deletedKeys = new ArrayList<>();
        private final List<Boolean> transactionActiveDuringDeletes = new ArrayList<>();

        @Override
        public StoredSpeakingAudioObject writeTemporary(InputStream content, Long declaredContentLength) {
            throw new AssertionError("Storage write is owned by the preparation test double");
        }

        @Override
        public String promoteTemporary(String temporaryKey) {
            throw new AssertionError("Storage promotion is owned by the preparation test double");
        }

        @Override
        public InputStream open(String storageKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String storageKey) {
            return false;
        }

        @Override
        public void delete(String storageKey) {
            transactionActiveDuringDeletes.add(TransactionSynchronizationManager.isActualTransactionActive());
            deletedKeys.add(storageKey);
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
