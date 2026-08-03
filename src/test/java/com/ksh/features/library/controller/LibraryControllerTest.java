package com.ksh.features.library.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** MockMvc contracts for Library-owned authoring and material inventory. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LibraryControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void anonymous_library_redirects_to_login() throws Exception {
        mockMvc.perform(get("/lecturer/library"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithUserDetails("student@ksh.edu.vn")
    void student_is_forbidden() throws Exception {
        mockMvc.perform(get("/lecturer/library"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void lecturer_library_page_uses_subject_lesson_flow_without_loose_attach_ui()
            throws Exception {
        mockMvc.perform(get("/lecturer/library/templates"))
                .andExpect(status().isOk())
                .andExpect(view().name("library/index"))
                .andExpect(content().string(containsString("Tạo bài học")))
                .andExpect(content().string(containsString("Mã môn")))
                .andExpect(content().string(not(containsString("libraryAttachWizard"))))
                .andExpect(content().string(not(containsString("Thêm vào lớp"))))
                .andExpect(content().string(not(containsString("Gắn vào lớp"))));
    }

    @Test
    @WithUserDetails("lecturer@ksh.edu.vn")
    void root_redirects_to_canonical_lessons_and_form_owns_uploads() throws Exception {
        mockMvc.perform(get("/lecturer/library"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/lecturer/library/templates"));

        mockMvc.perform(get("/lecturer/library/templates/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("library/lesson-form"))
                .andExpect(content().string(containsString("multipart/form-data")))
                .andExpect(content().string(containsString("Tải PDF chính")))
                .andExpect(content().string(containsString("Tải materials đính kèm")));
    }
}
