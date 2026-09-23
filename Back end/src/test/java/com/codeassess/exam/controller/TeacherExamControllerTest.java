package com.codeassess.exam.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.service.TeacherExamService;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class TeacherExamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherExamService teacherExamService;

    @Test
    public void testTeacherOwnsExamReturns200() throws Exception {
        User mockUser = new User();
        mockUser.setId("teacher-id");
        mockUser.setRole(com.codeassess.user.entity.Role.TEACHER);

        when(teacherExamService.getTeacherExamById(anyString(), anyString())).thenReturn(ExamResponse.builder().build());
        when(teacherExamService.getExamSubmissions(anyString(), anyString())).thenReturn(java.util.Collections.emptyList());

        mockMvc.perform(get("/api/teacher/exams/1/submissions")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(mockUser)))
                .andExpect(status().isOk());
    }

    @Test
    public void testTeacherDoesNotOwnExamReturns404() throws Exception {
        User mockUser = new User();
        mockUser.setId("teacher-id");
        mockUser.setRole(com.codeassess.user.entity.Role.TEACHER);

        when(teacherExamService.getExamSubmissions(anyString(), anyString()))
                .thenThrow(new ResourceNotFoundException("Exam not found or access denied"));

        mockMvc.perform(get("/api/teacher/exams/non-existent-exam/submissions")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(mockUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    public void testStudentAccessesTeacherEndpointReturns403() throws Exception {
        User mockUser = new User();
        mockUser.setId("student-id");
        mockUser.setRole(com.codeassess.user.entity.Role.STUDENT);

        mockMvc.perform(get("/api/teacher/exams/1/submissions")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(mockUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    public void testAnonymousAccessesTeacherEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/teacher/exams/1/submissions"))
                .andExpect(status().isUnauthorized());
    }
}
