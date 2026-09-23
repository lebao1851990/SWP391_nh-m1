package com.codeassess.submission.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.service.AdminSubmissionService;
import com.codeassess.submission.service.StudentSubmissionService;
import com.codeassess.submission.service.TeacherSubmissionService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherSubmissionService teacherSubmissionService;

    @MockBean
    private StudentSubmissionService studentSubmissionService;

    @MockBean
    private AdminSubmissionService adminSubmissionService;

    private User teacherUser;
    private User studentUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder().id("teacher-1").email("teacher@test.com").role(Role.TEACHER).build();
        studentUser = User.builder().id("student-1").email("student@test.com").role(Role.STUDENT).build();
        adminUser = User.builder().id("admin-1").email("admin@test.com").role(Role.ADMIN).build();
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/teacher/submissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/student/submissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/submissions")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessTeacherApi() throws Exception {
        mockMvc.perform(get("/api/teacher/submissions")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotAccessStudentApi() throws Exception {
        mockMvc.perform(get("/api/student/submissions")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/submissions")).andExpect(status().isForbidden());
    }

    @Test
    void teacherCanAccessSubmissionFromOwnClass() throws Exception {
        SubmissionDetailResponse response = SubmissionDetailResponse.builder().id("sub-1").sourceCode("System.out.println();").build();
        Mockito.when(teacherSubmissionService.getTeacherSubmissionById("sub-1", "teacher-1")).thenReturn(response);

        mockMvc.perform(get("/api/teacher/submissions/sub-1").with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value("System.out.println();"));
    }

    @Test
    void studentCanAccessOwnSubmission() throws Exception {
        SubmissionDetailResponse response = SubmissionDetailResponse.builder().id("sub-1").sourceCode("public class Main {}").build();
        Mockito.when(studentSubmissionService.getStudentSubmissionById("sub-1", "student-1")).thenReturn(response);

        mockMvc.perform(get("/api/student/submissions/sub-1").with(user(studentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value("public class Main {}"));
    }

    @Test
    void studentCannotAccessAnotherStudentSubmission() throws Exception {
        Mockito.when(studentSubmissionService.getStudentSubmissionById("sub-2", "student-1"))
                .thenThrow(new ResourceNotFoundException("Not found or access denied"));

        mockMvc.perform(get("/api/student/submissions/sub-2").with(user(studentUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanAccessSubmission() throws Exception {
        SubmissionDetailResponse response = SubmissionDetailResponse.builder().id("sub-1").status("GRADED").build();
        Mockito.when(adminSubmissionService.getSubmissionById("sub-1")).thenReturn(response);

        mockMvc.perform(get("/api/admin/submissions/sub-1").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRADED"));
    }
}
