package com.codeassess.plagiarism.controller;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealCreateRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.service.StudentAppealService;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(StudentAppealController.class)
@org.springframework.context.annotation.Import(com.codeassess.security.SecurityConfig.class)
public class StudentAppealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentAppealService studentAppealService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.codeassess.security.CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private User student;

    @BeforeEach
    public void setup() {
        student = User.builder().id("student1").email("student@test.com").build();
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    public void createAppeal_Success() throws Exception {
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(student));

        AppealCreateRequestDto req = new AppealCreateRequestDto();
        req.setSubmissionId("sub1");
        req.setBasis("Valid basis");
        req.setExplanation("Valid explanation text with sufficient length");
        
        AppealResponseDto res = AppealResponseDto.builder()
                .id("apl_123")
                .status("PENDING")
                .explanation("Valid explanation text with sufficient length")
                .build();
                
        when(studentAppealService.createAppeal(any(AppealCreateRequestDto.class), eq(student)))
                .thenReturn(res);

        mockMvc.perform(post("/api/student/appeals").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("apl_123"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    public void createAppeal_Conflict() throws Exception {
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(student));

        AppealCreateRequestDto req = new AppealCreateRequestDto();
        req.setSubmissionId("sub1");
        req.setBasis("Valid basis");
        req.setExplanation("Valid explanation text with sufficient length");
        
        when(studentAppealService.createAppeal(any(), any()))
                .thenThrow(new ConflictException("Already exists"));

        mockMvc.perform(post("/api/student/appeals").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    public void createAppeal_ExplanationTooShort_Returns400() throws Exception {
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(student));

        AppealCreateRequestDto req = new AppealCreateRequestDto();
        req.setSubmissionId("sub1");
        req.setBasis("Valid basis");
        req.setExplanation("Too short"); // 9 chars < 20

        mockMvc.perform(post("/api/student/appeals").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("20")));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    public void createAppeal_ExplanationTooLong_Returns400() throws Exception {
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(student));

        AppealCreateRequestDto req = new AppealCreateRequestDto();
        req.setSubmissionId("sub1");
        req.setBasis("Valid basis");
        req.setExplanation("a".repeat(1001)); // 1001 chars > 1000

        mockMvc.perform(post("/api/student/appeals").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1000")));
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = "STUDENT")
    public void createAppeal_SubmissionNotFoundOrNotOwned_Returns404() throws Exception {
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(student));

        AppealCreateRequestDto req = new AppealCreateRequestDto();
        req.setSubmissionId("sub-other");
        req.setBasis("Valid basis");
        req.setExplanation("Valid explanation text with sufficient length");

        when(studentAppealService.createAppeal(any(), any()))
                .thenThrow(new ResourceNotFoundException("Submission not found or access denied"));

        mockMvc.perform(post("/api/student/appeals").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
