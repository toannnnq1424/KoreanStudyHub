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
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationApplicationService;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationSource;
import com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus;
import com.ksh.features.practice.ai.readinglistening.QuestionExplanationReadService;
import com.ksh.features.practice.dto.PracticeDtos.*;
import com.ksh.features.practice.repository.PracticeQuestionGroupRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PracticeServiceTest {

    private PracticeSetRepository setRepository;
    private PracticeQuestionRepository questionRepository;
    private com.ksh.features.practice.repository.PracticeQuestionVersionRepository questionVersionRepository;
    private PracticeQuestionGroupRepository groupRepository;
    private com.ksh.features.practice.repository.PracticeSectionRepository sectionRepository;
    private com.ksh.features.practice.repository.PracticeAttemptRepository attemptRepository;
    private com.ksh.features.practice.repository.PracticeTestRepository testRepository;
    private WritingEvaluationClient evaluationClient;
    private QuestionExplanationReadService explanationReadService;
    private com.ksh.common.storage.AudioStorageService audioStorageService;
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
        explanationReadService = mock(QuestionExplanationReadService.class);
        audioStorageService = mock(com.ksh.common.storage.AudioStorageService.class);
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
                explanationReadService,
                audioStorageService,
                objectMapper
        );
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
    void speakingPlayerRejectsMissingImmutablePromptAudioWithoutNullPointerFailure() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        setEntityId(attempt, 77L);
        attempt.setStatus(PracticeAttempt.STATUS_IN_PROGRESS);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        when(attemptRepository.findByIdAndUserId(77L, 2L)).thenReturn(Optional.of(attempt));

        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getSetId()).thenReturn(1L);
        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        when(testVersion.getTestId()).thenReturn(10L);
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        when(sectionVersion.getSkill()).thenReturn("SPEAKING");

        PracticeQuestionGroupVersion groupVersion = mock(PracticeQuestionGroupVersion.class);
        when(groupVersion.getId()).thenReturn(700L);
        PracticeQuestionVersion questionVersion = mock(PracticeQuestionVersion.class);
        when(questionVersion.getId()).thenReturn(800L);
        when(questionVersion.getGroupVersionId()).thenReturn(700L);
        when(questionVersion.getQuestionId()).thenReturn(11L);
        when(questionVersion.getQuestionNo()).thenReturn(1);
        when(questionVersion.getQuestionType()).thenReturn(PracticeQuestion.TYPE_SPEAKING);
        when(questionVersion.getQuestionContentJson()).thenReturn("""
                {"schemaVersion":"question-content-v1"}
                """);
        when(questionVersion.getDisplayOrder()).thenReturn(0);

        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(
                new PracticeVersionSnapshot(
                        mock(PracticePublishedVersion.class), setVersion, testVersion, sectionVersion,
                        List.of(groupVersion), List.of(questionVersion))));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> practiceService.getSpeakingPlayerDelivery(77L, 2L));

        assertTrue(exception.getMessage().contains("missing immutable prompt audio"));
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
    }

    @Test
    void resultWithVersionLockNeverFallsBackToLiveGraphWhenSnapshotIsMissing() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        setEntityId(attempt, 50L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.markSubmitted(BigDecimal.ONE, BigDecimal.ONE, "{\"11\":\"A\"}");
        when(attemptRepository.findByIdAndUserId(50L, 2L)).thenReturn(Optional.of(attempt));
        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> practiceService.getReadingListeningResult(50L, 2L));

        assertTrue(exception.getMessage().contains("snapshot hợp lệ"));
        verify(setRepository, never()).findById(anyLong());
        verify(sectionRepository, never()).findById(anyLong());
        verify(questionRepository, never()).findBySetIdOrderByDisplayOrderAsc(anyLong());
    }

    @Test
    void readingResultUsesLockedQuestionVersionAnswerAndExplanationSnapshot() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        setEntityId(attempt, 50L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.markSubmitted(BigDecimal.ONE, BigDecimal.ONE, "{\"11\":\"A\"}");
        when(attemptRepository.findByIdAndUserId(50L, 2L)).thenReturn(Optional.of(attempt));

        PracticeSet liveSet = new PracticeSet("TOPIK live title", "", "READING",  "GLOBAL", null, null, "{}", "PUBLISHED", 1L);
        setEntityId(liveSet, 1L);
        when(setRepository.findById(1L)).thenReturn(Optional.of(liveSet));

        PracticeSection liveSection = mock(PracticeSection.class);
        when(liveSection.getId()).thenReturn(20L);
        when(liveSection.getSetId()).thenReturn(1L);
        when(liveSection.getTestId()).thenReturn(10L);
        when(liveSection.getSkill()).thenReturn("READING");
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(liveSection));

        PracticePublishedVersion publishedVersion = mock(PracticePublishedVersion.class);
        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getSetId()).thenReturn(1L);
        when(setVersion.getTitle()).thenReturn("Snapshot title");
        when(setVersion.getDescription()).thenReturn("");
        when(setVersion.getSkill()).thenReturn("READING");
        when(setVersion.getMetadataJson()).thenReturn("{}");
        when(setVersion.getCreationMethod()).thenReturn("MANUAL");

        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        when(sectionVersion.getTitle()).thenReturn("Snapshot section");
        when(sectionVersion.getSkill()).thenReturn("READING");

        PracticeQuestionGroupVersion groupVersion = mock(PracticeQuestionGroupVersion.class);
        when(groupVersion.getId()).thenReturn(700L);
        when(groupVersion.getGroupId()).thenReturn(7L);
        when(groupVersion.getGroupLabel()).thenReturn("1");
        when(groupVersion.getQuestionFrom()).thenReturn(1);
        when(groupVersion.getQuestionTo()).thenReturn(1);
        when(groupVersion.getInstruction()).thenReturn("Snapshot instruction");
        when(groupVersion.getDisplayOrder()).thenReturn(0);

        PracticeQuestionVersion questionVersion = mock(PracticeQuestionVersion.class);
        when(questionVersion.getId()).thenReturn(800L);
        when(questionVersion.getGroupVersionId()).thenReturn(700L);
        when(questionVersion.getQuestionId()).thenReturn(11L);
        when(questionVersion.getQuestionNo()).thenReturn(1);
        when(questionVersion.getQuestionType()).thenReturn(PracticeQuestion.TYPE_SINGLE_CHOICE);
        when(questionVersion.getPrompt()).thenReturn("Snapshot prompt");
        when(questionVersion.getOptionsJson()).thenReturn("[\"A\",\"B\"]");
        when(questionVersion.getAnswerKey()).thenReturn("A");
        when(questionVersion.getExplanation()).thenReturn("Snapshot explanation");
        when(questionVersion.getPoints()).thenReturn(BigDecimal.ONE);
        when(questionVersion.getDisplayOrder()).thenReturn(0);

        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(
                new PracticeVersionSnapshot(publishedVersion, setVersion, testVersion, sectionVersion,
                        List.of(groupVersion), List.of(questionVersion))));

        ReadingListeningResultView result = practiceService.getReadingListeningResult(50L, 2L);

        assertEquals("Snapshot title", result.set().title());
        assertEquals(1, result.correctCount());
        assertEquals(0, result.incorrectCount());
        assertEquals("Snapshot prompt", result.groups().get(0).questions().get(0).prompt());
        assertNull(result.groups().get(0).questions().get(0).explanationJson());
        verify(explanationReadService).readDisplayJson(800L, "ALPHA");
        verify(setRepository, never()).findById(1L);
        verify(sectionRepository, never()).findById(20L);
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
    void readingResultUsesLockedUngroupedQuestionVersionSnapshot() {
        PracticePublishedVersionService versionService = mock(PracticePublishedVersionService.class);
        practiceService.setPublishedVersionServiceForTests(versionService);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        setEntityId(attempt, 50L);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        attempt.markSubmitted(BigDecimal.ONE, BigDecimal.ONE, "{\"11\":\"A\"}");
        when(attemptRepository.findByIdAndUserId(50L, 2L)).thenReturn(Optional.of(attempt));

        PracticeSet liveSet = new PracticeSet("Live title", "", "READING",  "GLOBAL", null, null, "{}", "PUBLISHED", 1L);
        setEntityId(liveSet, 1L);
        when(setRepository.findById(1L)).thenReturn(Optional.of(liveSet));

        PracticeSection liveSection = mock(PracticeSection.class);
        when(liveSection.getId()).thenReturn(20L);
        when(liveSection.getSetId()).thenReturn(1L);
        when(liveSection.getTestId()).thenReturn(10L);
        when(liveSection.getSkill()).thenReturn("READING");
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(liveSection));

        PracticePublishedVersion publishedVersion = mock(PracticePublishedVersion.class);
        PracticeSetVersion setVersion = mock(PracticeSetVersion.class);
        when(setVersion.getSetId()).thenReturn(1L);
        when(setVersion.getTitle()).thenReturn("Snapshot title");
        when(setVersion.getDescription()).thenReturn("");
        when(setVersion.getSkill()).thenReturn("READING");
        when(setVersion.getMetadataJson()).thenReturn("{}");
        when(setVersion.getCreationMethod()).thenReturn("MANUAL");

        PracticeTestVersion testVersion = mock(PracticeTestVersion.class);
        PracticeSectionVersion sectionVersion = mock(PracticeSectionVersion.class);
        when(sectionVersion.getSectionId()).thenReturn(20L);
        when(sectionVersion.getTitle()).thenReturn("Snapshot section");
        when(sectionVersion.getSkill()).thenReturn("READING");

        PracticeQuestionVersion questionVersion = mock(PracticeQuestionVersion.class);
        when(questionVersion.getId()).thenReturn(800L);
        when(questionVersion.getGroupVersionId()).thenReturn(null);
        when(questionVersion.getQuestionId()).thenReturn(11L);
        when(questionVersion.getQuestionNo()).thenReturn(1);
        when(questionVersion.getQuestionType()).thenReturn(PracticeQuestion.TYPE_SINGLE_CHOICE);
        when(questionVersion.getPrompt()).thenReturn("Snapshot prompt");
        when(questionVersion.getOptionsJson()).thenReturn("[\"A\",\"B\"]");
        when(questionVersion.getAnswerKey()).thenReturn("A");
        when(questionVersion.getExplanation()).thenReturn("Snapshot explanation");
        when(questionVersion.getPoints()).thenReturn(BigDecimal.ONE);
        when(questionVersion.getDisplayOrder()).thenReturn(0);

        when(versionService.snapshot(100L, 101L, 102L, 103L)).thenReturn(Optional.of(
                new PracticeVersionSnapshot(publishedVersion, setVersion, testVersion, sectionVersion,
                        List.of(), List.of(questionVersion))));

        ReadingListeningResultView result = practiceService.getReadingListeningResult(50L, 2L);

        assertEquals("Snapshot title", result.set().title());
        assertEquals(1, result.correctCount());
        assertEquals(0, result.incorrectCount());
        assertEquals(1, result.groups().size());
        assertEquals(1, result.groups().get(0).questions().size());
        assertNotEquals("Ungrouped", result.groups().get(0).groupLabel());
        assertEquals("Snapshot prompt", result.groups().get(0).questions().get(0).prompt());
        assertNull(result.groups().get(0).questions().get(0).explanationJson());
        verify(explanationReadService).readDisplayJson(eq(800L), anyString());
        verify(questionRepository, never()).findById(any());
    }



    @Test
    void testReEvaluateNotFound() {
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> practiceService.reEvaluate(1L, 2L));
    }

    @Test
    void testReEvaluateSuccess() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
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
                .thenReturn("{\"score\":6.0,\"overall_score\":6.0,\"raw_score\":30.0,\"raw_score_max\":50.0}");

        practiceService.reEvaluate(1L, 2L);
        verify(evaluationClient, times(1)).evaluate(eq(2L), anyString(), anyString(), anyBoolean(), eq(WritingTaskType.Q54));
    }

    @Test
    void testWritingReEvaluateUnavailablePreservesPreviousValidResult() {
        String oldAnswers = "{\"10\":\"Tôi học tiếng Hàn.\"}";
        String oldFeedback = "{\"10\":{\"raw_score\":30.0,\"raw_score_max\":50.0,\"summary\":\"old\"}}";
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
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
                .thenReturn("{\"evaluation_status\":\"EVALUATION_UNAVAILABLE\",\"evaluation_source\":\"PROVIDER\",\"evaluation_reason\":\"PROVIDER_TRANSPORT_ERROR\",\"evaluation_retryable\":true,\"score_available\":false}");

        Long result = practiceService.reEvaluate(1L, 2L);

        assertEquals(1L, result);
        assertEquals("GRADED", attempt.getStatus());
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(60.00)));
        assertEquals(oldFeedback, attempt.getAiFeedbackJson());
    }

    @Test
    void testReEvaluateEmptyScoreSavedAsZero() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
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
        setEntityId(q, 10L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        // Stub evaluate to return a contract-valid JSON with raw_score = 0.0 (spam/empty)
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn("{\"score\":0.0,\"overall_score\":0.0,\"raw_score\":0.0,\"raw_score_max\":50.0}");

        practiceService.reEvaluate(1L, 2L);

        // Verify that score is saved as exactly ZERO (0.0) in attempt
        assertEquals(BigDecimal.ZERO, attempt.getScore(), "Empty score must be persisted as exactly 0");
    }

    @Test
    void testGetResult() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"10\":\"3\"}");
        attempt.setScore(BigDecimal.TEN);
        attempt.setTotalPoints(BigDecimal.TEN);
        setEntityId(attempt, 1L);
        when(attemptRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(attempt));

        PracticeSet set = new PracticeSet("Title", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        when(setRepository.findById(any())).thenReturn(Optional.of(set));

        PracticeSection section = new PracticeSection(1L, "Section 1", "READING", "MCQ", "Desc", 40, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findById(any())).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));

        PracticeQuestion q = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        setEntityId(q, 10L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(any())).thenReturn(List.of(q));

        PracticeResultView view = practiceService.getResult(1L, 2L);
        assertNotNull(view);
        assertEquals(BigDecimal.TEN, view.score());
    }



    @Test
    void testGetLearningProgressOverview() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.markGraded(BigDecimal.valueOf(8), BigDecimal.valueOf(10), "{}", "{}");
        setEntityId(attempt, 99L);
        when(attemptRepository.findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                2L, PracticeAttempt.STATUS_DISCARDED)).thenReturn(List.of(attempt));
        when(setRepository.findAllById(any())).thenReturn(List.of(
                new PracticeSet("Reading Test", "Desc", "MIXED",  "GLOBAL", null, null, null, "PUBLISHED", 1L)));
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test 1", "Desc", 1, 40);
        setEntityId(test, 10L);
        when(testRepository.findAllById(any())).thenReturn(List.of(test));
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Desc", 40, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findAllById(any())).thenReturn(List.of(section));

        LearningProgressOverview overview =
                practiceService.getProgressPageData(2L, "Toan", "avatar.jpg").overview();
        assertNotNull(overview);
        assertEquals("Toan", overview.studentName());
        assertEquals("avatar.jpg", overview.avatarUrl());
        assertEquals("Vững", overview.currentLevel());
        assertEquals(1, overview.totalAttempts());
        assertEquals(1, overview.totalCompletedTests());
        // Verify skill metric for READING is calculated
        Optional<SkillMetric> readingMetric = overview.skillMetrics().stream()
                .filter(m -> "READING".equals(m.skill())).findFirst();
        assertTrue(readingMetric.isPresent());
        assertEquals(80.0, readingMetric.get().normalizedScore());
        assertEquals("READING", overview.recentHistory().get(0).skill());
    }

    @Test
    void testGetPracticeAnalytics() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.markGraded(BigDecimal.valueOf(8), BigDecimal.valueOf(10), "{\"100\":\"1\"}", "{}");
        setEntityId(attempt, 99L);
        when(attemptRepository.findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                2L, PracticeAttempt.STATUS_DISCARDED)).thenReturn(List.of(attempt));
        PracticeSet set = new PracticeSet("Reading Test", "Desc", "MIXED",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        setEntityId(set, 1L);
        when(setRepository.findAllById(any())).thenReturn(List.of(set));
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test 1", "Desc", 1, 40);
        setEntityId(test, 10L);
        when(testRepository.findAllById(any())).thenReturn(List.of(test));
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Desc", 40, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findAllById(any())).thenReturn(List.of(section));

        PracticeQuestion q1 = mock(PracticeQuestion.class);
        when(q1.getId()).thenReturn(100L);
        when(q1.getQuestionType()).thenReturn("MCQ");
        when(q1.getSetId()).thenReturn(1L);
        when(q1.getAnswerKey()).thenReturn("1");
        when(q1.getPoints()).thenReturn(BigDecimal.ONE);
        when(questionRepository.findBySetIdIn(anyList())).thenReturn(List.of(q1));

        PracticeAnalytics analytics =
                practiceService.getProgressPageData(2L, "Toan", "").analytics();
        assertNotNull(analytics);
        assertFalse(analytics.weeklySkillMetrics().isEmpty());
        assertFalse(analytics.scoreTrend().isEmpty());
        assertFalse(analytics.history().isEmpty());
        // Verify Reading weekly score is mapped
        Optional<SkillMetric> readingMetric = analytics.weeklySkillMetrics().stream()
                .filter(m -> "READING".equals(m.skill())).findFirst();
        assertTrue(readingMetric.isPresent());
        assertEquals(80.0, readingMetric.get().normalizedScore());
        assertEquals("READING", analytics.history().get(0).skill());
    }

    @Test
    void progressPageDataReusesOneAttemptAndEntitySnapshot() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.markGraded(
                BigDecimal.valueOf(8), BigDecimal.TEN, "{\"100\":\"1\"}", "{}");
        setEntityId(attempt, 99L);
        when(attemptRepository.findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                2L, PracticeAttempt.STATUS_DISCARDED)).thenReturn(List.of(attempt));

        PracticeSet set = new PracticeSet(
                "Reading Test", "Desc", "MIXED", "GLOBAL",
                null, null, null, "PUBLISHED", 1L);
        setEntityId(set, 1L);
        when(setRepository.findAllById(any())).thenReturn(List.of(set));
        com.ksh.entities.PracticeTest test =
                new com.ksh.entities.PracticeTest(1L, "Test 1", "Desc", 1, 40);
        setEntityId(test, 10L);
        when(testRepository.findAllById(any())).thenReturn(List.of(test));
        PracticeSection section = new PracticeSection(
                1L, "Reading Section", "READING", "MCQ", "Desc",
                40, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);
        when(sectionRepository.findAllById(any())).thenReturn(List.of(section));

        PracticeQuestion question = mock(PracticeQuestion.class);
        when(question.getId()).thenReturn(100L);
        when(question.getSetId()).thenReturn(1L);
        when(question.getQuestionType()).thenReturn("MCQ");
        when(question.getAnswerKey()).thenReturn("1");
        when(question.getPoints()).thenReturn(BigDecimal.ONE);
        when(questionRepository.findBySetIdIn(anyList())).thenReturn(List.of(question));

        PracticeProgressPageData page =
                practiceService.getProgressPageData(2L, "Toan", "avatar.jpg");

        assertEquals(1, page.overview().totalAttempts());
        assertFalse(page.analytics().history().isEmpty());
        verify(attemptRepository, times(1))
                .findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        2L, PracticeAttempt.STATUS_DISCARDED);
        verify(setRepository, times(1)).findAllById(any());
        verify(testRepository, times(1)).findAllById(any());
        verify(sectionRepository, times(1)).findAllById(any());
    }

    @Test
    void progressAnalyticsUsesAttemptQuestionVersionInsteadOfLiveQuestion() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.lockPublishedVersion(700L, 701L, 702L, 703L);
        attempt.markGraded(BigDecimal.ONE, BigDecimal.ONE, "{\"100\":\"1\"}", "{}");
        setEntityId(attempt, 99L);
        when(attemptRepository.findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                2L, PracticeAttempt.STATUS_DISCARDED)).thenReturn(List.of(attempt));

        PracticeQuestionVersion versionQuestion = mock(PracticeQuestionVersion.class);
        when(versionQuestion.getId()).thenReturn(800L);
        when(versionQuestion.getPublishedVersionId()).thenReturn(700L);
        when(versionQuestion.getSectionVersionId()).thenReturn(703L);
        when(versionQuestion.getQuestionId()).thenReturn(100L);
        when(versionQuestion.getQuestionNo()).thenReturn(1);
        when(versionQuestion.getDisplayOrder()).thenReturn(1);
        when(versionQuestion.getQuestionType()).thenReturn(PracticeQuestion.TYPE_SINGLE_CHOICE);
        when(versionQuestion.getOptionsJson()).thenReturn("[\"A\",\"B\"]");
        when(versionQuestion.getAnswerKey()).thenReturn("1");
        when(versionQuestion.getPoints()).thenReturn(BigDecimal.ONE);
        when(questionVersionRepository
                .findByPublishedVersionIdInOrderByPublishedVersionIdAscSectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc(
                        anyList()))
                .thenReturn(List.of(versionQuestion));

        PracticeAnalytics analytics =
                practiceService.getProgressPageData(2L, "Toan", "").analytics();

        assertEquals(1, analytics.questionTypePerf().size());
        assertEquals(PracticeQuestion.TYPE_SINGLE_CHOICE,
                analytics.questionTypePerf().get(0).questionType());
        verify(questionRepository, never()).findBySetIdIn(anyList());
    }

    @Test
    void progressAnalyticsSkipsMalformedVersionQuestionWithoutFailingPage() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.lockPublishedVersion(700L, 701L, 702L, 703L);
        attempt.markGraded(BigDecimal.ZERO, BigDecimal.ONE, "{}", "{}");
        setEntityId(attempt, 99L);
        when(attemptRepository.findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                2L, PracticeAttempt.STATUS_DISCARDED)).thenReturn(List.of(attempt));

        PracticeQuestionVersion malformedQuestion = mock(PracticeQuestionVersion.class);
        when(malformedQuestion.getId()).thenReturn(801L);
        when(malformedQuestion.getPublishedVersionId()).thenReturn(700L);
        when(malformedQuestion.getSectionVersionId()).thenReturn(703L);
        when(malformedQuestion.getQuestionId()).thenReturn(101L);
        when(malformedQuestion.getQuestionNo()).thenReturn(1);
        when(malformedQuestion.getDisplayOrder()).thenReturn(1);
        when(malformedQuestion.getQuestionType()).thenReturn("UNKNOWN_OBJECTIVE_TYPE");
        when(malformedQuestion.getPoints()).thenReturn(BigDecimal.ONE);
        when(questionVersionRepository
                .findByPublishedVersionIdInOrderByPublishedVersionIdAscSectionVersionIdAscDisplayOrderAscQuestionNoAscIdAsc(
                        anyList()))
                .thenReturn(List.of(malformedQuestion));

        PracticeProgressPageData page =
                practiceService.getProgressPageData(2L, "Toan", "");

        assertNotNull(page);
        assertTrue(page.analytics().questionTypePerf().isEmpty());
    }

    @Test
    void testProgressAnalyticsUsesLatestBoundedAttemptsForAverage() {
        List<PracticeAttempt> allAttempts = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
            BigDecimal score = i == 101 ? BigDecimal.valueOf(100) : BigDecimal.valueOf(50);
            attempt.markGraded(score, BigDecimal.valueOf(100), "{}", "{}");
            setEntityId(attempt, i);
            allAttempts.add(attempt);
        }
        List<PracticeAttempt> recent100 = allAttempts.subList(0, 100);
        when(attemptRepository.findTop100ByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(
                2L, PracticeAttempt.STATUS_DISCARDED)).thenReturn(recent100);

        LearningProgressOverview overview =
                practiceService.getProgressPageData(2L, "Toan", "").overview();

        assertEquals(100, overview.totalAttempts());
        assertEquals(100, overview.totalCompletedTests());
        assertEquals(50.0, overview.recentAverageScore());
        assertEquals("Đang tiến bộ", overview.currentLevel());
        assertEquals(8, overview.recentHistory().size());
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
        attempt.setStatus(status);
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
        setEntityId(existingAttempt, 88L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(testRepository.findByIdForShare(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findByIdForShare(20L)).thenReturn(Optional.of(section));

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
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("IN_PROGRESS");

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        Map<String, String> form = Map.of("answer_101", "3", "other_field", "value");
        practiceService.saveInProgressAnswers(99L, 2L, form);

        assertEquals("{\"101\":\"3\"}", attempt.getAnswersJson());
        verify(attemptRepository).save(attempt);
    }

    @Test
    void testSaveInProgressAnswersNotInProgressThrows() {
        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");

        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

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
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

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
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

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
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

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
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

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
    void testGetReadingListeningResultLegacyFallback() {
        PracticeSet set = new PracticeSet("Reading Set", "Desc", "READING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test Full", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Reading Section", "READING", "MCQ", "Instruction", 60, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "READING", 20L);
        attempt.setStatus("SUBMITTED");
        attempt.setAnswersJson("{\"101\":\"3\"}");
        setEntityId(attempt, 99L);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));

        // Question group with sectionId = null (legacy)
        PracticeQuestionGroup group = new PracticeQuestionGroup(1L, "Group 1", 1, 1, "Instruction", null, null, 1);
        group.setSectionId(null);
        setEntityId(group, 5L);

        PracticeQuestion q1 = new PracticeQuestion(
                1L, 1, "MCQ", "Q",
                "[]", "3", "Giải thích đáp án đúng",
                BigDecimal.valueOf(5), 1
        );
        q1.setGroupId(5L);
        setEntityId(q1, 101L);

        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1));
        when(questionRepository.findById(101L)).thenReturn(Optional.of(q1));

        ReadingListeningResultView result = practiceService.getReadingListeningResult(99L, 2L);
        assertNotNull(result);
        assertEquals(1, result.groups().size());
        assertEquals("Group 1", result.groups().get(0).groupLabel());
        assertEquals(1, result.groups().get(0).questions().size());
        assertEquals("Q", result.groups().get(0).questions().get(0).prompt());
    }

    @Test
    void testQuestionComparatorOrdering() {
        PracticeQuestion q1 = new PracticeQuestion(
                1L, 52, "ESSAY", "Prompt 1",
                "[]", "", "Explain",
                BigDecimal.TEN, 5
        );
        setEntityId(q1, 1L);

        PracticeQuestion q2 = new PracticeQuestion(
                1L, 51, "ESSAY", "Prompt 2",
                "[]", "", "Explain",
                BigDecimal.TEN, 10
        );
        setEntityId(q2, 2L);

        PracticeQuestion q3 = new PracticeQuestion(
                1L, null, "ESSAY", "Prompt 3",
                "[]", "", "Explain",
                BigDecimal.TEN, 1
        );
        setEntityId(q3, 3L);

        PracticeQuestion q4 = new PracticeQuestion(
                1L, null, "ESSAY", "Prompt 4",
                "[]", "", "Explain",
                BigDecimal.TEN, null
        );
        setEntityId(q4, 4L);

        List<PracticeQuestion> list = new ArrayList<>(List.of(q4, q1, q3, q2));
        list.sort(practiceService.questionComparator);

        assertEquals(3L, list.get(0).getId()); // displayOrder 1
        assertEquals(1L, list.get(1).getId()); // displayOrder 5
        assertEquals(2L, list.get(2).getId()); // displayOrder 10
        assertEquals(4L, list.get(3).getId()); // questionNo null, displayOrder null
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
    void testLegacyFeedbackMappingRules() throws Exception {
        // ESSAY Questions
        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Prompt 2", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q2, 102L);

        List<PracticeQuestion> questions = List.of(q1, q2);
        String answersJson = "{\"101\":\"Answer one\",\"102\":\"Answer two\"}";

        // Rule 1: Match student_text exactly
        String aiFeedbackJson1 = "{\"rubric_scores\":[],\"student_text\":\"Answer one\",\"raw_score\":8.0,\"raw_score_max\":10.0}";
        List<PracticeQuestionFeedbackRow> feedbacks1 = practiceService.buildQuestionFeedbackRows(questions, answersJson, aiFeedbackJson1);
        assertNotNull(feedbacks1.get(0).writingFeedback());
        assertNull(feedbacks1.get(1).writingFeedback());
        assertEquals("Answer one", feedbacks1.get(0).learnerAnswer());
        assertFalse(feedbacks1.get(0).reEvaluatable());
        assertFalse(feedbacks1.get(1).reEvaluatable());

        // Rule 2: Match multiple student_text (same answer) -> match last ordered matching
        String answersJsonDup = "{\"101\":\"Same answer\",\"102\":\"Same answer\"}";
        String aiFeedbackJsonDup = "{\"rubric_scores\":[],\"student_text\":\"Same answer\",\"raw_score\":8.0,\"raw_score_max\":10.0}";
        List<PracticeQuestionFeedbackRow> feedbacksDup = practiceService.buildQuestionFeedbackRows(questions, answersJsonDup, aiFeedbackJsonDup);
        assertNull(feedbacksDup.get(0).writingFeedback());
        assertNotNull(feedbacksDup.get(1).writingFeedback());
        assertFalse(feedbacksDup.get(0).reEvaluatable());
        assertFalse(feedbacksDup.get(1).reEvaluatable());

        // Rule 3: No match -> map to last ordered essay
        String aiFeedbackJsonNoMatch = "{\"rubric_scores\":[],\"student_text\":\"Different answer\",\"raw_score\":7.0,\"raw_score_max\":10.0}";
        List<PracticeQuestionFeedbackRow> feedbacksNoMatch = practiceService.buildQuestionFeedbackRows(questions, answersJson, aiFeedbackJsonNoMatch);
        assertNull(feedbacksNoMatch.get(0).writingFeedback());
        assertNotNull(feedbacksNoMatch.get(1).writingFeedback());
        assertFalse(feedbacksNoMatch.get(0).reEvaluatable());
        assertFalse(feedbacksNoMatch.get(1).reEvaluatable());

        // Rule 4: Only one essay -> map to single essay question
        List<PracticeQuestion> singleList = List.of(q1);
        List<PracticeQuestionFeedbackRow> feedbacksSingle = practiceService.buildQuestionFeedbackRows(singleList, answersJson, aiFeedbackJsonNoMatch);
        assertNotNull(feedbacksSingle.get(0).writingFeedback());
        assertTrue(feedbacksSingle.get(0).reEvaluatable());
    }

    @Test
    void testCurrentWritingFeedbackMapMarksOnlySupportedEssayRowsReEvaluatable() {
        PracticeQuestion mcq = new PracticeQuestion(1L, 50, "MCQ", "Prompt 0", "[\"1\",\"2\"]", "1", "Explain", BigDecimal.TEN, 0);
        setEntityId(mcq, 100L);
        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 1);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Prompt 2", "[]", "", "Explain", BigDecimal.TEN, 2);
        setEntityId(q2, 102L);

        String answersJson = "{\"100\":\"1\",\"101\":\"Answer one\",\"102\":\"Answer two\"}";
        String feedbackJson = "{\"101\":{\"raw_score\":8.0,\"raw_score_max\":10.0},\"102\":{\"raw_score\":7.0,\"raw_score_max\":10.0}}";

        List<PracticeQuestionFeedbackRow> rows = practiceService.buildQuestionFeedbackRows(
                List.of(mcq, q1, q2),
                answersJson,
                feedbackJson);

        assertFalse(rows.get(0).reEvaluatable());
        assertTrue(rows.get(1).reEvaluatable());
        assertTrue(rows.get(2).reEvaluatable());
        assertNull(rows.get(0).writingFeedback());
        assertEquals(0, rows.get(1).writingFeedback().rawScore().compareTo(BigDecimal.valueOf(8.0)));
        assertEquals(0, rows.get(2).writingFeedback().rawScore().compareTo(BigDecimal.valueOf(7.0)));
    }

    @Test
    void speakingFeedbackMapBuildsPerQuestionRowsWithoutLeakingAnswers() throws Exception {
        PracticeQuestion q1 = new PracticeQuestion(1L, 1, "SPEAKING", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        PracticeQuestion q2 = new PracticeQuestion(1L, 2, "SPEAKING", "Prompt 2", "[]", "", "Explain", BigDecimal.TEN, 1);
        setEntityId(q1, 101L);
        setEntityId(q2, 102L);

        String answersJson = "{\"101\":\"SECRET ANSWER ONE\",\"102\":\"SECRET ANSWER TWO\"}";
        String feedbackJson = """
                {
                  "101": {"score": 3.0, "percentage": 33.33, "summary_vi": "Short", "strengths": [{"criterionId":"S","explanationVi":"Generic"}]},
                  "102": {"score": 7.0, "percentage": 77.78, "summary_vi": "Long", "needs_improvement": [{"criterionId":"N","explanationVi":"Generic"}]}
                }
                """;

        List<SpeakingQuestionFeedbackRow> rows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(q2, q1), answersJson, feedbackJson);

        assertEquals(List.of(101L, 102L), rows.stream().map(SpeakingQuestionFeedbackRow::questionId).toList());
        assertEquals("SECRET ANSWER ONE", rows.get(0).learnerAnswer());
        assertEquals("SECRET ANSWER TWO", rows.get(1).learnerAnswer());
        assertEquals(0, rows.get(0).speakingFeedback().percentage().compareTo(new BigDecimal("33.33")));
        assertEquals(0, rows.get(1).speakingFeedback().percentage().compareTo(new BigDecimal("77.78")));
        assertFalse(rows.get(0).legacyFeedbackApplied());
        assertFalse(rows.get(1).legacyFeedbackApplied());
        String serializedFeedback = objectMapper.writeValueAsString(rows.get(0).speakingFeedback());
        assertFalse(serializedFeedback.contains("SECRET ANSWER"));
        assertFalse(serializedFeedback.contains("\"score\""));
    }

    @Test
    void speakingAiEnvelopeBuildsAndReadsRichPerQuestionFeedback() throws Exception {
        PracticeQuestion q1 = new PracticeQuestion(1L, 1, "SPEAKING", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);
        com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult result =
                new com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult(
                        com.ksh.features.practice.ai.speaking.SpeakingEvaluationStatus.EVALUATED,
                        true,
                        com.ksh.features.practice.ai.speaking.SpeakingEvaluationSource.PROVIDER,
                        "gemini-compatible",
                        "gpt-4o-mini-transcribe",
                        "prompt-v",
                        "rubric-v",
                        "schema-v",
                        501L,
                        7L,
                        "저는 한국어를 공부해요.",
                        "저는 한국어를 공부해요.",
                        "저는 한국어를 공부해요.",
                        "Learner studies Korean",
                        null,
                        new BigDecimal("0.93"),
                        "LOW",
                        new BigDecimal("82"),
                        "B1",
                        "Bạn trả lời rõ ý.",
                        "Câu trả lời bám đề.",
                        List.of("Có ý chính rõ"),
                        List.of("Thêm ví dụ"),
                        List.of(new com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult.ActionPlanItem(
                                com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                                "S_CONTENT_SPECIFICITY_EXAMPLES",
                                "Thêm ví dụ",
                                "Nói thêm một ví dụ cá nhân.",
                                "Giúp nội dung cụ thể hơn.",
                                "HIGH")),
                        List.of(),
                        List.of(new com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult.TranscriptAnnotation(
                                "strength",
                                "CONTENT",
                                com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                                "S_CONTENT_RELEVANCE",
                                "한국어",
                                "",
                                3,
                                6,
                                "Có từ khóa đúng chủ đề.",
                                "LOW",
                                com.ksh.features.practice.ai.speaking.SpeakingEvidenceSource.TRANSCRIPT,
                                "TEXT_SPAN",
                                "한국어",
                                "Có từ khóa đúng chủ đề.",
                                "",
                                BigDecimal.ONE)),
                        List.of(new com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult.FeedbackItem(
                                com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                                "S_CONTENT_RELEVANCE",
                                "TEXT_SPAN",
                                "한국어",
                                com.ksh.features.practice.ai.speaking.SpeakingEvidenceSource.TRANSCRIPT,
                                "Bám đúng chủ đề.",
                                "")),
                        List.of(),
                        "Độ tin cậy ổn.",
                        List.of(new com.ksh.features.practice.ai.speaking.SpeakingEvaluationResult.RubricScore(
                                com.ksh.features.practice.ai.speaking.SpeakingRubricCriterion.CONTENT_TASK_FULFILLMENT,
                                new BigDecimal("16"),
                                new BigDecimal("20"),
                                "Nội dung tốt")),
                        List.of(),
                        List.of(),
                        List.of(),
                        "저는 한국어를 꾸준히 공부하고 있어요.",
                        "저는 매일 한국어를 공부하면서 새로운 표현을 익히고 있습니다.",
                        List.of("Pronunciation advisory only"),
                        List.of("Fluency text"),
                        null,
                        false);

        String feedbackJson = practiceService.speakingAiFeedbackEnvelope(Map.of(101L, result));
        List<SpeakingQuestionFeedbackRow> rows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(q1),
                "{\"101\":\"PRIVATE_SPEAKING_ANSWER\"}",
                feedbackJson);

        assertEquals("speaking_ai_v1", objectMapper.readTree(feedbackJson).path("_contract").asText());
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).speakingFeedback());
        assertEquals("EVALUATED", rows.get(0).speakingFeedback().evaluationStatus());
        assertEquals(new BigDecimal("82"), rows.get(0).speakingFeedback().percentage());
        assertEquals("Bạn trả lời rõ ý.", rows.get(0).speakingFeedback().overallSummary());
        assertEquals("저는 한국어를 공부해요.", rows.get(0).speakingFeedback().actuallyHeardTranscript());
        assertEquals(1, rows.get(0).speakingFeedback().actionPlan().size());
        assertEquals(1, rows.get(0).speakingFeedback().transcriptAnnotations().size());
        assertFalse(objectMapper.writeValueAsString(rows.get(0).speakingFeedback()).contains("PRIVATE_SPEAKING_ANSWER"));
    }

    @Test
    void speakingFeedbackRowsUseDeterministicQuestionOrderIncludingNulls() {
        PracticeQuestion first = new PracticeQuestion(1L, 3, "SPEAKING", "P1", "[]", "", "E", BigDecimal.ONE, 0);
        PracticeQuestion second = new PracticeQuestion(1L, 3, "SPEAKING", "P2", "[]", "", "E", BigDecimal.ONE, 0);
        PracticeQuestion third = new PracticeQuestion(1L, 2, "SPEAKING", "P3", "[]", "", "E", BigDecimal.ONE, 1);
        PracticeQuestion nullOrder = new PracticeQuestion(1L, 1, "SPEAKING", "P4", "[]", "", "E", BigDecimal.ONE, null);
        setEntityId(first, 101L);
        setEntityId(second, 102L);
        setEntityId(third, 103L);
        setEntityId(nullOrder, 104L);

        String feedback = "{\"101\":{\"score\":3},\"102\":{\"score\":3},\"103\":{\"score\":3},\"104\":{\"score\":3}}";
        List<SpeakingQuestionFeedbackRow> rows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(nullOrder, third, second, first), "{}", feedback);

        assertEquals(List.of(101L, 102L, 103L, 104L),
                rows.stream().map(SpeakingQuestionFeedbackRow::questionId).toList());
    }

    @Test
    void speakingLegacyOneObjectFeedbackIsMarkedAsGlobalCompatibility() {
        PracticeQuestion q1 = new PracticeQuestion(1L, 1, "SPEAKING", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        PracticeQuestion q2 = new PracticeQuestion(1L, 2, "SPEAKING", "Prompt 2", "[]", "", "Explain", BigDecimal.TEN, 1);
        setEntityId(q1, 101L);
        setEntityId(q2, 102L);

        List<SpeakingQuestionFeedbackRow> rows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(q1, q2),
                "{\"101\":\"Answer one\",\"102\":\"Answer two\"}",
                "{\"score\":7.0,\"summary_vi\":\"Legacy global\"}");

        assertEquals(2, rows.size());
        assertTrue(rows.get(0).legacyFeedbackApplied());
        assertTrue(rows.get(1).legacyFeedbackApplied());
        assertEquals("Legacy global", rows.get(0).speakingFeedback().summaryVi());
        assertEquals(0, rows.get(0).speakingFeedback().percentage().compareTo(new BigDecimal("77.78")));
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
        assertEquals(0, attempt.getScore().compareTo(new BigDecimal("75.00")));
        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(40)));

        String submittedFeedback = attempt.getAiFeedbackJson();
        practiceService.reEvaluate(99L, 2L);

        assertEquals(submittedFeedback, attempt.getAiFeedbackJson());
        assertEquals(0, attempt.getScore().compareTo(new BigDecimal("75.00")));
        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(40)));

        PracticeResultView result = practiceService.getResult(99L, 2L);
        assertEquals("75%", result.scoreLabel());
        assertEquals("AUDIO_SUBMITTED", result.speakingQuestionFeedbacks().get(1).learnerAnswer());
        verify(speakingMediaService).requireReadyMediaForOwner(2L, 99L, List.of(101L, 102L));
        verify(speakingService, times(4)).evaluateQuestion(any(SpeakingEvaluationApplicationService.EvaluationInput.class));
        verify(evaluationClient, never()).evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any());
        verifyNoInteractions(explanationReadService, audioStorageService);
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
        assertEquals(0, attempt.getScore().compareTo(new BigDecimal("80.00")));
        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals("speaking_ai_v1", feedback.path("_contract").asText());
        assertTrue(feedback.path("speaking_feedback_by_question").has("101"));
        assertFalse(attempt.getAiFeedbackJson().contains("provider_raw_body"));
        verify(speakingMediaService).requireReadyMediaForOwner(2L, 99L, List.of(101L));
        verify(speakingService, times(1)).evaluateQuestion(any(SpeakingEvaluationApplicationService.EvaluationInput.class));
        verify(evaluationClient, never()).evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void speakingReEvaluateDoesNotCallRealSpeakingAiService() {
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

        practiceService.reEvaluate(99L, 2L);

        verify(speakingService, never()).evaluateQuestion(any(SpeakingEvaluationApplicationService.EvaluationInput.class));
    }

    @Test
    void mixedLegacySpeakingEssayPersistsVersionedEnvelopeAndAggregatesByQuestionRegardlessOfOrder() throws Exception {
        assertMixedLegacySpeakingEssayOrder(List.of("SPEAKING", "ESSAY"));
        setUp();
        assertMixedLegacySpeakingEssayOrder(List.of("ESSAY", "SPEAKING"));
    }

    private void assertMixedLegacySpeakingEssayOrder(List<String> order) throws Exception {
        PracticeSet set = new PracticeSet("Mixed Speaking Set", "Desc", "SPEAKING",  "GLOBAL", null, null, null, "PUBLISHED", 1L);
        setEntityId(set, 1L);
        com.ksh.entities.PracticeTest test = new com.ksh.entities.PracticeTest(1L, "Test", "Desc", 1, 40);
        setEntityId(test, 10L);
        PracticeSection section = new PracticeSection(1L, "Mixed Speaking Section", "SPEAKING", "ORAL", "Instruction", 30, BigDecimal.TEN, 1);
        section.setTestId(10L);
        setEntityId(section, 20L);

        PracticeQuestionGroup group = mock(PracticeQuestionGroup.class);
        when(group.getId()).thenReturn(30L);
        when(group.getGroupLabel()).thenReturn("1-3");
        when(group.getQuestionFrom()).thenReturn(0);
        when(group.getQuestionTo()).thenReturn(3);
        when(group.getInstruction()).thenReturn("Prompt group");
        when(group.getSectionId()).thenReturn(20L);
        when(group.getDisplayOrder()).thenReturn(0);

        PracticeQuestion mcq = new PracticeQuestion(1L, 0, "MCQ", "MCQ", "[\"A\",\"B\"]", "1", "Explain", BigDecimal.valueOf(5), 0);
        PracticeQuestion speaking = new PracticeQuestion(1L, 1, "SPEAKING", "Speaking prompt", "[]", "", "Explain", BigDecimal.TEN, 1);
        PracticeQuestion essay = new PracticeQuestion(1L, 2, "ESSAY", "Essay prompt", "[]", "", "Explain", BigDecimal.valueOf(20), 2);
        setEntityId(mcq, 100L);
        setEntityId(speaking, 101L);
        setEntityId(essay, 202L);
        mcq.setGroupId(30L);
        speaking.setGroupId(30L);
        essay.setGroupId(30L);

        List<PracticeQuestion> orderedQuestions = new ArrayList<>();
        orderedQuestions.add(mcq);
        for (String type : order) {
            orderedQuestions.add("SPEAKING".equals(type) ? speaking : essay);
        }

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "SPEAKING", 20L);
        attempt.setStatus("IN_PROGRESS");
        setEntityId(attempt, 99L);
        useImmutableSnapshot(attempt, set, test, section, group, orderedQuestions);

        when(setRepository.findById(1L)).thenReturn(Optional.of(set));
        when(testRepository.findById(10L)).thenReturn(Optional.of(test));
        when(sectionRepository.findById(20L)).thenReturn(Optional.of(section));
        when(sectionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(section));
        when(attemptRepository.findByIdAndUserId(99L, 2L)).thenReturn(Optional.of(attempt));
        when(groupRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(group));
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(orderedQuestions);

        String essayFeedback = "{\"score\":8.0,\"overall_score\":8.0,\"raw_score\":8.0,\"raw_score_max\":10.0,\"summary_vi\":\"ESSAY_MIXED_FEEDBACK\"}";
        when(evaluationClient.evaluate(eq(2L), eq("Essay prompt"), eq("ESSAY_PRIVATE_SENTINEL_MIXED"), eq(false), any()))
                .thenReturn(essayFeedback);
        when(evaluationClient.evaluate(eq(2L), eq("Essay prompt"), eq("ESSAY_PRIVATE_SENTINEL_MIXED"), eq(true), any()))
                .thenReturn(essayFeedback);

        practiceService.submitAttempt(99L, 2L, Map.of(
                "answer_100", "1",
                "answer_101", "SPEAKING_PRIVATE_SENTINEL_MIXED",
                "answer_202", "ESSAY_PRIVATE_SENTINEL_MIXED"
        ));

        assertMixedEnvelope(attempt);
        assertEquals(0, attempt.getScore().compareTo(new BigDecimal("74.60")));
        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(35)));

        PracticeResultView result = practiceService.getResult(99L, 2L);
        assertEquals(List.of(100L, 101L, 202L), result.speakingQuestionFeedbacks().stream()
                .map(SpeakingQuestionFeedbackRow::questionId)
                .toList());
        SpeakingQuestionFeedbackRow speakingRow = result.speakingQuestionFeedbacks().get(1);
        SpeakingQuestionFeedbackRow essayRow = result.speakingQuestionFeedbacks().get(2);
        assertNotNull(speakingRow.speakingFeedback());
        assertNull(speakingRow.legacyEssayFeedback());
        assertNull(essayRow.speakingFeedback());
        assertNotNull(essayRow.legacyEssayFeedback());
        assertEquals("SPEAKING_PRIVATE_SENTINEL_MIXED", speakingRow.learnerAnswer());
        assertEquals("ESSAY_PRIVATE_SENTINEL_MIXED", essayRow.learnerAnswer());

        String submittedFeedback = attempt.getAiFeedbackJson();
        practiceService.reEvaluate(99L, 2L);

        assertEquals(objectMapper.readTree(submittedFeedback), objectMapper.readTree(attempt.getAiFeedbackJson()));
        assertEquals(0, attempt.getScore().compareTo(new BigDecimal("74.60")));
        verify(evaluationClient, times(2)).evaluate(eq(2L), eq("Essay prompt"), eq("ESSAY_PRIVATE_SENTINEL_MIXED"), anyBoolean(), any());
        verifyNoInteractions(explanationReadService, audioStorageService);
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
    void speakingEmptyOrMalformedFeedbackProducesSafeRowsWithoutFeedback() {
        PracticeQuestion question = new PracticeQuestion(
                1L, 1, "SPEAKING", "Prompt", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(question, 101L);

        List<SpeakingQuestionFeedbackRow> emptyRows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(question), "{\"101\":\"Answer\"}", "{}");
        List<SpeakingQuestionFeedbackRow> malformedRows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(question), "{\"101\":\"Answer\"}", "not-json");
        List<SpeakingQuestionFeedbackRow> unknownRows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(question), "{\"101\":\"Answer\"}", "{\"unknown\":\"value\"}");
        List<SpeakingQuestionFeedbackRow> invalidMapRows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(question), "{\"101\":\"Answer\"}", "{\"101\":\"not-an-object\"}");
        List<SpeakingQuestionFeedbackRow> unknownContractRows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(question), "{\"101\":\"Answer\"}", "{\"_contract\":\"speaking_mixed_v2\",\"score\":7.0}");
        List<SpeakingQuestionFeedbackRow> invalidMixedRows = practiceService.buildSpeakingQuestionFeedbackRows(
                List.of(question), "{\"101\":\"Answer\"}", "{\"_contract\":\"speaking_mixed_v1\",\"speaking_feedback_by_question\":{\"101\":\"not-object\"},\"essay_feedback_by_question\":{}}");

        assertFalse(emptyRows.get(0).feedbackAvailable());
        assertNull(emptyRows.get(0).speakingFeedback());
        assertFalse(malformedRows.get(0).feedbackAvailable());
        assertNull(malformedRows.get(0).speakingFeedback());
        assertFalse(unknownRows.get(0).feedbackAvailable());
        assertFalse(invalidMapRows.get(0).feedbackAvailable());
        assertFalse(unknownContractRows.get(0).feedbackAvailable());
        assertFalse(invalidMixedRows.get(0).feedbackAvailable());
    }

    @Test
    void testMalformedWritingFeedbackMapDisablesReEvaluateWithoutBreakingRows() {
        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Prompt 2", "[]", "", "Explain", BigDecimal.TEN, 1);
        setEntityId(q2, 102L);

        String answersJson = "{\"101\":\"Answer one\",\"102\":\"Answer two\"}";
        String feedbackJson = "{\"101\":{\"raw_score\":8.0,\"raw_score_max\":10.0},\"102\":{\"raw_score\":7.0,\"raw_score_max\":0}}";

        List<PracticeQuestionFeedbackRow> rows = practiceService.buildQuestionFeedbackRows(
                List.of(q1, q2),
                answersJson,
                feedbackJson);

        assertNotNull(rows.get(0).writingFeedback());
        assertNotNull(rows.get(1).writingFeedback());
        assertFalse(rows.get(0).reEvaluatable());
        assertFalse(rows.get(1).reEvaluatable());
    }

    @Test
    void testIncompleteWritingFeedbackMapDisablesReEvaluateWithoutBreakingRows() {
        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Prompt 2", "[]", "", "Explain", BigDecimal.TEN, 1);
        setEntityId(q2, 102L);

        String answersJson = "{\"101\":\"Answer one\",\"102\":\"Answer two\"}";
        String feedbackJson = "{\"101\":{\"raw_score\":8.0,\"raw_score_max\":10.0}}";

        List<PracticeQuestionFeedbackRow> rows = practiceService.buildQuestionFeedbackRows(
                List.of(q1, q2),
                answersJson,
                feedbackJson);

        assertNotNull(rows.get(0).writingFeedback());
        assertNull(rows.get(1).writingFeedback());
        assertFalse(rows.get(0).reEvaluatable());
        assertFalse(rows.get(1).reEvaluatable());
    }

    @Test
    void testNonNumericWritingFeedbackScoreDisablesReEvaluateWithoutBreakingRows() {
        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Prompt 2", "[]", "", "Explain", BigDecimal.TEN, 1);
        setEntityId(q2, 102L);

        String answersJson = "{\"101\":\"Answer one\",\"102\":\"Answer two\"}";
        String feedbackJson = "{\"101\":{\"raw_score\":\"bad\",\"raw_score_max\":10.0},\"102\":{\"raw_score\":7.0,\"raw_score_max\":10.0}}";

        List<PracticeQuestionFeedbackRow> rows = practiceService.buildQuestionFeedbackRows(
                List.of(q1, q2),
                answersJson,
                feedbackJson);

        assertNotNull(rows.get(0).writingFeedback());
        assertNotNull(rows.get(1).writingFeedback());
        assertNull(rows.get(0).writingFeedback().rawScore());
        assertFalse(rows.get(0).reEvaluatable());
        assertFalse(rows.get(1).reEvaluatable());
    }

    @Test
    void testWritingFeedbackViewPreservesSelectedEntryOrderAndDefaultsOptionalLists() {
        PracticeQuestion q1 = new PracticeQuestion(1L, 51, "ESSAY", "Prompt 1", "[]", "", "Explain", BigDecimal.TEN, 0);
        setEntityId(q1, 101L);

        String answersJson = "{\"101\":\"Answer one\"}";
        String feedbackJson = """
                {"101":{"raw_score":8.0,"raw_score_max":10.0,"rubric_scores":[{"name":"first","score":8,"feedback":"A"},{"name":"second","score":7,"feedback":"B"}],"strengths":"bad","needs_improvement":[1,{"criterionId":"need"}],"annotations":[{"id":"ann-1","start":0,"end":6,"index":1}],"sentence_rewrites":[{"original":"old","upgraded":"new","reason":"why"}],"unknown":{"kept":true}}}
                """;

        List<PracticeQuestionFeedbackRow> rows = practiceService.buildQuestionFeedbackRows(
                List.of(q1),
                answersJson,
                feedbackJson);

        PracticeQuestionFeedbackRow row = rows.get(0);
        assertNotNull(row.writingFeedback());
        assertEquals("first", row.writingFeedback().rubricScores().get(0).name());
        assertEquals("second", row.writingFeedback().rubricScores().get(1).name());
        assertTrue(row.writingFeedback().strengths().isEmpty());
        assertEquals(1, row.writingFeedback().needsImprovement().size());
        assertEquals("need", row.writingFeedback().needsImprovement().get(0).criterionId());
        assertEquals("ann-1", row.writingFeedback().annotations().get(0).id());
        assertEquals("old", row.writingFeedback().sentenceRewrites().get(0).original());
        assertTrue(row.reEvaluatable());
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

        // Essay Question (Points 15.0)
        PracticeQuestion qEssay = new PracticeQuestion(
                1L, 51, "ESSAY", "Q2",
                "[]", "", "Explain",
                BigDecimal.valueOf(15.0), 0
        );
        setEntityId(qEssay, 102L);

        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(qMcq, qEssay));

        // Stub AI Client to return raw_score = 6.0, raw_score_max = 10.0 (meaning 60% earned points)
        // 60% of 15.0 = 9.0 earned points.
        // Total earned points = MCQ (5.0) + ESSAY (9.0) = 14.0 points.
        // Total possible points = 5.0 + 15.0 = 20.0 points.
        // Final percentage score = (14.0 / 20.0) * 100 = 70.00%
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any())).thenReturn("{\"raw_score\":6.0,\"raw_score_max\":10.0}");

        Map<String, String> form = Map.of("answer_101", "3", "answer_102", "My essay");
        practiceService.submitAttempt(99L, 2L, form);

        assertEquals("GRADED", attempt.getStatus());
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(70.00)));
        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(20.0)));
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
        setEntityId(q, 101L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn("{\"evaluation_status\":\"EVALUATION_UNAVAILABLE\",\"evaluation_source\":\"PROVIDER\",\"evaluation_reason\":\"MISSING_API_KEY\",\"evaluation_retryable\":true,\"score_available\":false}");

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
        setEntityId(q, 101L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn("{\"evaluation_status\":\"EVALUATION_CONTRACT_FAILED\",\"evaluation_source\":\"PROVIDER\",\"evaluation_reason\":\"PROVIDER_CONTRACT_INVALID\",\"evaluation_retryable\":true,\"score_available\":false}");

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
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 1);
        setEntityId(q2, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        when(evaluationClient.evaluate(anyLong(), eq("Q1"), anyString(), anyBoolean(), any()))
                .thenReturn("{\"raw_score\":8.0,\"raw_score_max\":10.0,\"rubric_scores\":[]}");
        when(evaluationClient.evaluate(anyLong(), eq("Q2"), anyString(), anyBoolean(), any()))
                .thenReturn("{\"raw_score\":21.0,\"raw_score_max\":30.0,\"rubric_scores\":[]}");

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1", "answer_102", "A2"));

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertTrue(root.get("101").isObject());
        assertFalse(root.get("101").isTextual());
        assertTrue(root.get("102").isObject());
        assertFalse(root.get("102").isTextual());
    }

    @Test
    void testWritingAggregationClampsRawScoresBeforeWeighting() {
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
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 52, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 1);
        setEntityId(q2, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        when(evaluationClient.evaluate(anyLong(), eq("Q1"), anyString(), anyBoolean(), any()))
                .thenReturn("{\"raw_score\":-5.0,\"raw_score_max\":10.0}");
        when(evaluationClient.evaluate(anyLong(), eq("Q2"), anyString(), anyBoolean(), any()))
                .thenReturn("{\"raw_score\":40.0,\"raw_score_max\":30.0}");

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1", "answer_102", "A2"));

        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(40.0)));
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(75.00)));
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
        setEntityId(q1, 101L);
        PracticeQuestion q2 = new PracticeQuestion(1L, 53, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 1);
        setEntityId(q2, 102L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q1, q2));

        when(evaluationClient.evaluate(anyLong(), eq("Q1"), eq("짧은 답"), anyBoolean(), any()))
                .thenReturn("{\"raw_score\":5.0,\"raw_score_max\":10.0}");
        when(evaluationClient.evaluate(anyLong(), eq("Q2"), anyString(), anyBoolean(), any()))
                .thenReturn("{\"raw_score\":15.0,\"raw_score_max\":30.0}");

        practiceService.submitAttempt(99L, 2L, Map.of(
                "answer_101", "짧은 답",
                "answer_102", "긴 답 ".repeat(80)
        ));

        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(40.0)));
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(50.00)));
        verify(evaluationClient).evaluate(eq(2L), eq("Q1"), eq("짧은 답"), eq(false), isNull());
        verify(evaluationClient).evaluate(eq(2L), eq("Q2"), anyString(), eq(false), isNull());
    }

    @Test
    void testWritingAggregationScalesRawScoreByConfiguredQuestionPoints() {
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
                .thenReturn("{\"raw_score\":24.0,\"raw_score_max\":30.0}");
        when(evaluationClient.evaluate(anyLong(), eq("Q2"), anyString(), anyBoolean(), eq(WritingTaskType.Q53)))
                .thenReturn("{\"raw_score\":24.0,\"raw_score_max\":30.0}");

        practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1", "answer_102", "A2"));

        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(45.0)));
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(80.00)));
        JsonNode feedback = assertDoesNotThrow(() -> objectMapper.readTree(attempt.getAiFeedbackJson()));
        assertEquals(24.0, feedback.get("101").path("raw_score").asDouble());
        assertEquals(24.0, feedback.get("102").path("raw_score").asDouble());
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

        PracticeQuestion q = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.TEN, 0);
        q.setWritingTaskType(WritingTaskType.Q53);
        setEntityId(q, 101L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn("{\"raw_score\":8.0,\"raw_score_max\":10.0}");

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
        setEntityId(q, 101L);
        when(questionRepository.findBySetIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(q));
        when(evaluationClient.evaluate(anyLong(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn("{\"raw_score\":8.0,\"raw_score_max\":10.0}");
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(PracticeAttempt.class, 99L))
                .when(attemptRepository).saveAndFlush(attempt);

        assertThrows(PracticeAttemptConflictException.class,
                () -> practiceService.submitAttempt(99L, 2L, Map.of("answer_101", "A1")));
    }

    @Test
    void testWritingQuestionReEvaluateReplacesTargetOnlyAndRecomputesScore() throws Exception {
        PracticeAttempt attempt = arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                "{\"102\":{\"raw_score\":6.0,\"raw_score_max\":10.0,\"summary\":\"keep\"},\"103\":{\"raw_score\":15.0,\"raw_score_max\":30.0,\"summary\":\"old\"}}",
                true);
        JsonNode oldNonTarget = objectMapper.readTree(attempt.getAiFeedbackJson()).get("102");

        when(evaluationClient.evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), any()))
                .thenReturn("{\"raw_score\":24.0,\"raw_score_max\":30.0,\"summary\":\"new\",\"rubric_scores\":[]}");

        Long result = practiceService.reEvaluateQuestion(99L, 103L, 2L);

        assertEquals(99L, result);
        assertEquals("GRADED", attempt.getStatus());
        assertEquals("{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}", attempt.getAnswersJson());
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(76.00)));
        assertEquals(0, attempt.getTotalPoints().compareTo(BigDecimal.valueOf(50.0)));
        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals(oldNonTarget, feedback.get("102"));
        assertEquals("new", feedback.get("103").path("summary").asText());
        assertTrue(feedback.get("103").isObject());
        assertFalse(feedback.get("103").isTextual());
        verify(evaluationClient, times(1)).evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), eq(WritingTaskType.Q54));
        verify(evaluationClient, never()).evaluate(eq(2L), eq("Q1"), anyString(), anyBoolean(), any());
    }

    @Test
    void testWritingQuestionReEvaluateUnavailablePreservesTargetAndAggregate() throws Exception {
        String oldFeedback = "{\"102\":{\"raw_score\":6.0,\"raw_score_max\":10.0,\"summary\":\"keep\"},\"103\":{\"raw_score\":15.0,\"raw_score_max\":30.0,\"summary\":\"old\"}}";
        PracticeAttempt attempt = arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                oldFeedback,
                true);
        BigDecimal oldScore = attempt.getScore();

        when(evaluationClient.evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), any()))
                .thenReturn("{\"evaluation_status\":\"EVALUATION_UNAVAILABLE\",\"evaluation_source\":\"PROVIDER\",\"evaluation_reason\":\"PROVIDER_HTTP_ERROR\",\"evaluation_retryable\":true,\"score_available\":false}");

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
    void testWritingQuestionReEvaluateConvertsLegacyFlatSingleEssayToCurrentMap() throws Exception {
        PracticeAttempt attempt = arrangeSingleEssayWritingQuestionReEvaluationAttempt(
                "{\"102\":\"A1\"}",
                "{\"raw_score\":6.0,\"raw_score_max\":10.0,\"student_text\":\"A1\"}");
        when(evaluationClient.evaluate(eq(2L), eq("Q1"), eq("A1"), eq(true), any()))
                .thenReturn("{\"raw_score\":8.0,\"raw_score_max\":10.0,\"summary\":\"new\"}");

        practiceService.reEvaluateQuestion(99L, 102L, 2L);

        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertTrue(feedback.has("102"));
        assertFalse(feedback.has("raw_score"));
        assertEquals("new", feedback.get("102").path("summary").asText());
        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(80.00)));
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
                "{\"102\":{\"raw_score\":6.0,\"raw_score_max\":10.0,\"summary\":\"keep\"},\"103\":{\"raw_score\":15.0,\"raw_score_max\":30.0,\"summary\":\"old\"}}",
                true);
        when(evaluationClient.evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), any()))
                .thenReturn("{\"raw_score\":0.0,\"raw_score_max\":30.0,\"summary\":\"invalid\"}");

        practiceService.reEvaluateQuestion(99L, 103L, 2L);

        assertEquals(0, attempt.getScore().compareTo(BigDecimal.valueOf(28.00)));
        JsonNode feedback = objectMapper.readTree(attempt.getAiFeedbackJson());
        assertEquals(0.0, feedback.get("103").path("raw_score").asDouble());
        verify(evaluationClient, times(1)).evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), eq(WritingTaskType.Q54));
    }

    @Test
    void testWritingQuestionReEvaluateFeedbackChangedBeforePhaseBConflictsAndPreservesOldResult() throws Exception {
        PracticeAttempt snapshotAttempt = arrangeWritingQuestionReEvaluationAttempt(
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                "{\"102\":{\"raw_score\":6.0,\"raw_score_max\":10.0},\"103\":{\"raw_score\":15.0,\"raw_score_max\":30.0}}",
                false);
        PracticeAttempt changedAttempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        changedAttempt.setStatus("GRADED");
        changedAttempt.setLockVersion(0L);
        changedAttempt.markGraded(
                BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(50.0),
                "{\"101\":\"3\",\"102\":\"A1\",\"103\":\"A2\"}",
                "{\"102\":{\"raw_score\":6.0,\"raw_score_max\":10.0},\"103\":{\"raw_score\":20.0,\"raw_score_max\":30.0}}");
        setEntityId(changedAttempt, 99L);
        when(attemptRepository.findByIdAndUserId(99L, 2L))
                .thenReturn(Optional.of(snapshotAttempt), Optional.of(changedAttempt));
        when(evaluationClient.evaluate(eq(2L), eq("Q2"), eq("A2"), eq(true), any()))
                .thenReturn("{\"raw_score\":24.0,\"raw_score_max\":30.0}");

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
                "{\"102\":{\"raw_score\":6.0,\"raw_score_max\":10.0},\"103\":{\"raw_score\":15.0,\"raw_score_max\":30.0}}",
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
        PracticeQuestion qEssay1 = new PracticeQuestion(1L, 51, "ESSAY", "Q1", "[]", "", "Explain", BigDecimal.valueOf(15.0), 1);
        qEssay1.setWritingTaskType(WritingTaskType.Q51);
        qEssay1.setGroupId(30L);
        setEntityId(qEssay1, 102L);
        PracticeQuestion qEssay2 = new PracticeQuestion(1L, 53, "ESSAY", "Q2", "[]", "", "Explain", BigDecimal.valueOf(30.0), 2);
        qEssay2.setWritingTaskType(WritingTaskType.Q54);
        qEssay2.setGroupId(30L);
        setEntityId(qEssay2, 103L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("GRADED");
        attempt.setLockVersion(0L);
        attempt.markGraded(BigDecimal.valueOf(50.00), BigDecimal.valueOf(50.0), answersJson, feedbackJson);
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
        qEssay.setWritingTaskType(WritingTaskType.Q53);
        qEssay.setGroupId(30L);
        setEntityId(qEssay, 102L);

        PracticeAttempt attempt = new PracticeAttempt(2L, 1L, 10L, "WRITING", 20L);
        attempt.setStatus("GRADED");
        attempt.setLockVersion(0L);
        attempt.markGraded(BigDecimal.valueOf(60.00), BigDecimal.TEN, answersJson, feedbackJson);
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
        practiceService.setPublishedVersionServiceForTests(versionService);
        attempt.lockPublishedVersion(100L, 101L, 102L, 103L);
    }

    private SpeakingEvaluationResult speakingResult(
            SpeakingEvaluationStatus status,
            boolean scoreAvailable,
            BigDecimal overallScore,
            boolean retryable
    ) {
        return new SpeakingEvaluationResult(
                status,
                scoreAvailable,
                SpeakingEvaluationSource.PROVIDER,
                "models/gemini-2.5-flash",
                "gpt-4o-mini-transcribe",
                "speaking-eval-v1",
                "speaking-rubric-v1",
                "speaking-schema-v1",
                12L,
                3L,
                null,
                null,
                null,
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
                List.of(),
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
}
