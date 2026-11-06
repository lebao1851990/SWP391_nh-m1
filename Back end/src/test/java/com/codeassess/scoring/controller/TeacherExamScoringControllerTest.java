package com.codeassess.scoring.controller;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.scoring.dto.request.QuestionOverrideRequestDto;
import com.codeassess.scoring.dto.response.ExamScoreAggregationDto;
import com.codeassess.scoring.dto.response.QuestionScoreDetailDto;
import com.codeassess.scoring.service.ExamScoreAggregationService;
import com.codeassess.scoring.service.QuestionScoreOverrideService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest(TeacherExamScoringController.class)
@org.springframework.context.annotation.Import(com.codeassess.security.SecurityConfig.class)
class TeacherExamScoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionScoreOverrideService questionScoreOverrideService;

    @MockBean
    private ExamScoreAggregationService examScoreAggregationService;

    @MockBean
    private com.codeassess.user.repository.UserRepository userRepository;

    @MockBean
    private com.codeassess.security.CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private User teacherUser;
    private User otherTeacherUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder()
                .id("teacher-owner-1")
                .email("teacher1@test.com")
                .role(Role.TEACHER)
                .build();

        otherTeacherUser = User.builder()
                .id("teacher-stranger-2")
                .email("teacher2@test.com")
                .role(Role.TEACHER)
                .build();

        studentUser = User.builder()
                .id("student-1")
                .email("student@test.com")
                .role(Role.STUDENT)
                .build();
    }

    @Test
    void unauthenticatedRequest_Returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/exams/exam-1/students/student-1/scores"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/teacher/exams/exam-1/questions/q-1/students/student-1/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overrideScore\":8.5}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/teacher/exams/exam-1/questions/q-1/students/student-1/override"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessTeacherExamScores_Returns403() throws Exception {
        mockMvc.perform(get("/api/teacher/exams/exam-1/students/student-1/scores"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/teacher/exams/exam-1/questions/q-1/students/student-1/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overrideScore\":8.5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedTeacher_CanRetrieveScoresForOwnExam_Returns200() throws Exception {
        ExamScoreAggregationDto mockDto = ExamScoreAggregationDto.builder()
                .examId("exam-1")
                .examTitle("Data Structures Midterm")
                .studentId("student-1")
                .studentName("Student One")
                .examScore10(BigDecimal.valueOf(8.50))
                .totalPointsEarned(BigDecimal.valueOf(85.00))
                .totalMaxPoints(BigDecimal.valueOf(100.00))
                .status("FINAL")
                .isProvisional(false)
                .totalQuestions(2)
                .submittedQuestions(2)
                .completedQuestions(2)
                .pendingQuestions(0)
                .questionScores(new ArrayList<>())
                .build();

        Mockito.when(examScoreAggregationService.calculateExamScore("exam-1", "student-1", "teacher-owner-1"))
                .thenReturn(mockDto);

        mockMvc.perform(get("/api/teacher/exams/exam-1/students/student-1/scores")
                        .with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examId").value("exam-1"))
                .andExpect(jsonPath("$.studentId").value("student-1"))
                .andExpect(jsonPath("$.examScore10").value(8.50))
                .andExpect(jsonPath("$.status").value("FINAL"));

        Mockito.verify(examScoreAggregationService).calculateExamScore("exam-1", "student-1", "teacher-owner-1");
    }

    @Test
    void teacherCannotRetrieveScoresForAnotherTeachersExam_Returns404Masking() throws Exception {
        Mockito.when(examScoreAggregationService.calculateExamScore("exam-1", "student-1", "teacher-stranger-2"))
                .thenThrow(new ResourceNotFoundException("Exam not found or access denied"));

        mockMvc.perform(get("/api/teacher/exams/exam-1/students/student-1/scores")
                        .with(user(otherTeacherUser)))
                .andExpect(status().isNotFound());

        Mockito.verify(examScoreAggregationService).calculateExamScore("exam-1", "student-1", "teacher-stranger-2");
    }

    @Test
    void authorizedTeacher_CanSetQuestionOverride_Returns200() throws Exception {
        QuestionOverrideRequestDto request = new QuestionOverrideRequestDto();
        request.setOverrideScore(BigDecimal.valueOf(9.00));
        request.setRationale("Grade appeal accepted");

        QuestionScoreDetailDto detailDto = QuestionScoreDetailDto.builder()
                .questionId("q-1")
                .maxScore(BigDecimal.valueOf(10.00))
                .autoScore(BigDecimal.valueOf(7.00))
                .overrideScore(BigDecimal.valueOf(9.00))
                .effectiveScore(BigDecimal.valueOf(9.00))
                .overrideReason("Grade appeal accepted")
                .isOverridden(true)
                .status("GRADED")
                .build();

        Mockito.when(questionScoreOverrideService.setQuestionOverride(
                eq("exam-1"), eq("q-1"), eq("student-1"), any(QuestionOverrideRequestDto.class), eq("teacher-owner-1")
        )).thenReturn(detailDto);

        mockMvc.perform(put("/api/teacher/exams/exam-1/questions/q-1/students/student-1/override")
                        .with(user(teacherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value("q-1"))
                .andExpect(jsonPath("$.overrideScore").value(9.00))
                .andExpect(jsonPath("$.isOverridden").value(true));

        Mockito.verify(questionScoreOverrideService).setQuestionOverride(
                eq("exam-1"), eq("q-1"), eq("student-1"), any(QuestionOverrideRequestDto.class), eq("teacher-owner-1")
        );
    }

    @Test
    void unauthorizedTeacher_CannotSetQuestionOverride_Returns404Masking() throws Exception {
        QuestionOverrideRequestDto request = new QuestionOverrideRequestDto();
        request.setOverrideScore(BigDecimal.valueOf(9.00));

        Mockito.when(questionScoreOverrideService.setQuestionOverride(
                eq("exam-1"), eq("q-1"), eq("student-1"), any(QuestionOverrideRequestDto.class), eq("teacher-stranger-2")
        )).thenThrow(new ResourceNotFoundException("Exam not found or access denied"));

        mockMvc.perform(put("/api/teacher/exams/exam-1/questions/q-1/students/student-1/override")
                        .with(user(otherTeacherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void authorizedTeacher_CanClearQuestionOverride_Returns200() throws Exception {
        QuestionScoreDetailDto detailDto = QuestionScoreDetailDto.builder()
                .questionId("q-1")
                .maxScore(BigDecimal.valueOf(10.00))
                .autoScore(BigDecimal.valueOf(7.00))
                .overrideScore(null)
                .effectiveScore(BigDecimal.valueOf(7.00))
                .isOverridden(false)
                .status("GRADED")
                .build();

        Mockito.when(questionScoreOverrideService.clearQuestionOverride(
                eq("exam-1"), eq("q-1"), eq("student-1"), eq("teacher-owner-1")
        )).thenReturn(detailDto);

        mockMvc.perform(delete("/api/teacher/exams/exam-1/questions/q-1/students/student-1/override")
                        .with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOverridden").value(false));

        Mockito.verify(questionScoreOverrideService).clearQuestionOverride(
                eq("exam-1"), eq("q-1"), eq("student-1"), eq("teacher-owner-1")
        );
    }
}



