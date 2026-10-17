package com.codeassess.ai.controller;

import com.codeassess.ai.dto.StudentAiMode;
import com.codeassess.ai.dto.StudentAiRequest;
import com.codeassess.ai.dto.StudentAiResponse;
import com.codeassess.ai.dto.StudentSandboxAiRequest;
import com.codeassess.ai.service.StudentAiService;
import com.codeassess.common.exception.GlobalExceptionHandler;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAiController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class StudentAiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentAiService studentAiService;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User student;

    @BeforeEach
    void setUp() {
        student = User.builder().id("student-123").email("student@test.com").build();
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(student));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    void testGetSubmissionFeedback_Success() throws Exception {
        StudentAiRequest request = new StudentAiRequest(StudentAiMode.CODE_EXPLANATION, null);
        StudentAiResponse response = new StudentAiResponse(StudentAiMode.CODE_EXPLANATION, "Explained code");

        when(studentAiService.getSubmissionFeedback(eq("sub-1"), eq("student-123"), any(StudentAiRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/student/submissions/sub-1/ai/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CODE_EXPLANATION"))
                .andExpect(jsonPath("$.explanation").value("Explained code"))
                .andExpect(jsonPath("$.advisoryNotice").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    void testExplainSubmissionError_Success() throws Exception {
        StudentAiResponse response = new StudentAiResponse(StudentAiMode.COMPILER_ERROR, "Error explanation");

        when(studentAiService.getSubmissionFeedback(eq("sub-1"), eq("student-123"), any(StudentAiRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/student/submissions/sub-1/ai/explain-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("COMPILER_ERROR"))
                .andExpect(jsonPath("$.explanation").value("Error explanation"));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    void testGetSandboxFeedback_Success() throws Exception {
        StudentSandboxAiRequest request = new StudentSandboxAiRequest(
                StudentAiMode.DEBUG_HINTS,
                "public class A {}",
                "error at line 1"
        );
        StudentAiResponse response = new StudentAiResponse(StudentAiMode.DEBUG_HINTS, "Debugging hints");

        when(studentAiService.getSandboxFeedback(eq("student-123"), any(StudentSandboxAiRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/student/sandbox/ai/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEBUG_HINTS"))
                .andExpect(jsonPath("$.explanation").value("Debugging hints"));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    void testGetSandboxFeedback_MissingSourceCode_BadRequest() throws Exception {
        StudentSandboxAiRequest request = new StudentSandboxAiRequest(
                StudentAiMode.DEBUG_HINTS,
                "", // Blank source code is disallowed
                null
        );

        mockMvc.perform(post("/api/student/sandbox/ai/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    void testGetSubmissionFeedback_MissingMode_BadRequest() throws Exception {
        mockMvc.perform(post("/api/student/submissions/sub-1/ai/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
