package com.codeassess.submission.controller;

import com.codeassess.config.CorsConfig;
import com.codeassess.exam.controller.StudentExamController;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.service.StudentExamService;
import com.codeassess.scoring.controller.StudentScoringController;
import com.codeassess.scoring.service.StudentScoringService;
import com.codeassess.security.CustomUserDetailsService;
import com.codeassess.security.SecurityConfig;
import com.codeassess.submission.service.StudentSubmissionService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task M2-14 & M2-15: Workflow 2 - End-to-End Student Exam & Submission Verification Test Suite
 * Author / Assignee: Kim (kimalone2004)
 */
@WebMvcTest(controllers = {
        StudentExamController.class,
        StudentSubmissionController.class,
        StudentScoringController.class
})
@Import({SecurityConfig.class, CorsConfig.class})
@AutoConfigureMockMvc
class Workflow2StudentExamSubmissionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private StudentExamService studentExamService;

    @MockBean
    private StudentSubmissionService studentSubmissionService;

    @MockBean
    private StudentScoringService studentScoringService;

    private static final String EXAM_ID = "exam-wf2-101";

    private User studentKim;
    private User studentOther;
    private User teacherUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        studentKim = User.builder()
                .id("student-kim-2004")
                .email("kim@codeassess.edu")
                .role(Role.STUDENT)
                .fullName("Hoang Kim")
                .build();

        studentOther = User.builder()
                .id("student-other-9999")
                .email("other@codeassess.edu")
                .role(Role.STUDENT)
                .fullName("Other Student")
                .build();

        teacherUser = User.builder()
                .id("teacher-001")
                .email("teacher@codeassess.edu")
                .role(Role.TEACHER)
                .fullName("Prof. Lecturer")
                .build();

        adminUser = User.builder()
                .id("admin-001")
                .email("admin@codeassess.edu")
                .role(Role.ADMIN)
                .fullName("System Admin")
                .build();
    }

    @Test
    @DisplayName("WF2-01A: Student retrieves list of active exams for enrolled classes")
    void testWorkflow2_StudentViewsExamList() throws Exception {
        ExamResponse examSummary = ExamResponse.builder()
                .id(EXAM_ID)
                .title("CS301 Java Data Structures Practical Exam")
                .classId("class-cs301")
                .duration(90)
                .status("ACTIVE")
                .examDate(LocalDateTime.now().plusDays(1))
                .courseName("Data Structures")
                .courseCode("CS301")
                .build();

        Mockito.when(studentExamService.getStudentExams("student-kim-2004"))
                .thenReturn(List.of(examSummary));

        mockMvc.perform(get("/api/student/exams").with(user(studentKim)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(EXAM_ID))
                .andExpect(jsonPath("$[0].title").value("CS301 Java Data Structures Practical Exam"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }
}
