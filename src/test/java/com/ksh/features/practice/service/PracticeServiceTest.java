package com.ksh.features.practice.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeQuestionGroup;
import com.ksh.entities.PracticeQuestionGroupVersion;
import com.ksh.entities.PracticeQuestionVersion;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSetVersion;
import com.ksh.entities.PracticeAttempt;
import com.ksh.entities.PracticeSection;
import com.ksh.entities.PracticeSectionVersion;
import com.ksh.entities.PracticePublishedVersion;
import com.ksh.entities.PracticeTestVersion;
import com.ksh.entities.WritingTaskType;
import com.ksh.features.practice.ai.writing.WritingEvaluationClient;
import com.ksh.features.practice.ai.writing.WritingContractTestFixtures;
import com.ksh.features.practice.ai.writing.WritingScoringPolicy;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationApplicationService;
import com.ksh.features.practice.ai.speaking.SpeakingContractTrust;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationSource;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluatorCapability;
import com.ksh.features.practice.ai.speaking.SpeakingEvidenceMode;
import com.ksh.features.practice.ai.speaking.SpeakingPromptRules;
import com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion;
import com.ksh.features.practice.dto.PracticeDtos.*;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PracticeServiceTest {

    private static final String
            PRODUCTION_SHAPED_WRITING_CONTRACT_IDENTITY =
            "ksh-writing-evaluation-v2|"
                    + "policy-component|".repeat(34);

    private PracticeSetRepository setRepository;
    private PracticeQuestionRepository questionRepository;
    private com.ksh.features.practice.repository.PracticeQuestionVersionRepository questionVersionRepository;
    private PracticeQuestionGroupRepository groupRepository;
    private com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository;
    private com.ksh.features.practice.repository.PracticeAttemptRepository attemptRepository;
    private com.ksh.features.practice.repository.PracticeTestRepository testRepository;
    private WritingEvaluationClient evaluationClient;
    private PracticePublishedVersionService publishedVersionService;
    private PracticeSpeakingMediaService speakingMediaService;
    private SpeakingEvaluationApplicationService speakingEvaluationService;
    private ObjectMapper objectMapper;

    private PracticeService practiceService;

    @BeforeEach
    void setUp() {
        setRepository = mock(PracticeSetRepository.class);
        questionRepository = mock(PracticeQuestionRepository.class);
        questionVersionRepository = mock(com.ksh.features.practice.repository.PracticeQuestionVersionRepository.class);
        groupRepository = mock(PracticeQuestionGroupRepository.class);
        sectionRepository = mock(com.ksh.features.practice.repository.PracticeSectionRepository.class);
        attemptRepository = mock(com.ksh.features.practice.repository.PracticeAttemptRepository.class);
        testRepository = mock(com.ksh.features.practice.repository.PracticeTestRepository.class);
        evaluationClient = mock(WritingEvaluationClient.class);
        publishedVersionService = mock(PracticePublishedVersionService.class);
        speakingMediaService = mock(PracticeSpeakingMediaService.class);
        speakingEvaluationService =
                mock(SpeakingEvaluationApplicationService.class);
        objectMapper = new ObjectMapper();
        when(setRepository.findByIdForUpdate(any())).thenAnswer(invocation ->
                setRepository.findById(invocation.getArgument(0)));

        practiceService = new PracticeService(
                setRepository,
                questionRepository,
                questionVersionRepository,
                groupRepository,
                sectionRepository,
                attemptRepository,
                testRepository,
                evaluationClient,
                objectMapper
        );
        practiceService.setPublishedVersionServiceForTests(
                publishedVersionService);
        practiceService.setSpeakingMediaService(speakingMediaService);
        practiceService.setSpeakingEvaluationApplicationService(
                speakingEvaluationService);
        lenient().when(publishedVersionService
                .hasCoherentAttemptIdentity(any(PracticeAttempt.class)))
                .thenReturn(true);
        lenient().when(evaluationClient.evaluationContractIdentity())
                .thenReturn(
                        PRODUCTION_SHAPED_WRITING_CONTRACT_IDENTITY);
        lenient().when(
                speakingEvaluationService.evaluationContractIdentity())
                .thenReturn("ksh-speaking-evaluation-test");
    }

    @Test
    void testGetPracticeNotFound() {
        when(setRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> practiceService.getPractice(1L));
    }

    @Test
    void testGetPracticeNotPublished() {
        PracticeSet set = new PracticeSet("Title", "Desc", "READING",  "GLOBAL", null, null, null, "DRAFT", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        assertThrows(EntityNotFoundException.class, () -> practiceService.getPractice(1L));
    }

    @Test
    void listeningPreflightPrefersCanonicalInternalCheckAudio() {
        PracticeSection section = mock(PracticeSection.class);
        when(section.getSetId()).thenReturn(1L);
        when(section.getTestId()).thenReturn(2L);
        when(section.getSkill()).thenReturn("LISTENING");
        when(section.getTitle()).thenReturn("Phần Nghe");
        when(section.getDeliveryJson()).thenReturn("""
                {"schemaVersion":"practice-section-delivery-v1","listeningDelivery":{"checkAudioReference":"/practice/materials/7/content"}}
                """);
        when(sectionRepository.findById(3L)).thenReturn(Optional.of(section));
        when(groupRepository.findBySectionIdOrderByDisplayOrderAsc(3L)).thenReturn(List.of());

        PracticeService.ListeningPreflightDelivery delivery =
                practiceService.getListeningPreflightDelivery(1L, 2L, 3L);

        assertEquals("/practice/materials/7/content", delivery.checkAudioReference());
    }

    @Test
    void listeningPreflightAcceptsOnlyTheBundledSeedSpeakerCheckOutsideMaterialRoutes() {
        PracticeSection section = mock(PracticeSection.class);
        when(section.getSetId()).thenReturn(2L);
        when(section.getTestId()).thenReturn(2L);
        when(section.getSkill()).thenReturn("LISTENING");
        when(section.getTitle()).thenReturn("Phần Nghe");
        when(section.getDeliveryJson()).thenReturn("""
                {"schemaVersion":"practice-section-delivery-v1","listeningDelivery":{"checkAudioReference":"/audio/practice/listening-speaker-check.wav"}}
                """);
        when(sectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(groupRepository.findBySectionIdOrderByDisplayOrderAsc(2L)).thenReturn(List.of());

        PracticeService.ListeningPreflightDelivery delivery =
                practiceService.getListeningPreflightDelivery(2L, 2L, 2L);

        assertEquals("/audio/practice/listening-speaker-check.wav", delivery.checkAudioReference());
    }

    @Test
    void listeningPreflightRejectsOtherStaticAudioPaths() {
        PracticeSection section = mock(PracticeSection.class);
        when(section.getSetId()).thenReturn(2L);
        when(section.getTestId()).thenReturn(2L);
        when(section.getSkill()).thenReturn("LISTENING");
        when(section.getDeliveryJson()).thenReturn("""
                {"schemaVersion":"practice-section-delivery-v1","listeningDelivery":{"checkAudioReference":"/audio/untrusted.wav"}}
                """);
        when(sectionRepository.findById(2L)).thenReturn(Optional.of(section));
        when(groupRepository.findBySectionIdOrderByDisplayOrderAsc(2L)).thenReturn(List.of());

        assertThrows(IllegalStateException.class,
                () -> practiceService.getListeningPreflightDelivery(2L, 2L, 2L));
    }

    @Test
    void listeningPreflightRejectsUnsafeCanonicalReferenceWithoutMaskingLegacyFallback() {
        PracticeSection section = mock(PracticeSection.class);
        when(section.getSetId()).thenReturn(1L);
        when(section.getTestId()).thenReturn(2L);
        when(section.getSkill()).thenReturn("LISTENING");
        when(section.getTitle()).thenReturn("Phần Nghe cũ");
        when(section.getDeliveryJson()).thenReturn("""
                {"schemaVersion":"practice-section-delivery-v1","listeningDelivery":{"checkAudioReference":"https://outside.example/check.mp3"}}
                """);
        when(sectionRepository.findById(3L)).thenReturn(Optional.of(section));
        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getAudioUrl()).thenReturn("/practice/materials/9/content");
        when(groupRepository.findBySectionIdOrderByDisplayOrderAsc(3L)).thenReturn(List.of(group));

        PracticeService.ListeningPreflightDelivery delivery =
                practiceService.getListeningPreflightDelivery(1L, 2L, 3L);

        assertEquals("/practice/materials/9/content", delivery.checkAudioReference());
    }

    @Test
    void listeningPreflightRejectsSectionWithoutAnyInternalCheckAudio() {
        PracticeSection section = mock(PracticeSection.class);
        when(section.getSetId()).thenReturn(1L);
        when(section.getTestId()).thenReturn(2L);
        when(section.getSkill()).thenReturn("LISTENING");
        when(section.getDeliveryJson()).thenReturn(null);
        when(sectionRepository.findById(3L)).thenReturn(Optional.of(section));
        when(groupRepository.findBySectionIdOrderByDisplayOrderAsc(3L)).thenReturn(List.of());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> practiceService.getListeningPreflightDelivery(1L, 2L, 3L));

        assertTrue(exception.getMessage().contains("audio thử loa bất biến hợp lệ"));
    }

    @Test
    void testGetPracticeWithGroups() {
        PracticeSet set = new PracticeSet("Title", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getId()).thenReturn(100L);
        when(group.getGroupLabel()).thenReturn("1-2");
        when(group.getQuestionFrom()).thenReturn(1);
        when(group.getQuestionTo()).thenReturn(2);
        when(group.getInstruction()).thenReturn("Instruction");
        when(group.getAudioUrl()).thenReturn("/practice/materials/8/content");
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(group));

        PracticeQuestion q1 = mock(PracticeQuestion.class);
        when(q1.getId()).thenReturn(10L);
        when(q1.getQuestionNo()).thenReturn(1);
        when(q1.getQuestionType()).thenReturn("MCQ");
        when(q1.getPrompt()).thenReturn("Q1");
        when(q1.getGroupId()).thenReturn(100L);
        when(q1.getPoints()).thenReturn(BigDecimal.valueOf(5));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q1));

        PracticeSetView view = practiceService.getPractice(1L);
        assertNotNull(view);
        assertEquals(1, view.groups().size());
        assertEquals("1-2", view.groups().get(0).groupLabel());
        assertEquals("/practice/materials/8/content", view.groups().get(0).audioUrl());
        assertEquals(1, view.groups().get(0).questions().size());
    }

    @Test
    void getPracticeDropsExternalGroupAudioFromLearnerDelivery() {
        PracticeSet set = new PracticeSet(
                "Title", "Desc", "LISTENING", "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(1L)).thenReturn(Optional.of(set));

        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getId()).thenReturn(100L);
        when(group.getGroupLabel()).thenReturn("1");
        when(group.getAudioUrl()).thenReturn("https://outside.example/question.mp3");
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());

        PracticeSetView view = practiceService.getPractice(1L);

        assertEquals(1, view.groups().size());
        assertNull(view.groups().get(0).audioUrl());
    }

    @Test
    void getPracticeAllowsBuiltInDeterministicListeningGroupAudio() {
        PracticeSet set = new PracticeSet(
                "Title", "Desc", "LISTENING", "GLOBAL", null, null, null,
                "PUBLISHED", 1L);
        when(setRepository.findById(1L)).thenReturn(Optional.of(set));

        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getId()).thenReturn(100L);
        when(group.getGroupLabel()).thenReturn("1");
        when(group.getAudioUrl()).thenReturn(
                "/audio/practice/listening-speaker-check.wav");
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of());

        PracticeSetView view = practiceService.getPractice(1L);

        assertEquals("/audio/practice/listening-speaker-check.wav",
                view.groups().get(0).audioUrl());
    }

    @Test
    void testGetPracticeFallbackGrouping() {
        PracticeSet set = new PracticeSet("Title", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());

        PracticeQuestion q1 = mock(PracticeQuestion.class);
        when(q1.getId()).thenReturn(10L);
        when(q1.getQuestionNo()).thenReturn(1);
        when(q1.getQuestionType()).thenReturn("MCQ");
        when(q1.getPrompt()).thenReturn("Q1");
        when(q1.getPoints()).thenReturn(BigDecimal.valueOf(5));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q1));

        PracticeSetView view = practiceService.getPractice(1L);
        assertNotNull(view);
        assertEquals(1, view.groups().size());
        assertEquals("1-2", view.groups().get(0).groupLabel());
    }

    @Test
    void getPracticeSummaryLoadsOnlySetAndTestsForDetailPages() {
        PracticeSet set = new PracticeSet("Title", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(1L)).thenReturn(Optional.of(set));

        com.ksh.entities.PracticeTest test =
                new com.ksh.entities.PracticeTest(1L, "Test 1", "Summary only", 1, 40);
        when(testRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(test));

        PracticeSetView view = practiceService.getPracticeSummary(1L);

        assertEquals("Title", view.set().title());
        assertTrue(view.groups().isEmpty());
        assertTrue(view.sections().isEmpty());
        assertEquals(1, view.tests().size());
        assertEquals("Test 1", view.tests().get(0).title());
        verify(groupRepository, never()).findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(questionRepository, never()).findBySetIdOrderByDisplayOrderAsc(anyLong());
    }

    @Test
    void startAttemptLocksLatestPublishedVersion() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeSet set = mock(PracticeSet.class);
        when(set.getId()).thenReturn(1L);
        when(set.getStatus()).thenReturn(PracticeSet.STATUS_PUBLISHED);
        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(setRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(set));

        com.ksh.entities.PracticeTest test = mock(com.ksh.entities.PracticeTest.class);
        when(test.getId()).thenReturn(10L);
        when(test.getSetId()).thenReturn(1L);
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(testRepository.findByIdForShare(10L)).thenReturn(Optional.of(test));

        PracticeSection section = mock(PracticeSection.class);
        when(section.getId()).thenReturn(20L);
        when(section.getSetId()).thenReturn(1L);
        when(section.getTestId()).thenReturn(10L);
        when(section.getSkill()).thenReturn("READING");
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findByIdForShare(20L)).thenReturn(Optional.of(section));

        when(versionService.latestLock(1L, 10L, 20L))
                .thenReturn(Optional.of(new PracticeAttemptVersionLock(100L, 101L, 102L, 103L)));
        PracticeVersionSnapshot snapshot = versionSnapshot("READING");
        when(versionService.snapshot(100L, 101L, 102L, 103L))
                .thenReturn(Optional.of(snapshot));
        when(attemptRepository.findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(
                2L, 10L, 20L, PracticeAttempt.STATUS_IN_PROGRESS)).thenReturn(Optional.empty());
        when(attemptRepository.save(any(PracticeAttempt.class))).thenAnswer(invocation -> {
            PracticeAttempt saved = invocation.getArgument(0);
            setEntityId(saved, 99L);
            return saved;
        });

        Long attemptId = practiceService.startAttempt(1L, 10L, 20L, 2L);

        assertEquals(99L, attemptId);
        org.mockito.ArgumentCaptor<PracticeAttempt> captor = org.mockito.ArgumentCaptor.forClass(PracticeAttempt.class);
        verify(attemptRepository).save(captor.capture());
        PracticeAttempt saved = captor.getValue();
        assertEquals(100L, saved.getPublishedVersionId());
        assertEquals(101L, saved.getSetVersionId());
        assertEquals(102L, saved.getTestVersionId());
        assertEquals(103L, saved.getSectionVersionId());
    }

    @Test
    void startAttemptDiscardsStaleLiveSkillAttemptAndUsesImmutableSkill() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeSet set = mock(PracticeSet.class);
        when(set.getId()).thenReturn(1L);
        when(set.getStatus()).thenReturn(PracticeSet.STATUS_PUBLISHED);
        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(setRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(set));

        com.ksh.entities.PracticeTest test = mock(com.ksh.entities.PracticeTest.class);
        when(test.getId()).thenReturn(10L);
        when(test.getSetId()).thenReturn(1L);
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(testRepository.findByIdForShare(10L)).thenReturn(Optional.of(test));

        PracticeSection liveSection = mock(PracticeSection.class);
        when(liveSection.getId()).thenReturn(20L);
        when(liveSection.getSetId()).thenReturn(1L);
        when(liveSection.getTestId()).thenReturn(10L);
        when(liveSection.getSkill()).thenReturn("READING");
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(liveSection));
        when(sectionRepository.findByIdForShare(20L)).thenReturn(Optional.of(liveSection));

        PracticeAttempt stale = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        setEntityId(stale, 88L);
        stale.lockPublishedVersion(90L, 91L, 92L, 93L);
        when(attemptRepository.findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(
                2L, 10L, 20L, PracticeAttempt.STATUS_IN_PROGRESS)).thenReturn(Optional.of(stale));

        when(versionService.latestLock(1L, 10L, 20L))
                .thenReturn(Optional.of(new PracticeAttemptVersionLock(100L, 101L, 102L, 103L)));
        PracticeVersionSnapshot snapshot = versionSnapshot("SPEAKING");
        when(versionService.snapshot(100L, 101L, 102L, 103L))
                .thenReturn(Optional.of(snapshot));
        when(attemptRepository.save(any(PracticeAttempt.class))).thenAnswer(invocation -> {
            PracticeAttempt saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setEntityId(saved, 99L);
            }
            return saved;
        });

        Long attemptId = practiceService.startAttempt(1L, 10L, 20L, 2L);

        assertEquals(99L, attemptId);
        assertEquals(PracticeAttempt.STATUS_DISCARDED, stale.getStatus());
        org.mockito.ArgumentCaptor<PracticeAttempt> captor =
                org.mockito.ArgumentCaptor.forClass(PracticeAttempt.class);
        verify(attemptRepository, times(2)).save(captor.capture());
        PracticeAttempt restarted = captor.getAllValues().get(1);
        assertEquals("SPEAKING", restarted.getSkill());
        assertEquals(100L, restarted.getPublishedVersionId());
        assertEquals(103L, restarted.getSectionVersionId());
    }

    @Test
    void speakingPlayerDeliveryCarriesQuestionImageFromImmutableContent() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        setEntityId(attempt, 77L);
        attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        when(attemptRepository.findByIdAndUserId(77L, 2L)).thenReturn(Optional.of(attempt));
        when(versionService.hasCoherentAttemptIdentity(attempt)).thenReturn(true);

        PracticePublishedVersion publishedVersion = mock(PracticePublishedVersion.class);
        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getSetId()).thenReturn(1L);
        when(setVersion.getTitle()).thenReturn("Speaking Set");
        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        when(testVersion.getTestId()).thenReturn(10L);
        when(testVersion.getTitle()).thenReturn("Test 1");
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        when(sectionVersion.getSkill()).thenReturn("SPEAKING");
        when(sectionVersion.getTitle()).thenReturn("Phần Nói");

        PracticeQuestionGroupVersion groupVersion = mock(PracticeQuestionGroupVersion.class);
        when(groupVersion.getId()).thenReturn(700L);
        when(groupVersion.getGroupLabel()).thenReturn("S1.1");

        PracticeQuestionVersion questionVersion = mock(PracticeQuestionVersion.class);
        when(questionVersion.getId()).thenReturn(800L);
        when(questionVersion.getGroupVersionId()).thenReturn(700L);
        when(questionVersion.getQuestionId()).thenReturn(11L);
        when(questionVersion.getQuestionNo()).thenReturn(1);
        when(questionVersion.getQuestionType()).thenReturn(PracticeQuestion.TYPE_SPEAKING);
        when(questionVersion.getPrompt()).thenReturn("![image](/practice/materials/legacy/content)\nYêu cầu bài làm");
        when(questionVersion.getQuestionContentJson()).thenReturn("""
                {"schemaVersion":"question-content-v1",
                 "imageReference":"/practice/materials/1/content",
                 "speakingDelivery":{
                   "promptAudioReference":"/practice/materials/5/content",
                   "promptPlayLimit":1,
                   "preparationSeconds":30,
                   "responseSeconds":60
                 }}
                """);
        when(questionVersion.getPoints()).thenReturn(BigDecimal.valueOf(100));
        when(questionVersion.getDisplayOrder()).thenReturn(0);

        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(
                new PracticeVersionSnapshot(publishedVersion, setVersion, testVersion, sectionVersion,
                        List.of(groupVersion), List.of(questionVersion))));

        PracticeService.SpeakingPlayerDelivery delivery =
                practiceService.getSpeakingPlayerDelivery(77L, 2L);

        PracticeService.SpeakingPlayerQuestion question = delivery.questions().get(0);
        assertEquals("Yêu cầu bài làm", question.prompt());
        assertEquals("/practice/materials/1/content", question.imageReference());
        assertEquals("/practice/materials/5/content", question.promptAudioReference());
    }

    @Test
    void speakingPlayerRejectsMalformedExplicitV2EvenWithLegacyGroupAudio() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        setEntityId(attempt, 77L);
        attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        when(attemptRepository.findByIdAndUserId(77L, 2L)).thenReturn(Optional.of(attempt));
        when(versionService.hasCoherentAttemptIdentity(attempt)).thenReturn(true);

        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getSetId()).thenReturn(1L);
        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        when(testVersion.getTestId()).thenReturn(10L);
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        when(sectionVersion.getSkill()).thenReturn("SPEAKING");

        PracticeQuestionGroupVersion groupVersion = mock(PracticeQuestionGroupVersion.class);
        when(groupVersion.getId()).thenReturn(700L);
        when(groupVersion.getAudioUrl())
                .thenReturn("/practice/materials/5/content");
        PracticeQuestionVersion questionVersion = mock(PracticeQuestionVersion.class);
        when(questionVersion.getId()).thenReturn(800L);
        when(questionVersion.getGroupVersionId()).thenReturn(700L);
        when(questionVersion.getQuestionId()).thenReturn(11L);
        when(questionVersion.getQuestionNo()).thenReturn(1);
        when(questionVersion.getQuestionType()).thenReturn(PracticeQuestion.TYPE_SPEAKING);
        when(questionVersion.getQuestionContentJson()).thenReturn("""
                {"schemaVersion":"question-content-v2",
                 "speakingDelivery":{
                   "inputType":"manual_text",
                   "deliveryMode":"text_only",
                   "promptAudioReference":"/practice/materials/9/content",
                   "audioOrigin":"none",
                   "promptPlayLimit":1,
                   "preparationSeconds":30,
                   "responseSeconds":60
                 }}
                """);
        when(questionVersion.getDisplayOrder()).thenReturn(0);

        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(
                new PracticeVersionSnapshot(
                        mock(PracticePublishedVersion.class), setVersion, testVersion, sectionVersion,
                        List.of(groupVersion), List.of(questionVersion))));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> practiceService.getSpeakingPlayerDelivery(77L, 2L));

        assertTrue(exception.getMessage().contains(
                "invalid immutable question-content-v2"));

        when(questionVersion.getQuestionContentJson()).thenReturn(
                "{\"schemaVersion\":\"question-content-v2\",");
        IllegalStateException syntaxDamaged = assertThrows(
                IllegalStateException.class,
                () -> practiceService.getSpeakingPlayerDelivery(77L, 2L));
        assertTrue(syntaxDamaged.getMessage().contains(
                "invalid immutable question-content-v2"));
    }

    @Test
    void speakingPlayerIgnoresExternalLegacyMarkdownImageReference() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        setEntityId(attempt, 77L);
        attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        when(attemptRepository.findByIdAndUserId(77L, 2L)).thenReturn(Optional.of(attempt));
        when(versionService.hasCoherentAttemptIdentity(attempt)).thenReturn(true);

        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getSetId()).thenReturn(1L);
        when(setVersion.getTitle()).thenReturn("Speaking Set");
        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        when(testVersion.getTestId()).thenReturn(10L);
        when(testVersion.getTitle()).thenReturn("Test 1");
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        when(sectionVersion.getSkill()).thenReturn("SPEAKING");
        when(sectionVersion.getTitle()).thenReturn("Phần Nói");

        PracticeQuestionVersion questionVersion = mock(PracticeQuestionVersion.class);
        when(questionVersion.getId()).thenReturn(800L);
        when(questionVersion.getQuestionId()).thenReturn(11L);
        when(questionVersion.getQuestionNo()).thenReturn(1);
        when(questionVersion.getQuestionType()).thenReturn(PracticeQuestion.TYPE_SPEAKING);
        when(questionVersion.getPrompt()).thenReturn("![image](https://evil.example/tracker.png)\nYêu cầu bài làm");
        when(questionVersion.getQuestionContentJson()).thenReturn("""
                {"schemaVersion":"question-content-v1",
                 "speakingDelivery":{
                   "promptAudioReference":"/practice/materials/5/content",
                   "promptPlayLimit":1,
                   "preparationSeconds":30,
                   "responseSeconds":60
                 }}
                """);
        when(questionVersion.getPoints()).thenReturn(BigDecimal.valueOf(100));
        when(questionVersion.getDisplayOrder()).thenReturn(0);

        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(
                new PracticeVersionSnapshot(mock(PracticePublishedVersion.class), setVersion, testVersion, sectionVersion,
                        List.of(), List.of(questionVersion))));

        PracticeService.SpeakingPlayerQuestion question =
                practiceService.getSpeakingPlayerDelivery(77L, 2L).questions().get(0);

        assertEquals("Yêu cầu bài làm", question.prompt());
        assertNull(question.imageReference());
    }

    @Test
    void attemptPlayerViewCarriesImmutableQuestionAndOptionMedia() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        setEntityId(attempt, 77L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        when(attemptRepository.findByIdAndUserId(77L, 2L)).thenReturn(Optional.of(attempt));
        when(versionService.hasCoherentAttemptIdentity(attempt)).thenReturn(true);

        PracticePublishedVersion publishedVersion = mock(PracticePublishedVersion.class);
        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getSetId()).thenReturn(1L);
        when(setVersion.getTitle()).thenReturn("Reading Set");
        when(setVersion.getDescription()).thenReturn("");
        when(setVersion.getSkill()).thenReturn("READING");
        when(setVersion.getMetadataJson()).thenReturn("{}");
        when(setVersion.getCreationMethod()).thenReturn("MANUAL");
        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        when(testVersion.getTestId()).thenReturn(10L);
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        when(sectionVersion.getSkill()).thenReturn("READING");
        when(sectionVersion.getTitle()).thenReturn("Reading");
        when(sectionVersion.getDurationMinutes()).thenReturn(40);

        PracticeQuestionGroupVersion groupVersion = mock(PracticeQuestionGroupVersion.class);
        when(groupVersion.getId()).thenReturn(700L);
        when(groupVersion.getGroupId()).thenReturn(70L);
        when(groupVersion.getGroupLabel()).thenReturn("R1.1");
        when(groupVersion.getQuestionFrom()).thenReturn(1);
        when(groupVersion.getQuestionTo()).thenReturn(1);
        when(groupVersion.getInstruction()).thenReturn("Đọc và chọn đáp án.");
        when(groupVersion.getStimulusType()).thenReturn("READING_PASSAGE");
        when(groupVersion.getPassageText()).thenReturn("본문");
        when(groupVersion.getImageUrl()).thenReturn("/practice/materials/7/content");
        when(groupVersion.getAudioUrl()).thenReturn(null);

        PracticeQuestionVersion questionVersion = mock(PracticeQuestionVersion.class);
        when(questionVersion.getId()).thenReturn(800L);
        when(questionVersion.getGroupVersionId()).thenReturn(700L);
        when(questionVersion.getQuestionId()).thenReturn(11L);
        when(questionVersion.getQuestionNo()).thenReturn(1);
        when(questionVersion.getQuestionType()).thenReturn(PracticeQuestion.TYPE_SINGLE_CHOICE);
        when(questionVersion.getPrompt()).thenReturn("![image](/practice/materials/legacy/content)\n무엇입니까?");
        when(questionVersion.getQuestionContentJson()).thenReturn("""
                {"schemaVersion":"question-content-v1",
                 "imageReference":"/practice/materials/8/content",
                 "audioReference":"/practice/materials/9/content",
                 "options":[
                   {"id":"opt_1","text":"A","imageReference":"/practice/materials/10/content"},
                   {"id":"opt_2","text":"B"}
                 ]}
                """);
        when(questionVersion.getOptionsJson()).thenReturn("[\"A\",\"B\"]");
        when(questionVersion.getAnswerKey()).thenReturn("1");
        when(questionVersion.getExplanation()).thenReturn("Teacher key");
        when(questionVersion.getPoints()).thenReturn(BigDecimal.valueOf(2));
        when(questionVersion.getDisplayOrder()).thenReturn(0);

        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(
                new PracticeVersionSnapshot(publishedVersion, setVersion, testVersion, sectionVersion,
                        List.of(groupVersion), List.of(questionVersion))));

        PracticeService.AttemptPlayerView playerView = practiceService.getAttemptPlayerView(77L, 2L);

        PracticeQuestionGroupRow group = playerView.view().groups().get(0);
        assertEquals("/practice/materials/7/content", group.imageUrl());
        assertEquals("본문", group.passageText());
        PracticeQuestionRow question = group.questions().get(0);
        assertEquals("무엇입니까?", question.prompt());
        assertEquals("/practice/materials/8/content", question.imageReference());
        assertEquals("/practice/materials/9/content", question.audioReference());
        assertEquals("/practice/materials/10/content", question.optionRows().get(0).imageReference());
        assertNull(question.answerKey());
        assertNull(question.explanation());

        JsonNode learnerPayload =
                objectMapper.valueToTree(playerView.view().groups());
        assertFalse(learnerPayload.toString().contains("Teacher key"));
        assertFalse(learnerPayload.toString().contains("answerKey"));
        assertFalse(learnerPayload.toString().contains("explanation"));
        assertFalse(learnerPayload.toString().contains("transcriptText"));
        assertFalse(learnerPayload.toString().contains(
                "stimulusProvenanceJson"));
    }

    @Test
    void playerDeliveryRejectsMissingLockBeforeSnapshotOrMutableContent() {
        PracticeAttempt attempt =
                new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        setEntityId(attempt, 77L);
        when(attemptRepository.findByIdAndUserId(77L, 2L))
                .thenReturn(Optional.of(attempt));

        PracticeAttemptStatePolicy.PracticeAttemptResumeNotAllowedException
                rejection = assertThrows(
                PracticeAttemptStatePolicy
                        .PracticeAttemptResumeNotAllowedException.class,
                () -> practiceService.getAttemptPlayerView(77L, 2L));

        assertEquals(
                PracticeAttemptStatePolicy.ResumeRejection
                        .INCOMPLETE_VERSION_LOCK,
                rejection.getRejection());
        assertTrue(rejection.getMessage().contains("bắt đầu lượt mới"));
        assertThrows(
                PracticeAttemptStatePolicy
                        .PracticeAttemptResumeNotAllowedException.class,
                () -> practiceService.getAttemptSectionDelivery(77L, 2L));
        assertThrows(
                PracticeAttemptStatePolicy
                        .PracticeAttemptResumeNotAllowedException.class,
                () -> practiceService.getPlayerQuestionGroupsForAttempt(
                        77L, 2L));
        verify(publishedVersionService, never())
                .hasCoherentAttemptIdentity(any());
        verify(publishedVersionService, never())
                .snapshot(any(), any(), any(), any());
        verify(setRepository, never()).findById(anyLong());
        verify(sectionRepository, never()).findById(anyLong());
        verify(groupRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(questionRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(attemptRepository, never()).save(any());
        verifyNoInteractions(
                speakingMediaService,
                speakingEvaluationService,
                evaluationClient);
    }

    @Test
    void listeningPlayerRejectsIncompatibleLockBeforeSnapshotOrMedia() {
        PracticeAttempt attempt =
                new PracticeAttempt(2L, 1L, 10L, "LISTENING", 20L);
        setEntityId(attempt, 77L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.setVersionCompatibilityStatus("STALE");
        when(attemptRepository.findByIdAndUserId(77L, 2L))
                .thenReturn(Optional.of(attempt));

        PracticeAttemptStatePolicy.PracticeAttemptResumeNotAllowedException
                rejection = assertThrows(
                PracticeAttemptStatePolicy
                        .PracticeAttemptResumeNotAllowedException.class,
                () -> practiceService
                        .getAttemptListeningPreflightDelivery(77L, 2L));

        assertEquals(
                PracticeAttemptStatePolicy.ResumeRejection
                        .INCOMPATIBLE_VERSION,
                rejection.getRejection());
        verify(publishedVersionService, never())
                .hasCoherentAttemptIdentity(any());
        verify(publishedVersionService, never())
                .snapshot(any(), any(), any(), any());
        verify(setRepository, never()).findById(anyLong());
        verify(sectionRepository, never()).findById(anyLong());
        verify(groupRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(questionRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(attemptRepository, never()).save(any());
        verifyNoInteractions(
                speakingMediaService,
                speakingEvaluationService,
                evaluationClient);
    }

    @Test
    void speakingPlayerRejectsIncoherentIdentityBeforeSnapshotOrProvider() {
        PracticeAttempt attempt =
                new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        setEntityId(attempt, 77L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        when(attemptRepository.findByIdAndUserId(77L, 2L))
                .thenReturn(Optional.of(attempt));
        when(publishedVersionService.hasCoherentAttemptIdentity(attempt))
                .thenReturn(false);

        PracticeAttemptStatePolicy.PracticeAttemptResumeNotAllowedException
                rejection = assertThrows(
                PracticeAttemptStatePolicy
                        .PracticeAttemptResumeNotAllowedException.class,
                () -> practiceService.getSpeakingPlayerDelivery(77L, 2L));

        assertEquals(
                PracticeAttemptStatePolicy.ResumeRejection
                        .INCONSISTENT_VERSION_IDENTITY,
                rejection.getRejection());
        verify(publishedVersionService)
                .hasCoherentAttemptIdentity(attempt);
        verify(publishedVersionService, never())
                .snapshot(any(), any(), any(), any());
        verify(setRepository, never()).findById(anyLong());
        verify(sectionRepository, never()).findById(anyLong());
        verify(groupRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(questionRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(attemptRepository, never()).save(any());
        verifyNoInteractions(
                speakingMediaService,
                speakingEvaluationService,
                evaluationClient);
    }


    @Test
    void publishedVersionIncludesUngroupedQuestion() {
        com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository =
                mock(com.ksh.features.practice.repository.PracticePublishedVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSetVersionRepository setVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeSetVersionRepository.class);
        com.ksh.features.practice.repository.PracticeTestVersionRepository testVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeTestVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSectionVersionRepository sectionVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeSectionVersionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository groupVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionVersionRepository questionVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSetRepository localSetRepository =
                mock(com.ksh.features.practice.repository.PracticeSetRepository.class);
        com.ksh.features.practice.repository.PracticeTestRepository localTestRepository =
                mock(com.ksh.features.practice.repository.PracticeTestRepository.class);
        com.ksh.features.practice.repository.PracticeSectionRepository localSectionRepository =
                mock(com.ksh.features.practice.repository.PracticeSectionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionGroupRepository localGroupRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionGroupRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionRepository localQuestionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionRepository.class);

        PracticeSet set = new PracticeSet("Snapshot set", "", "READING",  "GLOBAL", null, null, "{}", "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test", "", 0, 10);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading", "READING", "MCQ", "", 10, BigDecimal.ONE, 0);
        section.setTestId(10L);
        setEntityId(section, 20L);
        PracticeQuestion ungrouped = new PracticeQuestion(1L, 1, PracticeQuestion.TYPE_SINGLE_CHOICE, "Snapshot prompt",
                "[\"A\",\"B\"]", "A", "Snapshot explanation", BigDecimal.ONE, 0);
        ungrouped.setGroupId(null);
        ungrouped.setQuestionContentJson("{\"schemaVersion\":\"question-content-v1\"}");
        ungrouped.setAnswerSpecJson("{\"schemaVersion\":\"answer-spec-v1\"}");
        setEntityId(ungrouped, 11L);

        when(localSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(localTestRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(test));
        when(localSectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(localGroupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(localQuestionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(ungrouped));
        when(publishedVersionRepository.maxVersionNumberBySetId(1L)).thenReturn(0);
        when(publishedVersionRepository.save(any(PracticePublishedVersion.class))).thenAnswer(invocation -> {
            PracticePublishedVersion version = invocation.getArgument(0);
            setEntityId(version, 100L);
            return version;
        });
        when(setVersionRepository.save(any(PracticeSetVersion.class))).thenAnswer(invocation -> {
            PracticeSetVersion version = invocation.getArgument(0);
            setEntityId(version, 101L);
            return version;
        });
        when(testVersionRepository.save(any(PracticeTestVersion.class))).thenAnswer(invocation -> {
            PracticeTestVersion version = invocation.getArgument(0);
            setEntityId(version, 102L);
            return version;
        });
        when(sectionVersionRepository.save(any(PracticeSectionVersion.class))).thenAnswer(invocation -> {
            PracticeSectionVersion version = invocation.getArgument(0);
            setEntityId(version, 103L);
            return version;
        });
        when(questionVersionRepository.save(any(PracticeQuestionVersion.class))).thenAnswer(invocation -> {
            PracticeQuestionVersion version = invocation.getArgument(0);
            setEntityId(version, 104L);
            return version;
        });

        PracticePublishedVersionService service = new PracticePublishedVersionService(
                publishedVersionRepository,
                setVersionRepository,
                testVersionRepository,
                sectionVersionRepository,
                groupVersionRepository,
                questionVersionRepository,
                localSetRepository,
                localTestRepository,
                localSectionRepository,
                localGroupRepository,
                localQuestionRepository,
                objectMapper);

        service.createPublishedVersion(1L, 2L);

        org.mockito.ArgumentCaptor<PracticeQuestionVersion> captor =
                org.mockito.ArgumentCaptor.forClass(PracticeQuestionVersion.class);
        verify(questionVersionRepository).save(captor.capture());
        PracticeQuestionVersion saved = captor.getValue();
        assertEquals(100L, saved.getPublishedVersionId());
        assertEquals(103L, saved.getSectionVersionId());
        assertNull(saved.getGroupVersionId());
        assertEquals(11L, saved.getQuestionId());
        assertEquals("[\"A\",\"B\"]", saved.getOptionsJson());
        assertEquals("A", saved.getAnswerKey());
        assertEquals("Snapshot explanation", saved.getExplanation());
        assertEquals("SINGLE_CHOICE", saved.getQuestionType());
        assertEquals("{\"schemaVersion\":\"question-content-v1\"}", saved.getQuestionContentJson());
        assertEquals("{\"schemaVersion\":\"answer-spec-v1\"}", saved.getAnswerSpecJson());
        verify(setVersionRepository).save(any(PracticeSetVersion.class));
        verify(groupVersionRepository, never()).save(any());
    }

    @Test
    void publishedVersionRejectsUngroupedQuestionInMultiSectionTest() {
        com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository =
                mock(com.ksh.features.practice.repository.PracticePublishedVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSetVersionRepository setVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeSetVersionRepository.class);
        com.ksh.features.practice.repository.PracticeTestVersionRepository testVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeTestVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSectionVersionRepository sectionVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeSectionVersionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository groupVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionVersionRepository questionVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSetRepository localSetRepository =
                mock(com.ksh.features.practice.repository.PracticeSetRepository.class);
        com.ksh.features.practice.repository.PracticeTestRepository localTestRepository =
                mock(com.ksh.features.practice.repository.PracticeTestRepository.class);
        com.ksh.features.practice.repository.PracticeSectionRepository localSectionRepository =
                mock(com.ksh.features.practice.repository.PracticeSectionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionGroupRepository localGroupRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionGroupRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionRepository localQuestionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionRepository.class);

        PracticeSet set = new PracticeSet("Snapshot set", "", "READING",  "GLOBAL", null, null, "{}", "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test", "", 0, 10);
        setEntityId(test, 10L);
        PracticeSection firstSection = new PracticeSection(1L, "Reading 1", "READING", "MCQ", "", 10, BigDecimal.ONE, 0);
        firstSection.setTestId(10L);
        setEntityId(firstSection, 20L);
        PracticeSection secondSection = new PracticeSection(1L, "Reading 2", "READING", "MCQ", "", 10, BigDecimal.ONE, 1);
        secondSection.setTestId(10L);
        setEntityId(secondSection, 21L);
        PracticeQuestion ungrouped = new PracticeQuestion(1L, 1, PracticeQuestion.TYPE_SINGLE_CHOICE, "Snapshot prompt",
                "[\"A\",\"B\"]", "A", "Snapshot explanation", BigDecimal.ONE, 0);
        ungrouped.setGroupId(null);
        setEntityId(ungrouped, 11L);

        when(localSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(localTestRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(test));
        when(localSectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(firstSection, secondSection));
        when(localGroupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(localQuestionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(ungrouped));

        PracticePublishedVersionService service = new PracticePublishedVersionService(
                publishedVersionRepository,
                setVersionRepository,
                testVersionRepository,
                sectionVersionRepository,
                groupVersionRepository,
                questionVersionRepository,
                localSetRepository,
                localTestRepository,
                localSectionRepository,
                localGroupRepository,
                localQuestionRepository,
                objectMapper);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.createPublishedVersion(1L, 2L));
        assertTrue(ex.getMessage().contains("ungrouped questions are ambiguous"));
        verify(publishedVersionRepository, never()).save(any());
        verify(questionVersionRepository, never()).save(any());
    }

    @Test
    void publishedVersionRejectsUngroupedQuestionInMultiTestSingleSectionSet() {
        com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository =
                mock(com.ksh.features.practice.repository.PracticePublishedVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSetVersionRepository setVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeSetVersionRepository.class);
        com.ksh.features.practice.repository.PracticeTestVersionRepository testVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeTestVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSectionVersionRepository sectionVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeSectionVersionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository groupVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionVersionRepository questionVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSetRepository localSetRepository =
                mock(com.ksh.features.practice.repository.PracticeSetRepository.class);
        com.ksh.features.practice.repository.PracticeTestRepository localTestRepository =
                mock(com.ksh.features.practice.repository.PracticeTestRepository.class);
        com.ksh.features.practice.repository.PracticeSectionRepository localSectionRepository =
                mock(com.ksh.features.practice.repository.PracticeSectionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionGroupRepository localGroupRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionGroupRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionRepository localQuestionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionRepository.class);

        PracticeSet set = new PracticeSet("Snapshot set", "", "READING",  "GLOBAL", null, null, "{}", "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest firstTest = new com.ksh.entities.PracticeTest(1L, "Test 1", "", 0, 10);
        setEntityId(firstTest, 10L);
        com.ksh.entities.PracticeTest secondTest = new com.ksh.entities.PracticeTest(1L, "Test 2", "", 1, 10);
        setEntityId(secondTest, 11L);
        PracticeSection firstSection = new PracticeSection(1L, "Reading 1", "READING", "MCQ", "", 10, BigDecimal.ONE, 0);
        firstSection.setTestId(10L);
        setEntityId(firstSection, 20L);
        PracticeSection secondSection = new PracticeSection(1L, "Reading 2", "READING", "MCQ", "", 10, BigDecimal.ONE, 0);
        secondSection.setTestId(11L);
        setEntityId(secondSection, 21L);
        PracticeQuestion ungrouped = new PracticeQuestion(1L, 1, PracticeQuestion.TYPE_SINGLE_CHOICE, "Snapshot prompt",
                "[\"A\",\"B\"]", "A", "Snapshot explanation", BigDecimal.ONE, 0);
        ungrouped.setGroupId(null);
        setEntityId(ungrouped, 12L);

        when(localSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(localTestRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(firstTest, secondTest));
        when(localSectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(firstSection, secondSection));
        when(localGroupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(localQuestionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(ungrouped));

        PracticePublishedVersionService service = new PracticePublishedVersionService(
                publishedVersionRepository,
                setVersionRepository,
                testVersionRepository,
                sectionVersionRepository,
                groupVersionRepository,
                questionVersionRepository,
                localSetRepository,
                localTestRepository,
                localSectionRepository,
                localGroupRepository,
                localQuestionRepository,
                objectMapper);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.createPublishedVersion(1L, 2L));
        assertTrue(ex.getMessage().contains("ambiguous across multiple tests"));
        verify(publishedVersionRepository, never()).save(any());
        verify(questionVersionRepository, never()).save(any());
    }

    @Test
    void publishedVersionFailsClosedWhenContentHashCannotBeComputed() throws Exception {
        com.ksh.features.practice.repository.PracticePublishedVersionRepository publishedVersionRepository =
                mock(com.ksh.features.practice.repository.PracticePublishedVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSetVersionRepository setVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeSetVersionRepository.class);
        com.ksh.features.practice.repository.PracticeTestVersionRepository testVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeTestVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSectionVersionRepository sectionVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeSectionVersionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository groupVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionGroupVersionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionVersionRepository questionVersionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionVersionRepository.class);
        com.ksh.features.practice.repository.PracticeSetRepository localSetRepository =
                mock(com.ksh.features.practice.repository.PracticeSetRepository.class);
        com.ksh.features.practice.repository.PracticeTestRepository localTestRepository =
                mock(com.ksh.features.practice.repository.PracticeTestRepository.class);
        com.ksh.features.practice.repository.PracticeSectionRepository localSectionRepository =
                mock(com.ksh.features.practice.repository.PracticeSectionRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionGroupRepository localGroupRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionGroupRepository.class);
        com.ksh.features.practice.repository.PracticeQuestionRepository localQuestionRepository =
                mock(com.ksh.features.practice.repository.PracticeQuestionRepository.class);

        PracticeSet set = new PracticeSet("Snapshot set", "", "READING",  "GLOBAL", null, null, "{}", "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test", "", 0, 10);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading", "READING", "MCQ", "", 10, BigDecimal.ONE, 0);
        section.setTestId(10L);
        setEntityId(section, 20L);
        ObjectMapper failingMapper = spy(new ObjectMapper());
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("hash failure") {})
                .when(failingMapper).writeValueAsString(any());

        when(localSetRepository.findById(1L)).thenReturn(Optional.of(set));
        when(localTestRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(test));
        when(localSectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(localGroupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(localQuestionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(publishedVersionRepository.maxVersionNumberBySetId(1L)).thenReturn(0);

        PracticePublishedVersionService service = new PracticePublishedVersionService(
                publishedVersionRepository,
                setVersionRepository,
                testVersionRepository,
                sectionVersionRepository,
                groupVersionRepository,
                questionVersionRepository,
                localSetRepository,
                localTestRepository,
                localSectionRepository,
                localGroupRepository,
                localQuestionRepository,
                failingMapper);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.createPublishedVersion(1L, 2L));
        assertTrue(ex.getMessage().contains("content hash could not be computed"));
        verify(publishedVersionRepository, never()).save(any());
    }




    @Test
    void testReEvaluateNotFound() {
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> practiceService.reEvaluate(1L, 2L));
        assertNoReEvaluationDownstreamInteractions();
    }

    @Test
    void fullReEvaluateRejectsInProgressBeforeVersionSnapshotOrProvider() {
        PracticeAttempt attempt = reEvaluationAttempt(
                1L, "WRITING", PracticeAttempt.STATUS_IN_PROGRESS, true);
        when(attemptRepository.findByIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(attempt));

        PracticeAttemptStatePolicy.PracticeReEvaluationNotAllowedException ex =
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluate(1L, 2L));

        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection.NOT_TERMINAL,
                ex.getRejection());
        verify(publishedVersionService, never())
                .hasCoherentAttemptIdentity(any());
        assertNoReEvaluationDownstreamInteractions();
    }

    @Test
    void fullReEvaluateRejectsDiscardedBeforeVersionSnapshotOrProvider() {
        PracticeAttempt attempt = reEvaluationAttempt(
                1L, "WRITING", PracticeAttempt.STATUS_IN_PROGRESS, true);
        attempt.discard(java.time.LocalDateTime.now());
        when(attemptRepository.findByIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(attempt));

        PracticeAttemptStatePolicy.PracticeReEvaluationNotAllowedException ex =
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluate(1L, 2L));

        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection.DISCARDED,
                ex.getRejection());
        verify(publishedVersionService, never())
                .hasCoherentAttemptIdentity(any());
        assertNoReEvaluationDownstreamInteractions();
    }

    @Test
    void fullReEvaluateRejectsIncompleteAndIncompatibleLocksBeforeProvider() {
        PracticeAttempt incomplete = reEvaluationAttempt(
                1L, "WRITING", PracticeAttempt.STATUS_SUBMITTED, false);
        PracticeAttempt incompatible = reEvaluationAttempt(
                2L, "WRITING", PracticeAttempt.STATUS_SUBMITTED, true);
        incompatible.setVersionCompatibilityStatus("INCOMPATIBLE");
        when(attemptRepository.findByIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(incomplete));
        when(attemptRepository.findByIdAndUserId(2L, 2L))
                .thenReturn(Optional.of(incompatible));

        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .INCOMPLETE_VERSION_LOCK,
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluate(1L, 2L))
                        .getRejection());
        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .INCOMPATIBLE_VERSION,
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluate(2L, 2L))
                        .getRejection());

        verify(publishedVersionService, never())
                .hasCoherentAttemptIdentity(any());
        assertNoReEvaluationDownstreamInteractions();
    }

    @Test
    void fullReEvaluateRejectsInconsistentIdentityBeforeQuestionSnapshot() {
        PracticeAttempt attempt = reEvaluationAttempt(
                1L, "WRITING", PracticeAttempt.STATUS_SUBMITTED, true);
        when(attemptRepository.findByIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(attempt));
        when(publishedVersionService.hasCoherentAttemptIdentity(attempt))
                .thenReturn(false);

        PracticeAttemptStatePolicy.PracticeReEvaluationNotAllowedException ex =
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluate(1L, 2L));

        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .INCONSISTENT_VERSION_IDENTITY,
                ex.getRejection());
        assertNoReEvaluationDownstreamInteractions();
    }

    @Test
    void perQuestionGateRejectsOwnerLifecycleLockAndUnsupportedSkillEarly() {
        when(attemptRepository.findByIdAndUserId(1L, 2L))
                .thenReturn(Optional.empty());
        assertThrows(
                EntityNotFoundException.class,
                () -> practiceService.reEvaluateQuestion(1L, 10L, 2L));

        PracticeAttempt inProgress = reEvaluationAttempt(
                2L, "WRITING", PracticeAttempt.STATUS_IN_PROGRESS, true);
        PracticeAttempt reading = reEvaluationAttempt(
                3L, "READING", PracticeAttempt.STATUS_SUBMITTED, true);
        PracticeAttempt discarded = reEvaluationAttempt(
                4L, "WRITING", PracticeAttempt.STATUS_IN_PROGRESS, true);
        discarded.discard(LocalDateTime.parse("2026-07-25T12:00:00"));
        PracticeAttempt incomplete = reEvaluationAttempt(
                5L, "WRITING", PracticeAttempt.STATUS_SUBMITTED, false);
        PracticeAttempt incompatible = reEvaluationAttempt(
                6L, "WRITING", PracticeAttempt.STATUS_SUBMITTED, true);
        incompatible.setVersionCompatibilityStatus("INCOMPATIBLE");
        when(attemptRepository.findByIdAndUserId(2L, 2L))
                .thenReturn(Optional.of(inProgress));
        when(attemptRepository.findByIdAndUserId(3L, 2L))
                .thenReturn(Optional.of(reading));
        when(attemptRepository.findByIdAndUserId(4L, 2L))
                .thenReturn(Optional.of(discarded));
        when(attemptRepository.findByIdAndUserId(5L, 2L))
                .thenReturn(Optional.of(incomplete));
        when(attemptRepository.findByIdAndUserId(6L, 2L))
                .thenReturn(Optional.of(incompatible));

        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection.NOT_TERMINAL,
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluateQuestion(2L, 10L, 2L))
                        .getRejection());
        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .UNSUPPORTED_ACTION,
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluateQuestion(3L, 10L, 2L))
                        .getRejection());
        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection.DISCARDED,
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluateQuestion(
                                4L, 10L, 2L))
                        .getRejection());
        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .INCOMPLETE_VERSION_LOCK,
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluateQuestion(
                                5L, 10L, 2L))
                        .getRejection());
        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .INCOMPATIBLE_VERSION,
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluateQuestion(
                                6L, 10L, 2L))
                        .getRejection());

        verify(publishedVersionService, never())
                .hasCoherentAttemptIdentity(any());
        assertNoReEvaluationDownstreamInteractions();
    }

    @Test
    void perQuestionGateRejectsInconsistentIdentityBeforeQuestionSnapshot() {
        PracticeAttempt attempt = reEvaluationAttempt(
                7L, "WRITING", PracticeAttempt.STATUS_SUBMITTED, true);
        when(attemptRepository.findByIdAndUserId(7L, 2L))
                .thenReturn(Optional.of(attempt));
        when(publishedVersionService.hasCoherentAttemptIdentity(attempt))
                .thenReturn(false);

        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .INCONSISTENT_VERSION_IDENTITY,
                assertThrows(
                        PracticeAttemptStatePolicy
                                .PracticeReEvaluationNotAllowedException.class,
                        () -> practiceService.reEvaluateQuestion(
                                7L, 10L, 2L))
                        .getRejection());

        assertNoReEvaluationDownstreamInteractions();
    }

    @Test
    void testReEvaluateSuccess() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"10\":\"Tôi học tiếng Hàn.\"}");
        setEntityId(attempt, 1L);
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(attempt));

        PracticeSet set = new PracticeSet("Title", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeSection section = new PracticeSection(1L, "Phần Viết", "WRITING", "ESSAY", "Viết luận", 50, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findById(any())).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));

        PracticeQuestion q = new PracticeQuestion(
                1L, 54, "ESSAY", "Q",
                "[]", "", "Giải thích đáp án đúng",
                BigDecimal.valueOf(50.0), 0
        );
        q.setWritingTaskType(WritingTaskType.Q54);
        setEntityId(q, 10L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q54, "30", "current"));

        practiceService.reEvaluate(1L, 2L);
        verify(evaluationClient, times(1)).evaluate(eq(2L), anyString(), anyString(), anyBoolean(), eq(WritingTaskType.Q54));
    }

    @Test
    void testWritingReEvaluateUnavailablePreservesPreviousValidResult() {
        String oldAnswers = "{\"10\":\"Tôi học tiếng Hàn.\"}";
        String oldFeedback = "{\"10\":{\"raw_score\":30.0,\"raw_score_max\":50.0,\"summary\":\"old\"}}";
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.markGraded(BigDecimal.valueOf(60.00), BigDecimal.valueOf(50.0), oldAnswers, oldFeedback);
        setEntityId(attempt, 1L);
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(attempt));

        PracticeSet set = new PracticeSet("Title", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeSection section = new PracticeSection(1L, "Writing", "WRITING", "ESSAY", "Write", 50, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findById(any())).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));

        PracticeQuestion q = new PracticeQuestion(1L, 54, "ESSAY", "Q", "[]", "", "Explain", BigDecimal.valueOf(50.0), 0);
        q.setWritingTaskType(WritingTaskType.Q54);
        setEntityId(q, 10L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingUnavailable(
                        WritingTaskType.Q54,
                        "EVALUATION_UNAVAILABLE",
                        "PROVIDER_TRANSPORT_ERROR"));

        Long result = practiceService.reEvaluate(1L, 2L);

        assertEquals(1L, result);
        assertEquals("GRADED", attempt.getStatus());
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(60.00)));
        assertEquals(oldFeedback, attempt.getAiFeedbackJson());
    }

    @Test
    void testReEvaluateEmptyScoreSavedAsZero() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"10\":\"\"}"); // Empty answer
        setEntityId(attempt, 1L);
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(attempt));

        PracticeSet set = new PracticeSet("Title", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeSection section = new PracticeSection(1L, "Phần Viết", "WRITING", "ESSAY", "Viết luận", 50, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findById(any())).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));

        PracticeQuestion q = new PracticeQuestion(
                1L, 54, "ESSAY", "Q",
                "[]", "", "Giải thích đáp án đúng",
                BigDecimal.valueOf(50.0), 0
        );
        q.setWritingTaskType(WritingTaskType.Q54);
        setEntityId(q, 10L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        // Stub evaluate to return a contract-valid JSON with raw_score = 0.0 (spam/empty)
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingInvalid(
                        WritingTaskType.Q54, "BLANK_ANSWER"));

        practiceService.reEvaluate(1L, 2L);

        // Verify that score is saved as exactly ZERO (0.0) in attempt
        assertEquals(BigDecimal.ZERO, attempt.getScore(), "Empty score must be persisted as exactly 0");
    }




    private void setEntityId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PracticeAttempt reEvaluationAttempt(
            Long id,
            String skill,
            String status,
            boolean completeLock
    ) {
        PracticeAttempt attempt =
                new PracticeAttempt(2L, 1L, 10L, skill, 20L);
        if (completeLock) {
            attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        }
        if (PracticeAttempt.STATUS_SUBMITTED.equals(status)) {
            attempt.markSubmitted(BigDecimal.ONE, BigDecimal.TEN, "{}");
        } else if (PracticeAttempt.STATUS_GRADED.equals(status)) {
            attempt.markGraded(
                    BigDecimal.ONE, BigDecimal.TEN, "{}", "{}");
        }
        setEntityId(attempt, id);
        return attempt;
    }

    private void assertNoReEvaluationDownstreamInteractions() {
        verifyNoInteractions(
                evaluationClient,
                speakingEvaluationService,
                speakingMediaService);
        verify(publishedVersionService, never())
                .snapshot(any(), any(), any(), any());
        verify(setRepository, never()).findById(anyLong());
        verify(sectionRepository, never()).findById(anyLong());
        verify(groupRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(questionRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(questionVersionRepository, never())
                .findBySectionVersionIdOrderByDisplayOrderAscQuestionNoAscIdAsc(
                        anyLong());
        verify(attemptRepository, never()).save(any());
        verify(attemptRepository, never()).saveAndFlush(any());
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

    private PracticeAttempt arrangeObjectiveAttempt(String skill, String status, String existingAiFeedbackJson) {
        PracticeSet set = new PracticeSet(skill + " Set", "Desc", skill, "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, skill + " Section", skill, "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, skill, 20L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.setStatus(status);
        attempt.setDeadlineAt(LocalDateTime.now().plusMinutes(30));
        attempt.setAnswersJson("{\"101\":\"3\"}");
        if (existingAiFeedbackJson != null) {
            attempt.setAiFeedbackJson(existingAiFeedbackJson);
        }
        setEntityId(attempt, 99L);

        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giai thich dap an dung",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q1, 101L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(testRepository.findByIdForShare(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findByIdForShare(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByIdAndUserIdForUpdate(99L, 2L))
                .thenReturn(Optional.of(attempt));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));
        when(questionRepository.findById(101L)).thenReturn(Optional.of(q1));
        return attempt;
    }

    @Test
    void testStartAttemptValidationAndSuccess() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(testRepository.findByIdForShare(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findByIdForShare(20L)).thenReturn(Optional.of(section));
        stubCurrentReadingPublishedVersion();

        when(attemptRepository.findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        when(attemptRepository.save(any(PracticeAttempt.class))).thenAnswer(invocation -> {
            PracticeAttempt att = invocation.getArgument(0);
            setEntityId(att, 99L);
            return att;
        });

        Long attemptId = practiceService.startAttempt(1L, 10L, 20L, 2L);
        assertEquals(99L, attemptId);
    }

    @Test
    void startAttemptLifecycleLogOmitsRawUserId() {
        Long privateUserId = 987654321L;
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(testRepository.findByIdForShare(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findByIdForShare(20L)).thenReturn(Optional.of(section));
        stubCurrentReadingPublishedVersion();
        when(attemptRepository.findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(PracticeAttempt.class))).thenAnswer(invocation -> {
            PracticeAttempt att = invocation.getArgument(0);
            setEntityId(att, 99L);
            return att;
        });

        String logs = captureLogs(PracticeService.class, () ->
                assertEquals(99L, practiceService.startAttempt(1L, 10L, 20L, privateUserId)));

        assertFalse(logs.contains(String.valueOf(privateUserId)));
        assertTrue(logs.contains("PracticeAttempt id=99"));
    }

    @Test
    void testStartAttemptReuseExisting() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt existingAttempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        existingAttempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        setEntityId(existingAttempt, 88L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(testRepository.findByIdForShare(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findByIdForShare(20L)).thenReturn(Optional.of(section));
        stubCurrentReadingPublishedVersion();

        when(attemptRepository.findFirstByUserIdAndTestIdAndSectionIdAndStatusOrderByCreatedAtDesc(2L, 10L, 20L, "IN_PROGRESS"))
                .thenReturn(Optional.of(existingAttempt));

        Long attemptId = practiceService.startAttempt(1L, 10L, 20L, 2L);
        assertEquals(88L, attemptId);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void startAttemptRejectsWhenSectionDisappearsAfterSetLock() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection preLockSection = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        preLockSection.setTestId(10L);
        setEntityId(preLockSection, 20L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(setRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(preLockSection));
        when(testRepository.findByIdForShare(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findByIdForShare(20L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> practiceService.startAttempt(1L, 10L, 20L, 2L));

        verify(attemptRepository, never()).save(any());
    }

    @Test
    void testStartAttemptInvalidSectionIdThrows() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        // Section not found
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            practiceService.startAttempt(1L, 10L, 999L, 2L);
        });
    }

    @Test
    void testStartAttemptSectionNotBelongingToTestThrows() {
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(9999L); // mismatch testId
        setEntityId(section, 20L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            practiceService.startAttempt(1L, 10L, 20L, 2L);
        });
    }

    @Test
    void testSaveInProgressAnswersSuccess() throws Exception {
        PracticeSet set = new PracticeSet(
                "Reading", "Desc", "READING", "GLOBAL",
                null, null, null, "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest test =
                new com.ksh.entities.PracticeTest(
                        1L, "Test", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(
                1L, "Reading", "READING", "MCQ",
                "Instruction", 40, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        PracticeQuestionGroup group = new PracticeQuestionGroup(
                1L, "1", 1, 1, "Instruction",
                null, null, 1);
        group.setSectionId(20L);
        setEntityId(group, 30L);
        PracticeQuestion question = new PracticeQuestion(
                1L, 1, "SINGLE_CHOICE", "Prompt",
                "[\"A\",\"B\"]", "1", "Explain",
                BigDecimal.TEN, 1);
        question.setGroupId(30L);
        setEntityId(question, 101L);
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");
        attempt.setLockVersion(0L);
        attempt.setDeadlineAt(LocalDateTime.now().plusMinutes(30));
        setEntityId(attempt, 99L);
        useImmutableSnapshot(
                attempt, set, test, section, group,
                List.of(question));

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByIdAndUserIdForUpdate(99L, 2L))
                .thenReturn(Optional.of(attempt));

        Map<String, String> form = Map.of("answer_101", "3", "other_field", "value");
        practiceService.saveInProgressAnswers(99L, 2L, form);

        assertEquals(
                "{\"schemaVersion\":\"practice-attempt-answers.v2\","
                        + "\"responses\":{\"101\":{\"responseMode\":\"TEXT\","
                        + "\"text\":\"3\"}}}",
                attempt.getAnswersJson());
        verify(attemptRepository).saveAndFlush(attempt);
    }

    @Test
    void testSaveInProgressAnswersNotInProgressThrows() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");
        attempt.setLockVersion(0L);

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByIdAndUserIdForUpdate(99L, 2L))
                .thenReturn(Optional.of(attempt));

        assertThrows(IllegalStateException.class, () -> {
            practiceService.saveInProgressAnswers(99L, 2L, Map.of());
        });
    }

    @Test
    void testSubmitReadingAttemptSuccessful() {
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");
        attempt.setDeadlineAt(LocalDateTime.now().plusMinutes(30));
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByIdAndUserIdForUpdate(99L, 2L))
                .thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q1, 101L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));

        Map<String, String> form = Map.of("answer_101", "3");
        Long attemptId = practiceService.submitAttempt(99L, 2L, form);

        assertEquals(99L, attemptId);
        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
        assertEquals("{\"101\":\"3\"}", attempt.getAnswersJson());
        verify(attemptRepository).save(attempt);
    }

    @Test
    void versionLockedSingleChoiceGradingUsesAnswerSpecSnapshotInsteadOfLiveQuestion() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING",
                "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test", "", 0, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading", "READING", "DEFAULT", "",
                40, BigDecimal.valueOf(4), 0);
        section.setTestId(10L);
        setEntityId(section, 20L);
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");
        attempt.setDeadlineAt(LocalDateTime.now().plusMinutes(30));
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByIdAndUserIdForUpdate(99L, 2L))
                .thenReturn(Optional.of(attempt));

        PracticePublishedVersion publishedVersion = mock(PracticePublishedVersion.class);
        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        PracticeQuestionVersion questionVersion = mock(PracticeQuestionVersion.class);
        when(questionVersion.getId()).thenReturn(800L);
        when(questionVersion.getQuestionId()).thenReturn(101L);
        when(questionVersion.getQuestionNo()).thenReturn(1);
        when(questionVersion.getDisplayOrder()).thenReturn(0);
        when(questionVersion.getPrompt()).thenReturn("Snapshot prompt");
        when(questionVersion.getQuestionType()).thenReturn("SINGLE_CHOICE");
        when(questionVersion.getOptionsJson()).thenReturn("[\"A\",\"B\",\"C\"]");
        when(questionVersion.getQuestionContentJson()).thenReturn(questionContentJson());
        when(questionVersion.getAnswerKey()).thenReturn("1");
        when(questionVersion.getAnswerSpecJson()).thenReturn(answerSpecJson());
        when(questionVersion.getPoints()).thenReturn(BigDecimal.valueOf(4));
        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(
                new PracticeVersionSnapshot(publishedVersion, setVersion, testVersion, sectionVersion,
                        List.of(), List.of(questionVersion))));

        PracticeQuestion liveQuestion = typedSingleChoiceQuestion();
        liveQuestion.setAnswerSpecJson("""
                {"schemaVersion":"answer-spec-v1","questionType":"SINGLE_CHOICE",\
                "correctOptionIds":["opt_2"],"scoringPolicyCode":"ALL_OR_NOTHING"}
                """);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(liveQuestion));
        String typedAnswer = """
                {"schemaVersion":"learner-answer-v1","questionType":"SINGLE_CHOICE","selectedOptionIds":["opt_1"]}
                """.trim();

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", typedAnswer));

        assertEquals(0, attempt.getScore().compareTo(new BigDecimal("4")));
        verify(questionRepository, never()).findById(any());
    }

    private PracticeQuestion typedSingleChoiceQuestion() {
        PracticeQuestion question = new PracticeQuestion(
                1L, 1, "SINGLE_CHOICE", "Choose", "[\"A\",\"B\",\"C\"]", "2", "",
                BigDecimal.valueOf(4), 0);
        setEntityId(question, 101L);
        question.setQuestionContentJson(questionContentJson());
        question.setAnswerSpecJson(answerSpecJson());
        return question;
    }

    private String questionContentJson() {
        return """
                {"schemaVersion":"question-content-v1","options":[
                  {"id":"opt_1","text":"A"},{"id":"opt_2","text":"B"},{"id":"opt_3","text":"C"}
                ]}
                """;
    }

    private String answerSpecJson() {
        return """
                {"schemaVersion":"answer-spec-v1","questionType":"SINGLE_CHOICE",\
                "correctOptionIds":["opt_1"],\
                "scoringPolicyCode":"ALL_OR_NOTHING"}
                """;
    }

    @Test
    void testSubmitReadingDoesNotGenerateLegacyObjectiveExplanation() {
        String metaJson = "{\"skills\":[\"READING\"]}";
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING",  "GLOBAL", null, null, metaJson, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");
        attempt.setDeadlineAt(LocalDateTime.now().plusMinutes(30));
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByIdAndUserIdForUpdate(99L, 2L))
                .thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q1, 101L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));

        Map<String, String> form = Map.of("answer_101", "3");
        Long attemptId = practiceService.submitAttempt(99L, 2L, form);

        assertEquals(99L, attemptId);
        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
        assertNull(attempt.getAiFeedbackJson());
    }

    @Test
    void testSubmitListeningDoesNotGenerateLegacyObjectiveExplanation() {
        PracticeAttempt attempt = arrangeObjectiveAttempt("LISTENING", "IN_PROGRESS", null);
        Map<String, String> form = Map.of("answer_101", "3");

        Long attemptId = practiceService.submitAttempt(99L, 2L, form);

        assertEquals(99L, attemptId);
        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
        assertNull(attempt.getAiFeedbackJson());
    }

    @Test
    void testReEvaluateReadingDoesNotGenerateLegacyObjectiveExplanationAndPreservesLegacyFeedback() {
        String legacyFeedback = "{\"items\":[{\"questionId\":\"101\",\"meaningVi\":\"legacy\"}]}";
        PracticeAttempt attempt = arrangeObjectiveAttempt("READING", "SUBMITTED", legacyFeedback);

        Long attemptId = practiceService.reEvaluate(99L, 2L);

        assertEquals(99L, attemptId);
        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
        assertEquals(legacyFeedback, attempt.getAiFeedbackJson());
    }

    @Test
    void testReEvaluateListeningDoesNotGenerateLegacyObjectiveExplanationAndPreservesLegacyFeedback() {
        String legacyFeedback = "{\"items\":[{\"questionId\":\"101\",\"meaningVi\":\"legacy\"}]}";
        PracticeAttempt attempt = arrangeObjectiveAttempt("LISTENING", "SUBMITTED", legacyFeedback);

        Long attemptId = practiceService.reEvaluate(99L, 2L);

        assertEquals(99L, attemptId);
        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
        assertEquals(legacyFeedback, attempt.getAiFeedbackJson());
    }

    @Test
    void testSubmitAttemptIgnoresOtherSectionsQuestions() {
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");
        attempt.setDeadlineAt(LocalDateTime.now().plusMinutes(30));
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByIdAndUserIdForUpdate(99L, 2L))
                .thenReturn(Optional.of(attempt));

        // Question 1 belongs to section 20
        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q1, 101L);

        // Question 2 belongs to another section (not in section 20)
        // Since getQuestionGroupsForSection(1L, 20L) only returns question 1, question 2 is not graded
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));

        Map<String, String> form = Map.of("answer_101", "3", "answer_102", "4");
        practiceService.submitAttempt(99L, 2L, form);

        assertEquals("SUBMITTED", attempt.getStatus());
        assertEquals(BigDecimal.valueOf(5), attempt.getScore());
        assertEquals(BigDecimal.valueOf(5), attempt.getTotalPoints());
    }

    @Test
    void testSubmitAttemptAlreadySubmittedThrows() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");
        setEntityId(attempt, 99L);

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        assertThrows(IllegalStateException.class, () -> {
            practiceService.submitAttempt(99L, 2L, Map.of());
        });
    }

    @Test
    void testSubmitAttemptOtherUserThrows() {
        // Attempt owned by user 2, requested by user 3
        when(attemptRepository.findByIdAndUserId(99L, 3L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            practiceService.submitAttempt(99L, 3L, Map.of());
        });
    }




    @Test
    void testSubmitAttemptThrowsOnInvalidPointsConfig() {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        // Question 1 has points <= 0
        PracticeQuestion q1 = new PracticeQuestion(
                1L, 51, "ESSAY", "Q",
                "[]", "", "Explain",
                BigDecimal.ZERO, 0
        );
        setEntityId(q1, 101L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));

        Map<String, String> form = Map.of("answer_101", "My answer");
        assertThrows(IllegalStateException.class, () -> {
            practiceService.submitAttempt(99L, 2L, form);
        });
    }


    @Test
    void speakingAsyncOutcomeUsesActualRetryableFailureAcrossAllQuestions() {
        Map<Long, SpeakingEvaluationResult> feedback =
                new LinkedHashMap<>();
        feedback.put(
                101L,
                speakingResult(
                        SpeakingEvaluationStatus.EVALUATED,
                        true,
                        new BigDecimal("80"),
                        false));
        feedback.put(
                102L,
                speakingResult(
                        SpeakingEvaluationStatus.EVALUATION_UNAVAILABLE,
                        false,
                        null,
                        true));

        PracticeService.SpeakingOutcomeClassification classification =
                PracticeService.classifySpeakingEvaluation(
                        feedback, true);

        assertFalse(classification.succeeded());
        assertTrue(classification.retryable());
        assertEquals(
                PracticeAttemptEvaluationOutcome.FAILED,
                classification.terminalStatus());
        assertEquals(
                "SPEAKING_EVALUATION_FAILED",
                classification.errorCode());
    }

    @Test
    void speakingAsyncOutcomeRejectsUntrustedEvaluatedProfile() {
        SpeakingEvaluationResult untrusted = speakingResult(
                SpeakingEvaluationStatus.EVALUATED,
                true,
                new BigDecimal("80"),
                false,
                SpeakingContractTrust.LEGACY_UNVERIFIED);

        PracticeService.SpeakingOutcomeClassification classification =
                PracticeService.classifySpeakingEvaluation(
                        Map.of(101L, untrusted), true);

        assertFalse(classification.succeeded());
        assertFalse(classification.retryable());
        assertEquals(
                PracticeAttemptEvaluationOutcome.FAILED,
                classification.terminalStatus());
        assertEquals(
                "SPEAKING_EVALUATION_FAILED",
                classification.errorCode());
    }

    @Test
    void writingAsyncOutcomeIgnoresSuccessfulSiblingWhenProviderIsUnavailable() {
        String feedbackJson = "{\"101\":"
                + currentWritingFeedback(
                        WritingTaskType.Q51, "8", "current")
                + ",\"102\":"
                + currentWritingUnavailable(
                        WritingTaskType.Q53,
                        "EVALUATION_UNAVAILABLE",
                        "PROVIDER_TRANSPORT_ERROR")
                + "}";

        PracticeService.EvaluationFailureMetadata classification =
                practiceService.writingFailureMetadata(feedbackJson);

        assertEquals(
                PracticeAttemptEvaluationOutcome.UNAVAILABLE,
                classification.terminalStatus());
        assertEquals(
                "PROVIDER_TRANSPORT_ERROR",
                classification.errorCode());
        assertTrue(classification.retryable());
    }

    @Test
    void writingAsyncOutcomePrefersContractFailureOverUnavailableSibling() {
        String feedbackJson = "{\"101\":"
                + currentWritingUnavailable(
                        WritingTaskType.Q51,
                        "EVALUATION_UNAVAILABLE",
                        "PROVIDER_TRANSPORT_ERROR")
                + ",\"102\":"
                + currentWritingUnavailable(
                        WritingTaskType.Q53,
                        "EVALUATION_CONTRACT_FAILED",
                        "PROVIDER_CONTRACT_INVALID")
                + "}";

        PracticeService.EvaluationFailureMetadata classification =
                practiceService.writingFailureMetadata(feedbackJson);

        assertEquals(
                PracticeAttemptEvaluationOutcome.FAILED,
                classification.terminalStatus());
        assertEquals(
                "PROVIDER_CONTRACT_INVALID",
                classification.errorCode());
        assertTrue(classification.retryable());
    }

    @Test
    void writingAsyncOutcomeRejectsStaleScoreBearingEnvelope() {
        PracticeService.EvaluationFailureMetadata classification =
                practiceService.writingFailureMetadata("""
                        {
                          "101":{
                            "evaluation_status":"EVALUATED",
                            "score_available":true,
                            "raw_score":8,
                            "raw_score_max":10
                          }
                        }
                        """);

        assertEquals(
                PracticeAttemptEvaluationOutcome.FAILED,
                classification.terminalStatus());
        assertEquals(
                "WRITING_EVALUATION_CONTRACT_FAILED",
                classification.errorCode());
        assertFalse(classification.retryable());
    }

    @Test
    void writingAsyncOutcomeRejectsUnsupportedCurrentScoreReason() {
        PracticeService.EvaluationFailureMetadata classification =
                practiceService.writingFailureMetadata(
                        "{\"101\":"
                                + currentWritingInvalid(
                                        WritingTaskType.Q51,
                                        "EMPTY_OR_TOO_SHORT")
                                + "}");

        assertEquals(
                PracticeAttemptEvaluationOutcome.FAILED,
                classification.terminalStatus());
        assertEquals(
                "WRITING_EVALUATION_CONTRACT_FAILED",
                classification.errorCode());
        assertFalse(classification.retryable());
    }

    @Test
    void writingAsyncOutcomeRejectsRetryableCurrentScoreEnvelope() {
        String retryableScore = currentWritingFeedback(
                WritingTaskType.Q51, "8", "current")
                .replace(
                        "\"evaluation_retryable\":false",
                        "\"evaluation_retryable\":true");

        PracticeService.EvaluationFailureMetadata classification =
                practiceService.writingFailureMetadata(
                        "{\"101\":" + retryableScore + "}");

        assertEquals(
                PracticeAttemptEvaluationOutcome.FAILED,
                classification.terminalStatus());
        assertEquals(
                "WRITING_EVALUATION_CONTRACT_FAILED",
                classification.errorCode());
        assertFalse(classification.retryable());
    }

    @Test
    void writingAsyncOutcomeRejectsUnavailableEnvelopeWithoutCurrentIdentity() {
        PracticeService.EvaluationFailureMetadata classification =
                practiceService.writingFailureMetadata("""
                        {
                          "101":{
                            "evaluation_status":"EVALUATION_UNAVAILABLE",
                            "evaluation_source":"PROVIDER",
                            "evaluation_reason":"PROVIDER_TRANSPORT_ERROR",
                            "evaluation_retryable":true,
                            "score_available":false
                          }
                        }
                        """);

        assertEquals(
                PracticeAttemptEvaluationOutcome.FAILED,
                classification.terminalStatus());
        assertEquals(
                "WRITING_EVALUATION_CONTRACT_FAILED",
                classification.errorCode());
        assertFalse(classification.retryable());
    }

    @Test
    void speakingSubmitAggregatesMultipleQuestionsAndPersistsFeedbackMap() throws Exception {
        PracticeSet set = new PracticeSet("Speaking Set", "Desc", "SPEAKING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Speaking Section", "SPEAKING", "ORAL", "Instruction", 30, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getId()).thenReturn(30L);
        when(group.getGroupLabel()).thenReturn("1-2");
        when(group.getQuestionFrom()).thenReturn(0);
        when(group.getQuestionTo()).thenReturn(2);
        when(group.getInstruction()).thenReturn("Prompt group");
        when(group.getSectionId()).thenReturn(20L);
        when(group.getDisplayOrder()).thenReturn(0);

        PracticeQuestion q1 = new PracticeQuestion(1L, 1, "SPEAKING", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        PracticeQuestion q2 = new PracticeQuestion(1L, 2, "SPEAKING", "Prompt 2", "[]", "", "Explain", BigDecimal.valueOf(30), 1);
        setEntityId(q1, 101L);
        setEntityId(q2, 102L);
        q1.setGroupId(30L);
        q2.setGroupId(30L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);
        useImmutableSnapshot(attempt, set, test, section, group, List.of(q1, q2));

        PracticeSpeakingMediaService speakingMediaService = mock(PracticeSpeakingMediaService.class);
        practiceService.setSpeakingMediaService(speakingMediaService);
        SpeakingEvaluationApplicationService speakingService = mock(SpeakingEvaluationApplicationService.class);
        when(speakingService.enabled()).thenReturn(true);
        when(speakingService.evaluateQuestion(any(SpeakingEvaluationApplicationService.EvaluationInput.class)))
                .thenAnswer(invocation -> {
                    SpeakingEvaluationApplicationService.EvaluationInput input = invocation.getArgument(0);
                    BigDecimal score = input.questionId().equals(101L)
                            ? new BigDecimal("60")
                            : new BigDecimal("80");
                    SpeakingEvaluationResult result = speakingResult(
                            SpeakingEvaluationStatus.EVALUATED, true, score, false);
                    return new SpeakingEvaluationApplicationService.Evaluation(result, false, false, "EVALUATED");
                });
        practiceService.setSpeakingEvaluationApplicationService(speakingService);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        practiceService.submitAttempt(99L, 2L, Map.of());

        JsonNode feedbackRoot = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertTrue(feedbackRoot.path("speaking_feedback_by_question").has("101"));
        assertTrue(feedbackRoot.path("speaking_feedback_by_question").has("102"));
        assertTrue(attempt.getAnswersJson().contains("AUDIO_SUBMITTED"));
        assertNull(attempt.getScore());
        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(40)));

        String submittedFeedback = attempt.getAiFeedbackJson();
        PracticeAttemptStatePolicy.PracticeReEvaluationNotAllowedException
                rejection = assertThrows(
                PracticeAttemptStatePolicy
                        .PracticeReEvaluationNotAllowedException.class,
                () -> practiceService.reEvaluate(99L, 2L));

        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .UNSUPPORTED_ACTION,
                rejection.getRejection());
        assertEquals(submittedFeedback, attempt.getAiFeedbackJson());
        assertNull(attempt.getScore());
        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(40)));

        verify(speakingMediaService).requireReadyMediaForOwner(2L, 99L, List.of(101L, 102L));
        verify(speakingService, times(2)).evaluateQuestion(any(SpeakingEvaluationApplicationService.EvaluationInput.class));
        verify(evaluationClient, never()).evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void speakingAiSubmitEvaluatesOnceAndPersistsVersionedEnvelope() throws Exception {
        PracticeSet set = new PracticeSet("Speaking AI Set", "Desc", "SPEAKING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Speaking Section", "SPEAKING", "ORAL", "Instruction", 30, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getId()).thenReturn(30L);
        when(group.getGroupLabel()).thenReturn("1-2");
        when(group.getQuestionFrom()).thenReturn(0);
        when(group.getQuestionTo()).thenReturn(2);
        when(group.getInstruction()).thenReturn("Prompt group");
        when(group.getSectionId()).thenReturn(20L);
        when(group.getDisplayOrder()).thenReturn(0);

        PracticeQuestion speaking = new PracticeQuestion(1L, 1, "SPEAKING", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 1);
        setEntityId(speaking, 101L);
        speaking.setGroupId(30L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);
        useImmutableSnapshot(attempt, set, test, section, group, List.of(speaking));

        PracticeSpeakingMediaService speakingMediaService = mock(PracticeSpeakingMediaService.class);
        practiceService.setSpeakingMediaService(speakingMediaService);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(speaking));

        SpeakingEvaluationApplicationService speakingService = mock(SpeakingEvaluationApplicationService.class);
        SpeakingEvaluationResult result = speakingResult(SpeakingEvaluationStatus.EVALUATED, true, new BigDecimal("80"), false);
        when(speakingService.enabled()).thenReturn(true);
        when(speakingService.evaluateQuestion(any(SpeakingEvaluationApplicationService.EvaluationInput.class)))
                .thenReturn(new SpeakingEvaluationApplicationService.Evaluation(result, false, false, "EVALUATED"));
        practiceService.setSpeakingEvaluationApplicationService(speakingService);

        practiceService.submitAttempt(99L, 2L, Map.of());

        assertEquals("GRADED", attempt.getStatus());
        assertNull(attempt.getScore());
        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals("speaking_ai_v1", feedback.path("_contract").asText());
        assertTrue(feedback.path("speaking_feedback_by_question").has("101"));
        JsonNode persisted = feedback.path("speaking_feedback_by_question").path("101");
        assertFalse(persisted.path("scoreAvailable").asBoolean(true));
        assertTrue(persisted.path("overallScore").isNull());
        assertEquals("TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION",
                persisted.path("evaluatorCapability").asText());
        assertFalse(attempt.getAiFeedbackJson().contains("provider_raw_body"));
        verify(speakingMediaService).requireReadyMediaForOwner(2L, 99L, List.of(101L));
        verify(speakingService, times(1)).evaluateQuestion(any(SpeakingEvaluationApplicationService.EvaluationInput.class));
        verify(evaluationClient, never()).evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void speakingReEvaluateFailsClosedBeforeSnapshotMediaProviderOrMutation() {
        PracticeSet set = new PracticeSet("Speaking AI Set", "Desc", "SPEAKING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Speaking Section", "SPEAKING", "ORAL", "Instruction", 30, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getId()).thenReturn(30L);
        when(group.getGroupLabel()).thenReturn("1");
        when(group.getQuestionFrom()).thenReturn(1);
        when(group.getQuestionTo()).thenReturn(1);
        when(group.getInstruction()).thenReturn("Prompt group");
        when(group.getSectionId()).thenReturn(20L);
        when(group.getDisplayOrder()).thenReturn(0);

        PracticeQuestion speaking = new PracticeQuestion(1L, 1, "SPEAKING", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(speaking, 101L);
        speaking.setGroupId(30L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.markGraded(new BigDecimal("70.00"), BigDecimal.TEN, "{\"101\":\"저는 학생입니다.\"}", "{\"score\":7.0,\"summary_vi\":\"legacy\"}");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(speaking));

        SpeakingEvaluationApplicationService speakingService = mock(SpeakingEvaluationApplicationService.class);
        when(speakingService.enabled()).thenReturn(true);
        practiceService.setSpeakingEvaluationApplicationService(speakingService);
        PracticeSpeakingMediaService mediaService =
                mock(PracticeSpeakingMediaService.class);
        practiceService.setSpeakingMediaService(mediaService);

        PracticeAttemptStatePolicy.PracticeReEvaluationNotAllowedException
                rejection = assertThrows(
                PracticeAttemptStatePolicy
                        .PracticeReEvaluationNotAllowedException.class,
                () -> practiceService.reEvaluate(99L, 2L));

        assertEquals(
                PracticeAttemptStatePolicy.ReEvaluationRejection
                        .UNSUPPORTED_ACTION,
                rejection.getRejection());
        verify(speakingService, never()).evaluateQuestion(any(SpeakingEvaluationApplicationService.EvaluationInput.class));
        verifyNoInteractions(evaluationClient);
        verifyNoInteractions(mediaService);
        verify(sectionRepository, never()).findById(anyLong());
        verify(groupRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(questionRepository, never())
                .findBySetIdOrderByDisplayOrderAsc(anyLong());
        verify(questionVersionRepository, never())
                .findBySectionVersionIdOrderByDisplayOrderAscQuestionNoAscIdAsc(
                        anyLong());
        verify(attemptRepository, never()).save(any());
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(publishedVersionService, never())
                .hasCoherentAttemptIdentity(any());
    }



    private void assertMixedEnvelope(PracticeAttempt attempt) throws Exception {
        JsonNode root = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals("speaking_mixed_v1", root.path("_contract").asText());
        assertTrue(root.path("speaking_feedback_by_question").path("101").isObject());
        assertTrue(root.path("essay_feedback_by_question").path("202").isObject());
        assertFalse(root.path("speaking_feedback_by_question").has("202"));
        assertFalse(root.path("essay_feedback_by_question").has("101"));
        assertFalse(attempt.getAiFeedbackJson().contains("SPEAKING_PRIVATE_SENTINEL_MIXED"));
        assertFalse(attempt.getAiFeedbackJson().contains("ESSAY_PRIVATE_SENTINEL_MIXED"));
        assertTrue(attempt.getAnswersJson().contains("SPEAKING_PRIVATE_SENTINEL_MIXED"));
        assertTrue(attempt.getAnswersJson().contains("ESSAY_PRIVATE_SENTINEL_MIXED"));
    }


    @Test
    void testWritingAggregationWithMcqAndEssay() throws Exception {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        // MCQ Question (Points 5.0)
        PracticeQuestion qMcq = new PracticeQuestion(
                1L, 10, "MCQ", "Q1",
                "[\"1\",\"2\",\"3\"]", "3", "Explain",
                BigDecimal.valueOf(5.0), 0
        );
        setEntityId(qMcq, 101L);

        // Canonical Q51 Writing question (10 points).
        PracticeQuestion qEssay = new PracticeQuestion(
                1L, 51, "ESSAY", "Q2",
                "[]", "", "Explain",
                BigDecimal.TEN, 0
        );
        qEssay.setWritingTaskType(WritingTaskType.Q51);
        setEntityId(qEssay, 102L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(qMcq, qEssay));

        when(evaluationClient.evaluate(
                anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q51, "6", "current"));

        Map<String, String> form = Map.of("answer_101", "3", "answer_102", "My essay");
        practiceService.submitAttempt(99L, 2L, form);

        assertEquals("GRADED", attempt.getStatus());
        assertEquals(
                0,
                attempt.getScore().compareTo(
                        new BigDecimal("73.33")));
        assertEquals(
                0,
                attempt.getTotalPoints().compareTo(
                        BigDecimal.valueOf(15.0)));
    }

    @Test
    void testWritingSubmitUnavailableStoresFeedbackWithoutFakeScore() throws Exception {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        q.setWritingTaskType(WritingTaskType.Q51);
        setEntityId(q, 101L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingUnavailable(
                        WritingTaskType.Q51,
                        "EVALUATION_UNAVAILABLE",
                        "MISSING_API_KEY"));

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1"));

        assertEquals("SUBMITTED", attempt.getStatus());
        assertNull(attempt.getScore());
        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals("EVALUATION_UNAVAILABLE", feedback.get("101").path("evaluation_status").asText());
        assertFalse(feedback.get("101").path("score_available").asBoolean(true));
        assertFalse(feedback.get("101").has("raw_score"));
    }

    @Test
    void testWritingSubmitContractFailedStoresFeedbackWithoutFakeScore() throws Exception {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        q.setWritingTaskType(WritingTaskType.Q51);
        setEntityId(q, 101L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingUnavailable(
                        WritingTaskType.Q51,
                        "EVALUATION_CONTRACT_FAILED",
                        "PROVIDER_CONTRACT_INVALID"));

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1"));

        assertEquals("SUBMITTED", attempt.getStatus());
        assertNull(attempt.getScore());
        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals("EVALUATION_CONTRACT_FAILED", feedback.get("101").path("evaluation_status").asText());
        assertFalse(feedback.get("101").path("score_available").asBoolean(true));
        assertFalse(feedback.get("101").has("raw_score"));
    }

    @Test
    void testWritingFeedbackMapWritesObjectValuesNotTextualJson() throws Exception {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        q1.setWritingTaskType(WritingTaskType.Q51);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 53, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 1);
        q2.setWritingTaskType(WritingTaskType.Q53);
        setEntityId(q2, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        when(evaluationClient.evaluate(anyLong(), eq("Q1"), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q51, "8", "first"));
        when(evaluationClient.evaluate(anyLong(), eq("Q2"), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "21", "second"));

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1", "answer_102", "A2"));

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertTrue(root.get("101").isObject());
        assertFalse(root.get("101").isTextual());
        assertTrue(root.get("102").isObject());
        assertFalse(root.get("102").isTextual());
    }

    @Test
    void testWritingAggregationRejectsOutOfRangeCurrentScores() {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        q1.setWritingTaskType(WritingTaskType.Q51);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 53, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 1);
        q2.setWritingTaskType(WritingTaskType.Q53);
        setEntityId(q2, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        when(evaluationClient.evaluate(anyLong(), eq("Q1"), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q51, "-5", "invalid"));
        when(evaluationClient.evaluate(anyLong(), eq("Q2"), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "40", "invalid"));

        assertThrows(
                IllegalStateException.class,
                () -> practiceService.submitAttempt(
                        99L,
                        2L,
                        Map.of("answer_101", "A1", "answer_102", "A2")));

        assertEquals("IN_PROGRESS", attempt.getStatus());
        assertNull(attempt.getScore());
    }

    @Test
    void testWritingAggregationDoesNotApplyAdditionalLengthPenalty() {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        q1.setWritingTaskType(WritingTaskType.Q51);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 53, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 1);
        q2.setWritingTaskType(WritingTaskType.Q53);
        setEntityId(q2, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        when(evaluationClient.evaluate(
                anyLong(), eq("Q1"), eq("짧은 답"), eq(false),
                eq(WritingTaskType.Q51)))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q51, "5", "short"));
        when(evaluationClient.evaluate(
                anyLong(), eq("Q2"), anyString(), eq(false),
                eq(WritingTaskType.Q53)))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "15", "long"));

        practiceService.submitAttempt(99L, 2L, Map.of(
                "answer_101", "짧은 답",
                "answer_102", "긴 답 ".repeat(80)
        ));

        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(40.0)));
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(50.00)));
        verify(evaluationClient).evaluate(
                eq(2L), eq("Q1"), eq("짧은 답"), eq(false),
                eq(WritingTaskType.Q51));
        verify(evaluationClient).evaluate(
                eq(2L), eq("Q2"), anyString(), eq(false),
                eq(WritingTaskType.Q53));
    }

    @Test
    void testWritingAggregationRejectsNonCanonicalConfiguredQuestionPoints() {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion qConfiguredThirty = new PracticeQuestion(1L, 53, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.valueOf(30.0), 0);
        qConfiguredThirty.setWritingTaskType(WritingTaskType.Q53);
        setEntityId(qConfiguredThirty, 101L);
        PracticeQuestion qConfiguredFifteen = new PracticeQuestion(1L, 53, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(15.0), 1);
        qConfiguredFifteen.setWritingTaskType(WritingTaskType.Q53);
        setEntityId(qConfiguredFifteen, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(qConfiguredThirty, qConfiguredFifteen));

        when(evaluationClient.evaluate(anyLong(), eq("Q1"), anyString(), anyBoolean(), eq(WritingTaskType.Q53)))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "24", "first"));
        when(evaluationClient.evaluate(anyLong(), eq("Q2"), anyString(), anyBoolean(), eq(WritingTaskType.Q53)))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "24", "second"));

        assertThrows(
                IllegalStateException.class,
                () -> practiceService.submitAttempt(
                        99L,
                        2L,
                        Map.of("answer_101", "A1", "answer_102", "A2")));

        assertEquals("IN_PROGRESS", attempt.getStatus());
        assertNull(attempt.getAiFeedbackJson());
    }

    @Test
    void testWritingSubmitConflictsWhenLockVersionChangesBeforePersist() {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt snapshotAttempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        snapshotAttempt.setStatus("IN_PROGRESS");
        snapshotAttempt.setLockVersion(0L);
        setEntityId(snapshotAttempt, 99L);

        PracticeAttempt changedAttempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        changedAttempt.setStatus("IN_PROGRESS");
        changedAttempt.setLockVersion(1L);
        setEntityId(changedAttempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L))
                .thenReturn(Optional.of(snapshotAttempt), Optional.of(changedAttempt));

        PracticeQuestion q = new PracticeQuestion(1L, 53, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.valueOf(30), 0);
        q.setWritingTaskType(WritingTaskType.Q53);
        setEntityId(q, 101L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "24", "current"));

        PracticeAttemptConflictException ex = assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1")));

        assertTrue(ex.getMessage().contains("Bài làm đã thay đổi"));
        verify(evaluationClient, times(1)).evaluate(eq(2L), eq("Q1"), eq("A1"), eq(false), eq(WritingTaskType.Q53));
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void testWritingSubmitMapsOptimisticLockFailureToConflict() {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("IN_PROGRESS");
        attempt.setLockVersion(0L);
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        PracticeQuestion q = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        q.setWritingTaskType(WritingTaskType.Q51);
        setEntityId(q, 101L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q51, "8", "current"));
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(PracticeAttempt.class, 99L))
                .when(attemptRepository).saveAndFlush(attempt);

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1")));
    }

    @Test
    void testWritingQuestionReEvaluateReplacesTargetOnlyAndRecomputesScore() throws Exception {
        PracticeAttempt attempt = arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                currentWritingMap(
                        currentWritingFeedback(
                                WritingTaskType.Q51, "6", "keep"),
                        currentWritingFeedback(
                                WritingTaskType.Q53, "15", "old")),
                true);
        JsonNode oldNonTarget = objectMapper.readTree(attempt.getAiFeedbackJson()).get("102");

        when(evaluationClient.evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "24", "new"));

        Long result = practiceService.reEvaluateQuestion(99L, 103L, 2L);

        assertEquals(99L, result);
        assertEquals("GRADED", attempt.getStatus());
        assertEquals("{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}", attempt.getAnswersJson());
        assertEquals(
                0,
                attempt.getScore().compareTo(
                        new BigDecimal("77.78")));
        assertEquals(
                0,
                attempt.getTotalPoints().compareTo(
                        BigDecimal.valueOf(45.0)));
        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals(oldNonTarget, feedback.get("102"));
        assertEquals(24.0,
                feedback.get("103").path("raw_score").asDouble());
        assertTrue(feedback.get("103").isObject());
        assertFalse(feedback.get("103").isTextual());
        verify(evaluationClient, times(1)).evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), eq(WritingTaskType.Q53));
        verify(evaluationClient, never()).evaluate(eq(2L), eq("Q1"), anyString(), anyBoolean(), any());
    }

    @Test
    void testWritingQuestionReEvaluateUnavailablePreservesTargetAndAggregate() throws Exception {
        String oldFeedback = currentWritingMap(
                currentWritingFeedback(
                        WritingTaskType.Q51, "6", "keep"),
                currentWritingFeedback(
                        WritingTaskType.Q53, "15", "old"));
        PracticeAttempt attempt = arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                oldFeedback,
                true);
        BigDecimal oldScore = attempt.getScore();

        when(evaluationClient.evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), any()))
                .thenReturn(currentWritingUnavailable(
                        WritingTaskType.Q53,
                        "EVALUATION_UNAVAILABLE",
                        "PROVIDER_HTTP_ERROR"));

        Long result = practiceService.reEvaluateQuestion(99L, 103L, 2L);

        assertEquals(99L, result);
        assertEquals(0, attempt.getScore().compareTo(oldScore));
        assertEquals(oldFeedback, attempt.getAiFeedbackJson());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @Test
    void testWritingQuestionReEvaluateBlocksLegacyFlatMultiEssayBeforeEvaluator() {
        arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                "{\"raw_score\":6.0,\"raw_score_max\":10.0,\"student_text\":\"A1\"}",
                true);

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.reEvaluateQuestion(99L, 102L, 2L));

        verifyNoInteractions(evaluationClient);
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void testWritingQuestionReEvaluateBlocksLegacyFlatSingleEssayBeforeEvaluator() {
        arrangeSingleEssayWritingQuestionReEvaluationAttempt(
                "{\"102\":\"A1\"}",
                "{\"raw_score\":6.0,\"raw_score_max\":10.0,\"student_text\":\"A1\"}");

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.reEvaluateQuestion(
                        99L, 102L, 2L));

        verifyNoInteractions(evaluationClient);
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void testWritingQuestionReEvaluateBlocksMalformedNonTargetBeforeEvaluator() {
        arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                "{\"102\":{\"summary\":\"missing raw\"},\"103\":{\"raw_score\":15.0,\"raw_score_max\":30.0}}",
                true);

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.reEvaluateQuestion(99L, 103L, 2L));

        verifyNoInteractions(evaluationClient);
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void testWritingQuestionReEvaluateBlocksStaleCurrentReasonBeforeEvaluator() {
        arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                currentWritingMap(
                        currentWritingInvalid(
                                WritingTaskType.Q51,
                                "EMPTY_OR_TOO_SHORT"),
                        currentWritingFeedback(
                                WritingTaskType.Q53,
                                "15",
                                "current")),
                true);

        assertThrows(
                PracticeAttemptConflictException.class,
                () -> practiceService.reEvaluateQuestion(
                        99L, 103L, 2L));

        verifyNoInteractions(evaluationClient);
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void testWritingQuestionReEvaluateBlocksZeroRawScoreMaxBeforeEvaluator() {
        arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                "{\"102\":{\"raw_score\":6.0,\"raw_score_max\":0},\"103\":{\"raw_score\":15.0,\"raw_score_max\":30.0}}",
                true);

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.reEvaluateQuestion(99L, 103L, 2L));

        verifyNoInteractions(evaluationClient);
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void testWritingQuestionReEvaluateBlocksNonNumericRawScoreBeforeEvaluator() {
        arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                "{\"102\":{\"raw_score\":\"six\",\"raw_score_max\":10.0},\"103\":{\"raw_score\":15.0,\"raw_score_max\":30.0}}",
                true);

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.reEvaluateQuestion(99L, 103L, 2L));

        verifyNoInteractions(evaluationClient);
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void testWritingQuestionReEvaluateTargetZeroScoreRecomputesWithoutDeltaDrift() throws Exception {
        PracticeAttempt attempt = arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                currentWritingMap(
                        currentWritingFeedback(
                                WritingTaskType.Q51, "6", "keep"),
                        currentWritingFeedback(
                                WritingTaskType.Q53, "15", "old")),
                true);
        when(evaluationClient.evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "0", "invalid"));

        practiceService.reEvaluateQuestion(99L, 103L, 2L);

        assertEquals(
                0,
                attempt.getScore().compareTo(
                        new BigDecimal("24.44")));
        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals(0.0, feedback.get("103").path("raw_score").asDouble());
        verify(evaluationClient, times(1)).evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), eq(WritingTaskType.Q53));
    }

    @Test
    void testWritingQuestionReEvaluateFeedbackChangedBeforePhaseBConflictsAndPreservesOldResult() throws Exception {
        PracticeAttempt snapshotAttempt = arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                currentWritingMap(
                        currentWritingFeedback(
                                WritingTaskType.Q51, "6", "snapshot"),
                        currentWritingFeedback(
                                WritingTaskType.Q53, "15", "snapshot")),
                false);
        PracticeAttempt changedAttempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        changedAttempt.setStatus("GRADED");
        changedAttempt.setLockVersion(0L);
        changedAttempt.markGraded(
                BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(50.0),
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                currentWritingMap(
                        currentWritingFeedback(
                                WritingTaskType.Q51, "6", "changed"),
                        currentWritingFeedback(
                                WritingTaskType.Q53, "20", "changed")));
        changedAttempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        setEntityId(changedAttempt, 99L);
        when(attemptRepository.findByIdAndUserId(99L, 2L))
                .thenReturn(Optional.of(snapshotAttempt), Optional.of(changedAttempt));
        when(evaluationClient.evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), any()))
                .thenReturn(currentWritingFeedback(
                        WritingTaskType.Q53, "24", "new"));

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.reEvaluateQuestion(99L, 103L, 2L));

        assertEquals(0, changedAttempt.getScore().compareTo(BigDecimal.valueOf(50.00)));
        assertEquals(20.0, objectMapper.readTree(changedAttempt.getAiFeedbackJson()).get("103").path("raw_score").asDouble());
        verify(attemptRepository, never()).saveAndFlush(changedAttempt);
    }

    @Test
    void testWritingQuestionReEvaluateRejectsWritingMcq() {
        arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                currentWritingMap(
                        currentWritingFeedback(
                                WritingTaskType.Q51, "6", "current"),
                        currentWritingFeedback(
                                WritingTaskType.Q53, "15", "current")),
                true);

        assertThrows(IllegalArgumentException.class,
                () -> practiceService.reEvaluateQuestion(99L, 101L, 2L));

        verifyNoInteractions(evaluationClient);
    }

    private PracticeAttempt arrangeWritingQuestionReEvaluationAttempt(
            String answersJson,
            String feedbackJson,
            boolean stubAttemptLookup
    ) {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        PracticeQuestionGroup group = new PracticeQuestionGroup(1L, "Group 1", 1, 3, "Instruction", null, null, 1);
        group.setSectionId(20L);
        setEntityId(group, 30L);

        PracticeQuestion qMcq = new PracticeQuestion(1L, 10, "MCQ", "M1", "[\"1\",\"2\",\"3\"]", "3", "Explain", BigDecimal.valueOf(5.0), 0);
        qMcq.setGroupId(30L);
        setEntityId(qMcq, 101L);
        PracticeQuestion qEssay1 = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 1);
        qEssay1.setWritingTaskType(WritingTaskType.Q51);
        qEssay1.setGroupId(30L);
        setEntityId(qEssay1, 102L);
        PracticeQuestion qEssay2 = new PracticeQuestion(1L, 53, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 2);
        qEssay2.setWritingTaskType(WritingTaskType.Q53);
        qEssay2.setGroupId(30L);
        setEntityId(qEssay2, 103L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("GRADED");
        attempt.setLockVersion(0L);
        attempt.markGraded(BigDecimal.valueOf(50.00), BigDecimal.valueOf(45.0), answersJson, feedbackJson);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(qMcq, qEssay1, qEssay2));
        if (stubAttemptLookup) {
            when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        }
        return attempt;
    }

    private PracticeAttempt arrangeSingleEssayWritingQuestionReEvaluationAttempt(String answersJson, String feedbackJson) {
        PracticeSet set = new PracticeSet("Writing Set", "Desc", "WRITING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Writing Section", "WRITING", "ESSAY", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        PracticeQuestionGroup group = new PracticeQuestionGroup(1L, "Group 1", 1, 1, "Instruction", null, null, 1);
        group.setSectionId(20L);
        setEntityId(group, 30L);
        PracticeQuestion qEssay = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        qEssay.setWritingTaskType(WritingTaskType.Q51);
        qEssay.setGroupId(30L);
        setEntityId(qEssay, 102L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("GRADED");
        attempt.setLockVersion(0L);
        attempt.markGraded(BigDecimal.valueOf(60.00), BigDecimal.TEN, answersJson, feedbackJson);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(qEssay));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        return attempt;
    }

    private PracticeVersionSnapshot versionSnapshot(String skill) {
        PracticePublishedVersion published = mock(PracticePublishedVersion.class);
        when(published.getId()).thenReturn(100L);
        when(published.getSetId()).thenReturn(1L);
        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getId()).thenReturn(101L);
        when(setVersion.getSetId()).thenReturn(1L);
        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        when(testVersion.getId()).thenReturn(102L);
        when(testVersion.getTestId()).thenReturn(10L);
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getId()).thenReturn(103L);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        when(sectionVersion.getSkill()).thenReturn(skill);
        return new PracticeVersionSnapshot(
                published, setVersion, testVersion, sectionVersion, List.of(), List.of());
    }

    private void stubCurrentReadingPublishedVersion() {
        when(publishedVersionService.latestLock(1L, 10L, 20L))
                .thenReturn(Optional.of(
                        new PracticeAttemptVersionLock(
                                100L, 101L, 102L, 103L)));
        PracticeVersionSnapshot snapshot = versionSnapshot("READING");
        when(publishedVersionService.snapshot(100L, 101L, 102L, 103L))
                .thenReturn(Optional.of(snapshot));
    }

    private void useImmutableSnapshot(
            PracticeAttempt attempt,
            PracticeSet set,
            com.ksh.entities.PracticeTest test,
            PracticeSection section,
            PracticeQuestionGroup group,
            List<PracticeQuestion> questions
    ) {
        PracticePublishedVersion published = new PracticePublishedVersion(
                set.getId(), 1, PracticePublishedVersion.STATUS_PUBLISHED, "hash", 1L);
        setEntityId(published, 100L);
        PracticeSetVersion setVersion = new PracticeSetVersion(100L, set);
        setEntityId(setVersion, 101L);
        PracticeTestVersion testVersion = new PracticeTestVersion(100L, 101L, test);
        setEntityId(testVersion, 102L);
        PracticeSectionVersion sectionVersion = new PracticeSectionVersion(100L, 102L, section);
        setEntityId(sectionVersion, 103L);
        PracticeQuestionGroupVersion groupVersion = new PracticeQuestionGroupVersion(100L, 103L, group);
        setEntityId(groupVersion, 104L);

        List<PracticeQuestionVersion> questionVersions = new ArrayList<>();
        long versionId = 200L;
        for (PracticeQuestion question : questions) {
            PracticeQuestionVersion questionVersion = new PracticeQuestionVersion(
                    100L, 103L, 104L, question);
            setEntityId(questionVersion, versionId++);
            questionVersions.add(questionVersion);
        }

        PracticeVersionSnapshot snapshot = new PracticeVersionSnapshot(
                published,
                setVersion,
                testVersion,
                sectionVersion,
                List.of(groupVersion),
                questionVersions
        );
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(snapshot));
        when(versionService.hasCoherentAttemptIdentity(attempt)).thenReturn(true);
        practiceService.setPublishedVersionServiceForTests(versionService);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
    }

    private String currentWritingFeedback(
            WritingTaskType taskType,
            String rawScore,
            String summary
    ) {
        int requested = new BigDecimal(rawScore).intValueExact();
        int maximum = WritingScoringPolicy.rubricFor(
                taskType.name()).totalMaxScore();
        int boundedScore = Math.max(0, Math.min(requested, maximum));
        String learnerAnswer =
                WritingContractTestFixtures.scoreBearingLearnerAnswer(
                        taskType.name(), boundedScore);
        String normalized = WritingContractTestFixtures.normalizedFeedback(
                objectMapper,
                taskType.name(),
                learnerAnswer,
                envelope -> WritingContractTestFixtures.applyRawScore(
                        envelope,
                        taskType.name(),
                        learnerAnswer,
                        boundedScore));
        if (requested >= 0 && requested <= maximum) {
            return normalized;
        }
        try {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode)
                    objectMapper.readTree(normalized);
            node.put("raw_score", requested);
            return node.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String currentWritingInvalid(
            WritingTaskType taskType,
            String reason
    ) {
        com.fasterxml.jackson.databind.node.ObjectNode node;
        try {
            node = (com.fasterxml.jackson.databind.node.ObjectNode)
                    objectMapper.readTree(currentWritingFeedback(
                            taskType, "0", "invalid"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        node.put(
                "evaluation_status",
                "INVALID_LEARNER_RESPONSE");
        node.put("evaluation_source", "BACKEND_RULE");
        node.put("evaluation_reason", reason);
        return node.toString();
    }

    private String currentWritingUnavailable(
            WritingTaskType taskType,
            String status,
            String reason
    ) {
        com.fasterxml.jackson.databind.node.ObjectNode node =
                objectMapper.createObjectNode();
        node.put("task_type", taskType.name());
        node.put("engine", "KSH_WRITING_EVALUATOR_STATUS");
        node.put(
                "policy_bundle_id",
                "KSH_WRITING_POLICY_BUNDLE_V3");
        node.put("evaluation_status", status);
        node.put("evaluation_source", "PROVIDER");
        node.put("evaluation_reason", reason);
        node.put("evaluation_retryable", true);
        node.put("score_available", false);
        return node.toString();
    }

    private static String currentWritingMap(
            String firstQuestion,
            String secondQuestion
    ) {
        return "{\"102\":" + firstQuestion
                + ",\"103\":" + secondQuestion + "}";
    }

    private SpeakingEvaluationResult speakingResult(
            SpeakingEvaluationStatus status,
            boolean scoreAvailable,
            BigDecimal overallScore,
            boolean retryable
    ) {
        return speakingResult(
                status,
                scoreAvailable,
                overallScore,
                retryable,
                SpeakingContractTrust.CURRENT_VERIFIED);
    }

    private SpeakingEvaluationResult speakingResult(
            SpeakingEvaluationStatus status,
            boolean scoreAvailable,
            BigDecimal overallScore,
            boolean retryable,
            SpeakingContractTrust contractTrust
    ) {
        return new SpeakingEvaluationResult(
                status,
                scoreAvailable,
                SpeakingEvaluationSource.PROVIDER,
                "models/gemini-2.5-flash",
                "gpt-4o-mini-transcribe",
                SpeakingPromptRules.PROMPT_VERSION,
                SpeakingPromptRules.RUBRIC_VERSION,
                SpeakingPromptRules.SCHEMA_VERSION,
                com.ksh.features.practice.ai.speaking
                        .SpeakingAssessmentPolicyBundle.POLICY_BUNDLE_ID,
                SpeakingEvaluatorCapability.TRANSCRIPT_GROUNDED_LANGUAGE_EVALUATION,
                SpeakingEvidenceMode.TRANSCRIPT_ONLY,
                SpeakingPromptRules.EVIDENCE_CONTRACT_VERSION,
                contractTrust,
                null,
                null,
                null,
                12L,
                3L,
                "저는 한국어를 공부해요.",
                "저는 한국어를 공부해요.",
                "저는 한국어를 공부해요.",
                null,
                null,
                null,
                null,
                overallScore,
                "B1",
                "Tóm tắt an toàn",
                "Hoàn thành nhiệm vụ",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                speakingLanguageProfileRows(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                scoreAvailable ? null : status.name(),
                retryable);
    }

    private List<SpeakingEvaluationResult.RubricScore> speakingLanguageProfileRows() {
        return List.of(
                speakingScored(SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT, "16", "20"),
                speakingScored(SpeakingRubricCriterion.GRAMMAR_SENTENCE_CONTROL, "16", "20"),
                speakingScored(SpeakingRubricCriterion.VOCABULARY_EXPRESSIONS, "12", "15"),
                speakingScored(SpeakingRubricCriterion.COHERENCE_ORGANIZATION, "12", "15"),
                new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.FLUENCY, null, null, "No audio",
                        com.ksh.features.practice.ai.speaking.SpeakingCriterionAvailability.NOT_SCORABLE),
                new SpeakingEvaluationResult.RubricScore(
                        SpeakingRubricCriterion.PRONUNCIATION_DELIVERY, null, null, "No audio",
                        com.ksh.features.practice.ai.speaking.SpeakingCriterionAvailability.NOT_SCORABLE));
    }

    private SpeakingEvaluationResult.RubricScore speakingScored(
            SpeakingRubricCriterion criterion,
            String score,
            String max
    ) {
        return new SpeakingEvaluationResult.RubricScore(
                criterion, new BigDecimal(score), new BigDecimal(max), "Language profile");
    }
}
