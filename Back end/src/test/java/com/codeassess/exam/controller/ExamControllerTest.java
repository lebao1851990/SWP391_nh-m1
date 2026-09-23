package com.codeassess.exam.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.service.AdminExamService;
import com.codeassess.exam.service.StudentExamService;
import com.codeassess.exam.service.TeacherExamService;
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
class ExamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherExamService teacherExamService;

    @MockBean
    private StudentExamService studentExamService;

    @MockBean
    private AdminExamService adminExamService;

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
        mockMvc.perform(get("/api/teacher/exams")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/student/exams")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/exams")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessTeacherApi() throws Exception {
        mockMvc.perform(get("/api/teacher/exams")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotAccessStudentApi() throws Exception {
        mockMvc.perform(get("/api/student/exams")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/exams")).andExpect(status().isForbidden());
    }

    @Test
    void teacherCanAccessExamBelongingToOwnClass() throws Exception {
        ExamResponse response = ExamResponse.builder().id("exam-1").title("Midterm").build();
        Mockito.when(teacherExamService.getTeacherExamById("exam-1", "teacher-1")).thenReturn(response);

        mockMvc.perform(get("/api/teacher/exams/exam-1").with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Midterm"));
    }

    @Test
    void studentCanAccessExamBelongingToEnrolledClass() throws Exception {
        ExamResponse response = ExamResponse.builder().id("exam-1").title("Midterm").build();
        Mockito.when(studentExamService.getStudentExamById("exam-1", "student-1")).thenReturn(response);

        mockMvc.perform(get("/api/student/exams/exam-1").with(user(studentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Midterm"));
    }

    @Test
    void studentCannotAccessExamFromClassNotEnrolledIn() throws Exception {
        Mockito.when(studentExamService.getStudentExamById("exam-2", "student-1"))
                .thenThrow(new ResourceNotFoundException("Not found or access denied"));

        mockMvc.perform(get("/api/student/exams/exam-2").with(user(studentUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanAccessExam() throws Exception {
        ExamResponse response = ExamResponse.builder().id("exam-1").title("Midterm").build();
        Mockito.when(adminExamService.getExamById("exam-1")).thenReturn(response);

        mockMvc.perform(get("/api/admin/exams/exam-1").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Midterm"));
    }
}
