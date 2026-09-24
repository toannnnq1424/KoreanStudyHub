package com.ksh.features.leader;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Subject;
import com.ksh.entities.User;
import com.ksh.features.admin.subjects.repository.SubjectRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.ClassCoLecturerRepository;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for LEADER shell, dashboard, assignment, and report.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaderSubjectIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private ClassCoLecturerRepository coLecturerRepository;

    private Subject cntt;
    private User leader;
    private User lecturer;

    @BeforeEach
    void setUp() {
        if (!subjectRepository.existsByCode("KT")) {
            subjectRepository.saveAndFlush(new Subject("Kinh tế", "KT", "Test fixture", true));
        }
        leader = userRepository.findByEmailIgnoreCase("leader@ksh.edu.vn").orElseThrow();
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        // Isolate the governed portfolio from unrelated seeded subjects.
        subjectRepository.findAll().stream()
                .filter(subject -> leader.getId().equals(subject.getLeaderUserId()))
                .forEach(subject -> {
                    subject.assignLeader(null);
                    subjectRepository.save(subject);
                });
        cntt = subjectRepository.findAll().stream()
                .filter(d -> "CNTT".equals(d.getCode()))
                .findFirst().orElseGet(() -> subjectRepository.saveAndFlush(
                        new Subject("Công nghệ thông tin", "CNTT", "Test fixture", true)));

        // Ensure LEADER resolution via leader_user_id.
        cntt.applyEdit(cntt.getName(), cntt.getCode(), cntt.getDescription(), true);
        cntt.assignLeader(leader.getId());
        subjectRepository.save(cntt);
        leader.promoteToLeader(cntt.getId());
        userRepository.save(leader);

        lecturer.setSubjectId(cntt.getId());
        userRepository.save(lecturer);
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void dashboard_ok_for_leader() throws Exception {
        mockMvc.perform(get("/leader"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/dashboard"))
                .andExpect(content().string(containsString("Dashboard môn học")));
    }

    @Test
    @WithUserDetails(value = "student@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void dashboard_403_for_student() throws Exception {
        mockMvc.perform(get("/leader"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void dashboard_lists_only_subject_classes() throws Exception {
        ClassEntity inSubject = new ClassEntity(
                "Lớp CNTT Leader", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inSubject.setCode("HCN01");
        inSubject.setSubjectId(cntt.getId());
        classRepository.save(inSubject);

        Subject other = subjectRepository.findAll().stream()
                .filter(d -> "KT".equals(d.getCode()))
                .findFirst().orElseThrow();
        ClassEntity outSubject = new ClassEntity(
                "Lớp KT Outside", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        outSubject.setCode("HKT01");
        outSubject.setSubjectId(other.getId());
        classRepository.save(outSubject);

        mockMvc.perform(get("/leader"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lớp CNTT Leader")))
                .andExpect(content().string(not(containsString("Lớp KT Outside"))));
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void assign_page_lists_subject_classes() throws Exception {
        ClassEntity inSubject = new ClassEntity(
                "Lớp Assign", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inSubject.setCode("HAS01");
        inSubject.setSubjectId(cntt.getId());
        classRepository.save(inSubject);

        mockMvc.perform(get("/leader/assign"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/assign"))
                .andExpect(content().string(containsString("Lớp Assign")));
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void assign_page_and_submit_allow_an_active_lecturer_from_another_subject() throws Exception {
        Subject other = subjectRepository.findAll().stream()
                .filter(d -> "KT".equals(d.getCode()))
                .findFirst().orElseThrow();
        lecturer.setSubjectId(other.getId());
        userRepository.saveAndFlush(lecturer);

        ClassEntity inSubject = new ClassEntity(
                "Lớp cần đồng giảng", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inSubject.setCode("HXL01");
        inSubject.setSubjectId(cntt.getId());
        ClassEntity saved = classRepository.saveAndFlush(inSubject);

        mockMvc.perform(get("/leader/assign"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(lecturer.getEmail())));

        mockMvc.perform(post("/leader/assign/" + saved.getId()).with(csrf())
                        .param("lecturerId", String.valueOf(lecturer.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("flashSuccess"));

        assertThat(coLecturerRepository.existsByClassIdAndLecturerId(
                saved.getId(), lecturer.getId())).isTrue();
        assertThat(classRepository.findById(saved.getId()).orElseThrow().getLecturerId())
                .isEqualTo(leader.getId());
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void add_co_lecturer_same_subject_preserves_owner() throws Exception {
        ClassEntity inSubject = new ClassEntity(
                "Lớp Reassign", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inSubject.setCode("HRS01");
        inSubject.setSubjectId(cntt.getId());
        ClassEntity saved = classRepository.save(inSubject);

        mockMvc.perform(post("/leader/assign/" + saved.getId()).with(csrf())
                        .param("lecturerId", String.valueOf(lecturer.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("flashSuccess"));

        ClassEntity updated = classRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getLecturerId()).isEqualTo(leader.getId());
        assertThat(updated.getCreatedBy()).isEqualTo(leader.getId());
        assertThat(updated.getSubjectId()).isEqualTo(cntt.getId());
        assertThat(coLecturerRepository.existsByClassIdAndLecturerId(
                saved.getId(), lecturer.getId())).isTrue();
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void add_co_lecturer_cross_subject_class_denied() throws Exception {
        Subject other = subjectRepository.findAll().stream()
                .filter(d -> "KT".equals(d.getCode()))
                .findFirst().orElseThrow();
        ClassEntity out = new ClassEntity(
                "Lớp Foreign", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        out.setCode("HFR01");
        out.setSubjectId(other.getId());
        ClassEntity saved = classRepository.save(out);

        mockMvc.perform(post("/leader/assign/" + saved.getId()).with(csrf())
                        .param("lecturerId", String.valueOf(lecturer.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void report_ok_and_scoped() throws Exception {
        ClassEntity inSubject = new ClassEntity(
                "Lớp Report", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inSubject.setCode("HRP01");
        inSubject.setSubjectId(cntt.getId());
        classRepository.save(inSubject);

        mockMvc.perform(get("/leader/report"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/report"))
                .andExpect(content().string(containsString("Lớp Report")));
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void retired_approvals_page_redirects_to_dashboard() throws Exception {
        ClassEntity pending = new ClassEntity(
                "Lớp chờ duyệt", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        pending.setCode("HAP01");
        pending.setSubjectId(cntt.getId());
        classRepository.save(pending);

        mockMvc.perform(get("/leader/approvals"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/leader"));
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void retired_review_actions_cannot_mutate_active_class() throws Exception {
        ClassEntity pending = new ClassEntity(
                "Lớp được duyệt", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        pending.setCode("HAP02");
        pending.setSubjectId(cntt.getId());
        ClassEntity saved = classRepository.saveAndFlush(pending);

        mockMvc.perform(post("/leader/approvals/" + saved.getId() + "/approve").with(csrf()))
                .andExpect(status().isGone());
        mockMvc.perform(post("/leader/approvals/" + saved.getId() + "/reject").with(csrf())
                        .param("note", "Retired action"))
                .andExpect(status().isGone());

        ClassEntity approved = classRepository.findById(saved.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(ClassEntity.STATUS_ACTIVE);
        assertThat(approved.getApprovedBy()).isNull();
        assertThat(approved.getApprovedAt()).isNull();
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void empty_state_when_no_subject() throws Exception {
        // Clear leader assignment and subject_id so resolver returns empty.
        for (Subject d : subjectRepository.findAll()) {
            if (leader.getId().equals(d.getLeaderUserId())) {
                d.assignLeader(null);
                subjectRepository.save(d);
            }
        }
        leader.setSubjectId(null);
        userRepository.save(leader);

        mockMvc.perform(get("/leader"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/dashboard"))
                .andExpect(model().attribute("emptySubject", true))
                .andExpect(model().attribute("leaderSubject", org.hamcrest.Matchers.nullValue()));
    }
    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void managed_subjects_list_and_detail_are_scoped_to_current_assignments() throws Exception {
        Subject first = subjectRepository.saveAndFlush(
                new Subject("UC09 Alpha", "UC09A", "First governed description", true));
        first.assignLeader(leader.getId());
        Subject second = subjectRepository.saveAndFlush(
                new Subject("UC09 Beta", "UC09B", "Second governed description", true));
        second.assignLeader(leader.getId());
        Subject outsider = subjectRepository.saveAndFlush(
                new Subject("UC09 Foreign", "UC09X", "Foreign description", true));
        Subject inactive = subjectRepository.saveAndFlush(
                new Subject("UC09 Hidden", "UC09I", "Hidden description", false));
        inactive.assignLeader(leader.getId());
        cntt.assignLeader(null);
        subjectRepository.saveAndFlush(cntt);
        leader.setSubjectId(outsider.getId());
        userRepository.saveAndFlush(leader);
        subjectRepository.flush();

        String list = mockMvc.perform(get("/leader/subjects"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(list).select(".leader-managed-subject-link").eachAttr("href"))
                .containsExactly("/leader/subjects/" + first.getId(),
                        "/leader/subjects/" + second.getId());
        assertThat(Jsoup.parse(list).select(".leader-managed-subject-link code").eachText())
                .containsExactly("UC09A", "UC09B");
        assertThat(list).contains("UC09 Alpha", "UC09 Beta")
                .doesNotContain("UC09 Foreign", "UC09 Hidden");

        mockMvc.perform(get("/leader/subjects/" + first.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("First governed description")))
                .andExpect(content().string(containsString("UC09A")))
                .andExpect(content().string(containsString("UC09 Alpha")))
                .andExpect(content().string(containsString("/leader/subjects")))
                .andExpect(content().string(containsString(
                        "/leader/question-bank?subjectId=" + first.getId())))
                .andExpect(content().string(not(containsString("Second governed description"))));
        mockMvc.perform(get("/leader/subjects/" + second.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Second governed description")))
                .andExpect(content().string(containsString(
                        "/leader/question-bank?subjectId=" + second.getId())))
                .andExpect(content().string(not(containsString("First governed description"))))
                .andExpect(content().string(containsString("UC09B")))
                .andExpect(content().string(containsString("UC09 Beta")));
    }

    @Test
    @WithUserDetails(value = "leader@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void managed_subject_detail_rechecks_assignment_and_visibility_on_every_request() throws Exception {
        Subject foreign = subjectRepository.saveAndFlush(
                new Subject("UC09 Foreign", "UC09F", "Private foreign description", true));
        Subject hidden = subjectRepository.saveAndFlush(
                new Subject("UC09 Hidden", "UC09H", "Private hidden description", false));
        hidden.assignLeader(leader.getId());
        leader.setSubjectId(foreign.getId());
        userRepository.saveAndFlush(leader);
        subjectRepository.flush();

        mockMvc.perform(get("/leader/subjects/" + foreign.getId()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Private foreign description"))));
        mockMvc.perform(get("/leader/subjects/" + hidden.getId()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Private hidden description"))));
        mockMvc.perform(get("/leader/subjects/" + cntt.getId()))
                .andExpect(status().isOk());
        cntt.assignLeader(null);
        subjectRepository.saveAndFlush(cntt);
        mockMvc.perform(get("/leader/subjects/" + cntt.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/leader/subjects/999999999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/leader/subjects"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn chưa được gán môn học")))
                .andExpect(content().string(not(containsString("UC09 Foreign"))));
    }

    @Test
    @WithUserDetails(value = "student@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void managed_subjects_are_not_available_to_students() throws Exception {
        mockMvc.perform(get("/leader/subjects")).andExpect(status().isForbidden());
        mockMvc.perform(get("/leader/subjects/" + cntt.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(value = "lecturer@ksh.edu.vn", setupBefore = TestExecutionEvent.TEST_EXECUTION)
    void managed_subjects_are_not_available_to_lecturers() throws Exception {
        mockMvc.perform(get("/leader/subjects")).andExpect(status().isForbidden());
        mockMvc.perform(get("/leader/subjects/" + cntt.getId()))
                .andExpect(status().isForbidden());
    }
}
