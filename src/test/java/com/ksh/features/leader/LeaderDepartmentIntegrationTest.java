package com.ksh.features.leader;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.repository.ClassCoLecturerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class LeaderDepartmentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private ClassCoLecturerRepository coLecturerRepository;

    private Department cntt;
    private User leader;
    private User lecturer;

    @BeforeEach
    void setUp() {
        leader = userRepository.findByEmailIgnoreCase("leader@ksh.edu.vn").orElseThrow();
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        cntt = departmentRepository.findAll().stream()
                .filter(d -> "CNTT".equals(d.getCode()))
                .findFirst().orElseThrow();

        // Ensure LEADER resolution via leader_user_id.
        cntt.assignLeader(leader.getId());
        departmentRepository.save(cntt);
        leader.promoteToLeader(cntt.getId());
        userRepository.save(leader);

        lecturer.setDepartmentId(cntt.getId());
        userRepository.save(lecturer);
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void dashboard_ok_for_leader() throws Exception {
        mockMvc.perform(get("/leader"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/dashboard"))
                .andExpect(content().string(containsString("Dashboard bộ môn")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void dashboard_403_for_student() throws Exception {
        mockMvc.perform(get("/leader"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void dashboard_lists_only_department_classes() throws Exception {
        ClassEntity inDept = new ClassEntity(
                "Lớp CNTT Leader", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inDept.setCode("HCN01");
        inDept.setDepartmentId(cntt.getId());
        classRepository.save(inDept);

        Department other = departmentRepository.findAll().stream()
                .filter(d -> "KT".equals(d.getCode()))
                .findFirst().orElseThrow();
        ClassEntity outDept = new ClassEntity(
                "Lớp KT Outside", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        outDept.setCode("HKT01");
        outDept.setDepartmentId(other.getId());
        classRepository.save(outDept);

        mockMvc.perform(get("/leader"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lớp CNTT Leader")))
                .andExpect(content().string(not(containsString("Lớp KT Outside"))));
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void assign_page_lists_department_classes() throws Exception {
        ClassEntity inDept = new ClassEntity(
                "Lớp Assign", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inDept.setCode("HAS01");
        inDept.setDepartmentId(cntt.getId());
        classRepository.save(inDept);

        mockMvc.perform(get("/leader/assign"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/assign"))
                .andExpect(content().string(containsString("Lớp Assign")));
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void add_co_lecturer_same_subject_preserves_owner() throws Exception {
        ClassEntity inDept = new ClassEntity(
                "Lớp Reassign", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inDept.setCode("HRS01");
        inDept.setDepartmentId(cntt.getId());
        ClassEntity saved = classRepository.save(inDept);

        mockMvc.perform(post("/leader/assign/" + saved.getId()).with(csrf())
                        .param("lecturerId", String.valueOf(lecturer.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("flashSuccess"));

        ClassEntity updated = classRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getLecturerId()).isEqualTo(leader.getId());
        assertThat(updated.getCreatedBy()).isEqualTo(leader.getId());
        assertThat(updated.getDepartmentId()).isEqualTo(cntt.getId());
        assertThat(coLecturerRepository.existsByClassIdAndLecturerId(
                saved.getId(), lecturer.getId())).isTrue();
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void add_co_lecturer_cross_subject_class_denied() throws Exception {
        Department other = departmentRepository.findAll().stream()
                .filter(d -> "KT".equals(d.getCode()))
                .findFirst().orElseThrow();
        ClassEntity out = new ClassEntity(
                "Lớp Foreign", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        out.setCode("HFR01");
        out.setDepartmentId(other.getId());
        ClassEntity saved = classRepository.save(out);

        mockMvc.perform(post("/leader/assign/" + saved.getId()).with(csrf())
                        .param("lecturerId", String.valueOf(lecturer.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void report_ok_and_scoped() throws Exception {
        ClassEntity inDept = new ClassEntity(
                "Lớp Report", leader.getId(), leader.getId(),
                "desc", null, null, 50);
        inDept.setCode("HRP01");
        inDept.setDepartmentId(cntt.getId());
        classRepository.save(inDept);

        mockMvc.perform(get("/leader/report"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/report"))
                .andExpect(content().string(containsString("Lớp Report")));
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void approvals_page_renders_pending_class() throws Exception {
        ClassEntity pending = new ClassEntity(
                "Lớp chờ duyệt", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        pending.setCode("HAP01");
        pending.setDepartmentId(cntt.getId());
        classRepository.save(pending);

        mockMvc.perform(get("/leader/approvals"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/approvals"))
                .andExpect(model().attribute("emptyDepartment", false))
                .andExpect(content().string(containsString("Lớp chờ duyệt")))
                .andExpect(content().string(containsString("Duyệt")));
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void approve_class_moves_it_out_of_draft() throws Exception {
        ClassEntity pending = new ClassEntity(
                "Lớp được duyệt", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        pending.setCode("HAP02");
        pending.setDepartmentId(cntt.getId());
        ClassEntity saved = classRepository.saveAndFlush(pending);

        mockMvc.perform(post("/leader/approvals/" + saved.getId() + "/approve").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/leader/approvals"))
                .andExpect(flash().attributeExists("flashSuccess"));

        ClassEntity approved = classRepository.findById(saved.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(ClassEntity.STATUS_ACTIVE);
        assertThat(approved.getApprovedBy()).isEqualTo(leader.getId());
        assertThat(approved.getApprovedAt()).isNotNull();
    }

    @Test
    @WithUserDetails("leader@ksh.edu.vn")
    void empty_state_when_no_department() throws Exception {
        // Clear leader assignment and department_id so resolver returns empty.
        for (Department d : departmentRepository.findAll()) {
            if (leader.getId().equals(d.getLeaderUserId())) {
                d.assignLeader(null);
                departmentRepository.save(d);
            }
        }
        leader.setDepartmentId(null);
        userRepository.save(leader);

        mockMvc.perform(get("/leader"))
                .andExpect(status().isOk())
                .andExpect(view().name("leader/dashboard"))
                .andExpect(model().attribute("emptyDepartment", true))
                .andExpect(model().attribute("leaderDepartment", org.hamcrest.Matchers.nullValue()));
    }
}
