package com.codeassess.submission.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.service.StudentSubmissionService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentSubmissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentSubmissionService studentSubmissionService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EXAM_ID = "exam-test-001";

    private User studentUser;
    private User teacherUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        studentUser = User.builder()
                .id("student-001")
                .email("student@test.com")
                .passwordHash("hashed")
                .role(Role.STUDENT)
                .fullName("Test Student")
                .build();

        teacherUser = User.builder()
                .id("teacher-001")
                .email("teacher@test.com")
                .passwordHash("hashed")
                .role(Role.TEACHER)
                .fullName("Test Teacher")
                .build();

        adminUser = User.builder()
                .id("admin-001")
                .email("admin@test.com")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .fullName("Test Admin")
                .build();
    }

    private SubmissionDetailResponse sampleResponse() {
        return SubmissionDetailResponse.builder()
                .id("sub-001")
                .examId(EXAM_ID)
                .studentId("student-001")
                .status("PENDING")
                .sourceCode("public class Test {}")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ===================================================================
    // A. Authentication — anonymous -> 401
    // ===================================================================

    @Test
    void submitCode_anonymous_returns401() throws Exception {
        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ===================================================================
    // B. Role enforcement
    // ===================================================================

    @Test
    void submitCode_teacher_returns403() throws Exception {
        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(teacherUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitCode_admin_returns403() throws Exception {
        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(adminUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ===================================================================
    // C. Ownership — student not enrolled in class -> 404
    // ===================================================================

    @Test
    void submitCode_studentNotInClass_returns404() throws Exception {
        Mockito.when(studentSubmissionService.submitCode(anyString(), anyString(), any()))
                .thenThrow(new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ===================================================================
    // D. Exam state
    // ===================================================================

    @Test
    void submitCode_examNotFound_returns404() throws Exception {
        Mockito.when(studentSubmissionService.submitCode(anyString(), anyString(), any()))
                .thenThrow(new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(post("/api/student/exams/nonexistent-exam/submissions")
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitCode_inactiveExam_returns400() throws Exception {
        Mockito.when(studentSubmissionService.submitCode(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Exam is not currently active for submissions"));

        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // ===================================================================
    // E. Successful submission — 201, stable public ID, PENDING status
    // ===================================================================

    @Test
    void submitCode_validStudent_returns201WithPublicId() throws Exception {
        Mockito.when(studentSubmissionService.submitCode(anyString(), anyString(), any()))
                .thenReturn(sampleResponse());

        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("sub-001"))
                .andExpect(jsonPath("$.examId").value(EXAM_ID))
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<String> examIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> studentIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SubmissionCreateRequest> requestCaptor = ArgumentCaptor.forClass(SubmissionCreateRequest.class);
        Mockito.verify(studentSubmissionService).submitCode(examIdCaptor.capture(), studentIdCaptor.capture(), requestCaptor.capture());
        assertThat(examIdCaptor.getValue()).isEqualTo(EXAM_ID);
        assertThat(studentIdCaptor.getValue()).isEqualTo("student-001");
        assertThat(requestCaptor.getValue().getSourceCode()).isEqualTo("public class Test {}");
    }

    // ===================================================================
    // E. Validation — empty sourceCode rejected -> 400
    // ===================================================================

    @Test
    void submitCode_emptySourceCode_returns400() throws Exception {
        SubmissionCreateRequest request = new SubmissionCreateRequest("");

        mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===================================================================
    // I. Security — studentId from request body is ignored
    // ===================================================================

    @Test
    void submitCode_requestBodyWithStudentIdIsIgnored() throws Exception {
        // SubmissionCreateRequest has no studentId field.
        // Extra JSON field "studentId" must be ignored by Jackson.
        // The response studentId comes from server-resolved SecurityContext identity.
        Mockito.when(studentSubmissionService.submitCode(anyString(), anyString(), any()))
                .thenReturn(sampleResponse());

        String body = "{\"sourceCode\":\"public class Test {}\",\"studentId\":\"injected-student-id\"}";

        mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value("student-001"));
    }
}
