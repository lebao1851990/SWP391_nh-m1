package com.codeassess.scoring.controller;

import com.codeassess.scoring.dto.request.SaveScoringPayload;
import com.codeassess.scoring.dto.response.ScoringDetail;
import com.codeassess.scoring.dto.response.StudentSubmissionResult;
import com.codeassess.scoring.service.StudentScoringService;
import com.codeassess.scoring.service.TeacherScoringService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherScoringService teacherScoringService;

    @MockBean
    private StudentScoringService studentScoringService;

    @Autowired
    private ObjectMapper objectMapper;

    private User teacherUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder().id("teacher-1").email("teacher@test.com").role(Role.TEACHER).build();
        studentUser = User.builder().id("student-1").email("student@test.com").role(Role.STUDENT).build();
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/teacher/submissions/1/scoring")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/teacher/submissions/1/scoring").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/teacher/submissions/1/scoring/audit-logs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/student/submissions/1/result")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotAccessTeacherScoring() throws Exception {
        mockMvc.perform(get("/api/teacher/submissions/1/scoring")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/teacher/submissions/1/scoring").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/teacher/submissions/1/scoring/audit-logs")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCannotAccessStudentResult() throws Exception {
        mockMvc.perform(get("/api/student/submissions/1/result")).andExpect(status().isForbidden());
    }

    @Test
    void teacherCanAccessScoring() throws Exception {
        ScoringDetail response = ScoringDetail.builder().submissionId("sub-1").totalScore(BigDecimal.TEN).build();
        Mockito.when(teacherScoringService.getScoringDetail("sub-1", "teacher-1")).thenReturn(response);

        mockMvc.perform(get("/api/teacher/submissions/sub-1/scoring").with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(10));
    }

    @Test
    void teacherCanUpdateScoring() throws Exception {
        SaveScoringPayload payload = new SaveScoringPayload();
        payload.setCriteriaScores(new ArrayList<>());
        
        ScoringDetail response = ScoringDetail.builder().submissionId("sub-1").totalScore(BigDecimal.TEN).build();
        Mockito.when(teacherScoringService.updateScoring(eq("sub-1"), eq("teacher-1"), any(SaveScoringPayload.class))).thenReturn(response);

        mockMvc.perform(put("/api/teacher/submissions/sub-1/scoring")
                .with(user(teacherUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(10));
    }

    @Test
    void studentCanAccessResult() throws Exception {
        StudentSubmissionResult response = StudentSubmissionResult.builder().submissionId("sub-1").totalScore(BigDecimal.TEN).build();
        Mockito.when(studentScoringService.getStudentResult("sub-1", "student-1")).thenReturn(response);

        mockMvc.perform(get("/api/student/submissions/sub-1/result").with(user(studentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(10));
    }

    @Test
    void teacherCanAccessAuditLogs() throws Exception {
        com.codeassess.scoring.dto.response.GradeAuditLogDto logDto = com.codeassess.scoring.dto.response.GradeAuditLogDto.builder()
                .id("log-1")
                .submissionId("sub-1")
                .actionType("OVERRIDE_SET")
                .actorName("Teacher One")
                .preEffectiveScore(BigDecimal.valueOf(8))
                .postEffectiveScore(BigDecimal.valueOf(7))
                .build();
        Mockito.when(teacherScoringService.getAuditLogs(eq("sub-1"), any(User.class))).thenReturn(java.util.List.of(logDto));

        mockMvc.perform(get("/api/teacher/submissions/sub-1/scoring/audit-logs").with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("log-1"))
                .andExpect(jsonPath("$[0].actionType").value("OVERRIDE_SET"))
                .andExpect(jsonPath("$[0].actorName").value("Teacher One"))
                .andExpect(jsonPath("$[0].preEffectiveScore").value(8))
                .andExpect(jsonPath("$[0].postEffectiveScore").value(7));
    }

    @Test
    void adminCanAccessAuditLogs() throws Exception {
        User adminUser = User.builder().id("admin-1").email("admin@test.com").role(Role.ADMIN).build();
        com.codeassess.scoring.dto.response.GradeAuditLogDto logDto = com.codeassess.scoring.dto.response.GradeAuditLogDto.builder()
                .id("log-admin-1")
                .submissionId("sub-1")
                .actionType("ARBITRATION_UPDATE")
                .actorName("System Admin")
                .preEffectiveScore(BigDecimal.valueOf(6))
                .postEffectiveScore(BigDecimal.valueOf(9))
                .build();
        Mockito.when(teacherScoringService.getAuditLogs(eq("sub-1"), any(User.class))).thenReturn(java.util.List.of(logDto));

        mockMvc.perform(get("/api/teacher/submissions/sub-1/scoring/audit-logs").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("log-admin-1"))
                .andExpect(jsonPath("$[0].actionType").value("ARBITRATION_UPDATE"))
                .andExpect(jsonPath("$[0].actorName").value("System Admin"));
    }
}
