package com.codeassess.classes.controller;

import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.service.AdminClassService;
import com.codeassess.classes.service.StudentClassService;
import com.codeassess.classes.service.TeacherClassService;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClassControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherClassService teacherClassService;

    @MockBean
    private StudentClassService studentClassService;

    @MockBean
    private AdminClassService adminClassService;

    private User teacherUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder().id("teacher-1").email("teacher@test.com").role(Role.TEACHER).build();
        studentUser = User.builder().id("student-1").email("student@test.com").role(Role.STUDENT).build();
    }

    // 1. Unauthenticated -> 401
    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/teacher/classes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/student/classes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/classes")).andExpect(status().isUnauthorized());
    }

    // 2. Role authorization -> 403
    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessTeacherApi() throws Exception {
        mockMvc.perform(get("/api/teacher/classes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotAccessStudentApi() throws Exception {
        mockMvc.perform(get("/api/student/classes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/classes")).andExpect(status().isForbidden());
    }

    // 3. Valid access + Resource Authorization
    @Test
    void teacherCanAccessOwnClass() throws Exception {
        ClassResponse response = ClassResponse.builder().id("class-1").name("Math").build();
        Mockito.when(teacherClassService.getTeacherClassById("class-1", "teacher-1")).thenReturn(response);

        mockMvc.perform(get("/api/teacher/classes/class-1").with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Math"));
    }

    @Test
    void studentCanAccessEnrolledClass() throws Exception {
        ClassResponse response = ClassResponse.builder().id("class-1").name("Math").build();
        Mockito.when(studentClassService.getStudentClassById("class-1", "student-1")).thenReturn(response);

        mockMvc.perform(get("/api/student/classes/class-1").with(user(studentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Math"));
    }

    // 4. Resource not found / Access denied
    @Test
    void teacherCannotAccessTeacherBClass() throws Exception {
        Mockito.when(teacherClassService.getTeacherClassById("class-2", "teacher-1"))
                .thenThrow(new ResourceNotFoundException("Not found or access denied"));

        mockMvc.perform(get("/api/teacher/classes/class-2").with(user(teacherUser)))
                .andExpect(status().isNotFound());
    }
}
