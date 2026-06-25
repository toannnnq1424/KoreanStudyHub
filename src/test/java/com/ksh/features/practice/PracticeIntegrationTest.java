package com.ksh.features.practice;

import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.PracticeSet;
import com.ksh.entities.PracticeSubmission;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.repository.PracticeQuestionRepository;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PracticeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticeSetRepository setRepository;

    @Autowired
    private PracticeQuestionRepository questionRepository;

    @Autowired
    private PracticeSubmissionRepository submissionRepository;

    @Autowired
    private com.ksh.features.practice.repository.PracticeDraftRepository draftRepository;

    @Autowired
    private com.ksh.features.practice.repository.PracticeEditLogRepository editLogRepository;

    @Autowired
    private com.ksh.features.practice.manage.service.PracticeRevisionService revisionService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.ksh.features.practice.ai.AnswerExplanationClient answerExplanationClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.ksh.features.practice.ai.WritingEvaluationClient writingEvaluationClient;

    private User student;
    private User lecturer;
    private PracticeSet practiceSet;
    private PracticeQuestion question;

    @BeforeEach
    void setUp() {
        student = userRepository.findByEmailIgnoreCase("student@ksh.edu.vn").orElseThrow();
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();

        // Seed a published practice set
        practiceSet = new PracticeSet(
                "TOPIK II - Đọc hiểu 35",
                "Mô tả đề thi đọc hiểu TOPIK II kì 35",
                "READING",
                "TOPIK_II",
                "GLOBAL",
                null,
                "practice-pdfs/test.pdf",
                "{}",
                "PUBLISHED",
                lecturer.getId()
        );
        setRepository.saveAndFlush(practiceSet);

        // Seed a question for the set
        question = new PracticeQuestion(
                practiceSet.getId(),
                1,
                "MCQ",
                "Câu hỏi 1",
                "[\"Đáp án A\", \"Đáp án B\"]",
                "1",
                "Giải thích đáp án đúng",
                BigDecimal.valueOf(2.5),
                0
        );
        questionRepository.saveAndFlush(question);
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testIndexAuthenticated() throws Exception {
        mockMvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/index"))
                .andExpect(model().attributeExists("sets"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSetDetailView() throws Exception {
        mockMvc.perform(get("/practice/sets/" + practiceSet.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/set-detail"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attributeExists("submissions"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testTestDetailView() throws Exception {
        mockMvc.perform(get("/practice/sets/" + practiceSet.getId() + "/tests/" + practiceSet.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/test-detail"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attributeExists("attempts"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testModeView() throws Exception {
        mockMvc.perform(get("/practice/sets/" + practiceSet.getId() + "/tests/" + practiceSet.getId() + "/mode"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/mode"))
                .andExpect(model().attributeExists("view"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testPlayerView() throws Exception {
        // Start attempt
        mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + practiceSet.getId() + "/attempts")
                        .with(csrf())
                        .param("mode", "exam"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/practice/attempts/*"));

        List<PracticeSubmission> submissions = submissionRepository.findTop20ByUserIdOrderByCreatedAtDesc(student.getId());
        assertThat(submissions).isNotEmpty();
        PracticeSubmission attempt = submissions.get(0);

        mockMvc.perform(get("/practice/attempts/" + attempt.getId()).param("mode", "exam"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/player"))
                .andExpect(model().attributeExists("view"))
                .andExpect(model().attribute("mode", "exam"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSubmitAttemptAndGetResult() throws Exception {
        // Start attempt
        mockMvc.perform(post("/practice/sets/" + practiceSet.getId() + "/tests/" + practiceSet.getId() + "/attempts")
                        .with(csrf())
                        .param("mode", "exam"))
                .andExpect(status().is3xxRedirection());

        List<PracticeSubmission> submissions = submissionRepository.findTop20ByUserIdOrderByCreatedAtDesc(student.getId());
        assertThat(submissions).isNotEmpty();
        PracticeSubmission attempt = submissions.get(0);

        // Perform Submit
        String paramName = "answer_" + question.getId();
        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/submit")
                        .with(csrf())
                        .param(paramName, "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/attempts/" + attempt.getId() + "/result"));

        // Perform GET result view -> should redirect to rl-result template for READING/LISTENING
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result"))
                .andExpect(model().attributeExists("result"));

        // Perform GET detailed result view -> should redirect to rl-result-detail template
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/rl-result-detail"))
                .andExpect(model().attributeExists("result"));

        // Perform POST Re-evaluation
        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/re-evaluate")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/attempts/" + attempt.getId() + "/result"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testSubmitWritingAttemptAndGetResult() throws Exception {
        // Seed a published WRITING set
        PracticeSet writingSet = new PracticeSet(
                "TOPIK II - Viết 35",
                "Mô tả đề thi viết TOPIK II kì 35",
                "WRITING",
                "TOPIK_II",
                "GLOBAL",
                null,
                "practice-pdfs/test.pdf",
                "{}",
                "PUBLISHED",
                lecturer.getId()
        );
        setRepository.saveAndFlush(writingSet);

        PracticeQuestion writingQuestion = new PracticeQuestion(
                writingSet.getId(),
                51,
                "ESSAY",
                "Câu hỏi viết 51",
                "[]",
                "",
                "Giải thích đáp án đúng",
                BigDecimal.valueOf(10.0),
                0
        );
        questionRepository.saveAndFlush(writingQuestion);

        // Start attempt
        mockMvc.perform(post("/practice/sets/" + writingSet.getId() + "/tests/" + writingSet.getId() + "/attempts")
                        .with(csrf())
                        .param("mode", "exam"))
                .andExpect(status().is3xxRedirection());

        List<PracticeSubmission> submissions = submissionRepository.findTop20ByUserIdOrderByCreatedAtDesc(student.getId());
        assertThat(submissions).isNotEmpty();
        PracticeSubmission attempt = submissions.get(0);

        // Perform Submit
        String paramName = "answer_" + writingQuestion.getId();
        mockMvc.perform(post("/practice/attempts/" + attempt.getId() + "/submit")
                        .with(csrf())
                        .param(paramName, "Tôi học tiếng Hàn."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/attempts/" + attempt.getId() + "/result"));

        // Perform GET result view -> should redirect to result template for WRITING
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result"))
                .andExpect(model().attributeExists("result"));

        // Perform GET detailed result view -> should redirect to result-detail template for WRITING
        mockMvc.perform(get("/practice/attempts/" + attempt.getId() + "/result/detail"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/result-detail"))
                .andExpect(model().attributeExists("result"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testProfileRedirectsToProgress() throws Exception {
        mockMvc.perform(get("/practice/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/progress"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testProgressAuthenticated() throws Exception {
        mockMvc.perform(get("/practice/progress"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/progress"))
                .andExpect(model().attributeExists("overview"))
                .andExpect(model().attributeExists("analytics"))
                .andExpect(model().attributeExists("overviewJson"))
                .andExpect(model().attributeExists("analyticsJson"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void testUploadDeniedForStudent() throws Exception {
        mockMvc.perform(get("/practice/manage/import"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testUploadAllowedForLecturer() throws Exception {
        mockMvc.perform(get("/practice/manage/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("practice/manage/import-wizard"));

        // Check legacy upload redirect
        mockMvc.perform(get("/practice/manage/upload"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/manage/import"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testManualDraftForLecturer() throws Exception {
        // GET /practice/manage/create redirects to /practice/manage/drafts/{draftId}
        mockMvc.perform(get("/practice/manage/create"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/practice/manage/drafts/*"));

        // Check legacy manual redirect
        mockMvc.perform(get("/practice/manage/manual"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/practice/manage/create"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testPublishDraft() throws Exception {
        String draftJson = """
        {
          "document": {
            "detectedCategory": "TOPIK_II",
            "title": "Đề mới xuất bản",
            "confidence": 1.0
          },
          "sections": [
            {
              "title": "Phần Đọc",
              "skill": "READING",
              "durationMinutes": 40,
              "groups": [
                {
                  "label": "1",
                  "questionFrom": 1,
                  "questionTo": 1,
                  "instruction": "Chỉ dẫn",
                  "questions": [
                    {
                      "questionNo": 1,
                      "questionType": "SINGLE_CHOICE",
                      "prompt": "Câu 1",
                      "options": ["A", "B"],
                      "answer": { "value": "1" },
                      "explanationVi": "Vì đúng",
                      "points": 5.0
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;

        com.ksh.entities.PracticeDraft draft = new com.ksh.entities.PracticeDraft(
                "Draft test", "Desc", "TOPIK_II", "GLOBAL", null, "DRAFT", lecturer.getId(), draftJson
        );
        draft = draftRepository.saveAndFlush(draft);

        mockMvc.perform(post("/practice/manage/drafts/" + draft.getId() + "/publish")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/practice/sets/*"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testOptimisticLockingConflict() throws Exception {
        com.ksh.entities.PracticeDraft draft = new com.ksh.entities.PracticeDraft(
                "Lock Test", "Desc", "TOPIK_II", "GLOBAL", null, "DRAFT", lecturer.getId(), "{}"
        );
        draft = draftRepository.saveAndFlush(draft);
        int originalVersion = draft.getVersion();

        mockMvc.perform(post("/practice/manage/drafts/" + draft.getId() + "/autosave")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"draftJson\":\"{}\",\"title\":\"Lock Test Sửa\",\"version\":" + originalVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(originalVersion + 1));

        mockMvc.perform(post("/practice/manage/drafts/" + draft.getId() + "/autosave")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"draftJson\":\"{}\",\"title\":\"Ghi đè lỗi\",\"version\":" + originalVersion + "}"))
                .andExpect(status().is4xxClientError()) // HTTP 409 Conflict
                .andExpect(jsonPath("$.status").value("conflict"));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void testPublishEditAndRestoreRevision() throws Exception {
        String draftJson = """
        {
          "document": {
            "detectedCategory": "TOPIK_II",
            "title": "Học liệu gốc",
            "confidence": 1.0
          },
          "sections": [
            {
              "title": "Phần Đọc",
              "skill": "READING",
              "durationMinutes": 40,
              "groups": [
                {
                  "label": "1",
                  "questionFrom": 1,
                  "questionTo": 1,
                  "instruction": "Chỉ dẫn",
                  "questions": [
                    {
                      "questionNo": 1,
                      "questionType": "SINGLE_CHOICE",
                      "prompt": "Câu 1 ban đầu",
                      "options": ["A", "B"],
                      "answer": { "value": "1" },
                      "explanationVi": "Vì đúng",
                      "points": 5.0
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;

        com.ksh.entities.PracticeDraft draft = new com.ksh.entities.PracticeDraft(
                "Học liệu gốc", "Desc", "TOPIK_II", "GLOBAL", null, "DRAFT", lecturer.getId(), draftJson
        );
        draft = draftRepository.saveAndFlush(draft);

        // 1. Publish first time
        mockMvc.perform(post("/practice/manage/drafts/" + draft.getId() + "/publish").with(csrf()))
                .andExpect(status().is3xxRedirection());

        List<PracticeSet> sets = setRepository.findAll();
        PracticeSet publishedSet = sets.stream()
                .filter(s -> "Học liệu gốc".equals(s.getTitle()))
                .findFirst().orElseThrow();

        // 2. Edit existing set -> redirects to /practice/manage/drafts/{id}
        mockMvc.perform(get("/practice/manage/sets/" + publishedSet.getId() + "/edit"))
                .andExpect(status().is3xxRedirection());

        List<com.ksh.entities.PracticeDraft> drafts = draftRepository.findByOwnerIdOrderByUpdatedAtDesc(lecturer.getId());
        com.ksh.entities.PracticeDraft editDraft = drafts.stream()
                .filter(d -> d.getPublishedSetId() != null && d.getPublishedSetId().equals(publishedSet.getId()))
                .findFirst().orElseThrow();

        // Modify a question prompt in the edit draft JSON
        String updatedJson = draftJson.replace("Câu 1 ban đầu", "Câu 1 đã sửa");
        
        mockMvc.perform(post("/practice/manage/drafts/" + editDraft.getId() + "/autosave")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"draftJson\":" + objectMapper.writeValueAsString(updatedJson) + ",\"title\":\"Học liệu đã sửa\",\"version\":" + editDraft.getVersion() + "}"))
                .andExpect(status().isOk());

        // 3. Publish modified draft to update original set
        mockMvc.perform(post("/practice/manage/drafts/" + editDraft.getId() + "/publish").with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 4. Assert a revision log entry was recorded
        List<com.ksh.entities.PracticeEditLog> logs = editLogRepository.findBySetIdOrderByEditedAtDesc(publishedSet.getId());
        assertThat(logs).isNotEmpty();
        com.ksh.entities.PracticeEditLog lastLog = logs.stream()
                .filter(l -> "QUESTIONS,METADATA".equals(l.getEditType()) || "QUESTIONS".equals(l.getEditType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No edit log found with QUESTIONS or QUESTIONS,METADATA edit type"));
        assertThat(lastLog.getBeforeSnapshotJson()).contains("Câu 1 ban đầu");
        assertThat(lastLog.getAfterSnapshotJson()).contains("Câu 1 đã sửa");

        // 5. Restore the revision
        revisionService.restoreRevision(lastLog.getId(), lecturer.getId());

        // Assert the questions in the active published set have reverted to "Câu 1 ban đầu"
        List<PracticeQuestion> revertedQs = questionRepository.findBySetIdOrderByDisplayOrderAsc(publishedSet.getId());
        assertThat(revertedQs).isNotEmpty();
        assertThat(revertedQs.get(0).getPrompt()).isEqualTo("Câu 1 ban đầu");
    }
}
