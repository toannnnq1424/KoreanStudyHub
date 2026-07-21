package com.ksh.features.head;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.classes.repository.ClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for HEAD shell, dashboard, assignment, and report.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HeadDepartmentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClassRepository classRepository;

    private Department cntt;
    private User head;
    private User lecturer;

    @BeforeEach
    void setUp() {
        head = userRepository.findByEmailIgnoreCase("head@ksh.edu.vn").orElseThrow();
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
        cntt = departmentRepository.findAll().stream()
                .filter(d -> "CNTT".equals(d.getCode()))
                .findFirst().orElseThrow();

        // Ensure HEAD resolution via head_user_id.
        cntt.assignHead(head.getId());
        departmentRepository.save(cntt);
        head.promoteToHead(cntt.getId());
        userRepository.save(head);

        lecturer.setDepartmentId(cntt.getId());
        userRepository.save(lecturer);
    }

    @Test
    @WithUserDetails("head@ksh.edu.vn")
    void dashboard_ok_for_head() throws Exception {
        mockMvc.perform(get("/head"))
                .andExpect(status().isOk())
                .andExpect(view().name("head/dashboard"))
                .andExpect(content().string(containsString("Dashboard bộ môn")));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void dashboard_403_for_student() throws Exception {
        mockMvc.perform(get("/head"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("head@ksh.edu.vn")
    void dashboard_lists_only_department_classes() throws Exception {
        ClassEntity inDept = new ClassEntity(
                "Lớp CNTT Head", head.getId(), head.getId(),
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

        mockMvc.perform(get("/head"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lớp CNTT Head")))
                .andExpect(content().string(not(containsString("Lớp KT Outside"))));
    }

    @Test
    @WithUserDetails("head@ksh.edu.vn")
    void assign_page_lists_department_classes() throws Exception {
        ClassEntity inDept = new ClassEntity(
                "Lớp Assign", head.getId(), head.getId(),
                "desc", null, null, 50);
        inDept.setCode("HAS01");
        inDept.setDepartmentId(cntt.getId());
        classRepository.save(inDept);

        mockMvc.perform(get("/head/assign"))
                .andExpect(status().isOk())
                .andExpect(view().name("head/assign"))
                .andExpect(content().string(containsString("Lớp Assign")));
    }

    @Test
    @WithUserDetails("head@ksh.edu.vn")
    void reassign_lecturer_same_department() throws Exception {
        ClassEntity inDept = new ClassEntity(
                "Lớp Reassign", head.getId(), head.getId(),
                "desc", null, null, 50);
        inDept.setCode("HRS01");
        inDept.setDepartmentId(cntt.getId());
        ClassEntity saved = classRepository.save(inDept);

        mockMvc.perform(post("/head/assign/" + saved.getId()).with(csrf())
                        .param("lecturerId", String.valueOf(lecturer.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("flashSuccess"));

        ClassEntity updated = classRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getLecturerId()).isEqualTo(lecturer.getId());
        assertThat(updated.getDepartmentId()).isEqualTo(cntt.getId());
    }

    @Test
    @WithUserDetails("head@ksh.edu.vn")
    void reassign_cross_department_class_denied() throws Exception {
        Department other = departmentRepository.findAll().stream()
                .filter(d -> "KT".equals(d.getCode()))
                .findFirst().orElseThrow();
        ClassEntity out = new ClassEntity(
                "Lớp Foreign", lecturer.getId(), lecturer.getId(),
                "desc", null, null, 50);
        out.setCode("HFR01");
        out.setDepartmentId(other.getId());
        ClassEntity saved = classRepository.save(out);

        mockMvc.perform(post("/head/assign/" + saved.getId()).with(csrf())
                        .param("lecturerId", String.valueOf(lecturer.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("head@ksh.edu.vn")
    void report_ok_and_scoped() throws Exception {
        ClassEntity inDept = new ClassEntity(
                "Lớp Report", head.getId(), head.getId(),
                "desc", null, null, 50);
        inDept.setCode("HRP01");
        inDept.setDepartmentId(cntt.getId());
        classRepository.save(inDept);

        mockMvc.perform(get("/head/report"))
                .andExpect(status().isOk())
                .andExpect(view().name("head/report"))
                .andExpect(content().string(containsString("Lớp Report")));
    }

    @Test
    @WithUserDetails("head@ksh.edu.vn")
    void empty_state_when_no_department() throws Exception {
        // Clear head assignment and department_id so resolver returns empty.
        for (Department d : departmentRepository.findAll()) {
            if (head.getId().equals(d.getHeadUserId())) {
                d.assignHead(null);
                departmentRepository.save(d);
            }
        }
        head.setDepartmentId(null);
        userRepository.save(head);

        mockMvc.perform(get("/head"))
                .andExpect(status().isOk())
                .andExpect(view().name("head/dashboard"))
                .andExpect(model().attribute("emptyDepartment", true))
                .andExpect(model().attribute("headDepartment", org.hamcrest.Matchers.nullValue()));
    }
}
