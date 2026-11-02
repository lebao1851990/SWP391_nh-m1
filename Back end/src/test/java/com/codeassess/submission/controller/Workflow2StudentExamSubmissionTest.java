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

    @Test
    @DisplayName("WF2-01B: Student enters exam workspace and views exam details")
    void testWorkflow2_StudentViewsExamDetail() throws Exception {
        ExamResponse examDetail = ExamResponse.builder()
                .id(EXAM_ID)
                .title("CS301 Java Data Structures Practical Exam")
                .classId("class-cs301")
                .duration(90)
                .status("ACTIVE")
                .courseName("Data Structures")
                .courseCode("CS301")
                .build();

        Mockito.when(studentExamService.getStudentExamById(EXAM_ID, "student-kim-2004"))
                .thenReturn(examDetail);

        mockMvc.perform(get("/api/student/exams/" + EXAM_ID).with(user(studentKim)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EXAM_ID))
                .andExpect(jsonPath("$.duration").value(90))
                .andExpect(jsonPath("$.courseCode").value("CS301"));
    }

    @Test
    @DisplayName("WF2-01C: Student submits valid Java solution and receives 201 Created")
    void testWorkflow2_StudentSubmitsSolution() throws Exception {
        String validSourceCode = "public class Solution { public static void main(String[] args) {} }";
        com.codeassess.submission.dto.request.SubmissionCreateRequest submitRequest =
                new com.codeassess.submission.dto.request.SubmissionCreateRequest(validSourceCode);

        com.codeassess.submission.dto.response.SubmissionDetailResponse createdSubmission =
                com.codeassess.submission.dto.response.SubmissionDetailResponse.builder()
                        .id("sub-wf2-501")
                        .examId(EXAM_ID)
                        .studentId("student-kim-2004")
                        .status("PENDING")
                        .sourceCode(validSourceCode)
                        .createdAt(LocalDateTime.now())
                        .build();

        Mockito.when(studentSubmissionService.submitCode(
                org.mockito.ArgumentMatchers.eq(EXAM_ID),
                org.mockito.ArgumentMatchers.eq("student-kim-2004"),
                org.mockito.ArgumentMatchers.any(com.codeassess.submission.dto.request.SubmissionCreateRequest.class)))
                .thenReturn(createdSubmission);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentKim))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("sub-wf2-501"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("WF2-01D: Student queries their submission history")
    void testWorkflow2_StudentViewsSubmissionHistory() throws Exception {
        com.codeassess.submission.dto.response.SubmissionListResponse listEntry =
                com.codeassess.submission.dto.response.SubmissionListResponse.builder()
                        .id("sub-wf2-501")
                        .examId(EXAM_ID)
                        .examTitle("CS301 Java Data Structures Practical Exam")
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();

        Mockito.when(studentSubmissionService.getStudentSubmissions("student-kim-2004"))
                .thenReturn(List.of(listEntry));

        mockMvc.perform(get("/api/student/submissions").with(user(studentKim)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sub-wf2-501"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("WF2-01E: Student inspects graded evaluation result with rubric score")
    void testWorkflow2_StudentInspectsGradedResultWithRubric() throws Exception {
        com.codeassess.scoring.dto.response.StudentSubmissionResult gradedResult =
                com.codeassess.scoring.dto.response.StudentSubmissionResult.builder()
                        .submissionId("sub-wf2-501")
                        .examName("CS301 Java Data Structures Practical Exam")
                        .status("GRADED")
                        .totalScore(new java.math.BigDecimal("95.00"))
                        .effectiveScore(new java.math.BigDecimal("95.00"))
                        .maxTotalScore(new java.math.BigDecimal("100.00"))
                        .criteria(List.of(
                                com.codeassess.scoring.dto.response.CriterionScoreDto.builder()
                                        .criterionId("crit-1")
                                        .name("Correctness")
                                        .description("Outputs expected answer")
                                        .score(new java.math.BigDecimal("70.00"))
                                        .maxScore(new java.math.BigDecimal("70.00"))
                                        .build()
                        ))
                        .evaluatedAt(LocalDateTime.now())
                        .build();

        Mockito.when(studentScoringService.getStudentResult("sub-wf2-501", "student-kim-2004"))
                .thenReturn(gradedResult);

        mockMvc.perform(get("/api/student/submissions/sub-wf2-501/result").with(user(studentKim)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value("sub-wf2-501"))
                .andExpect(jsonPath("$.status").value("GRADED"))
                .andExpect(jsonPath("$.effectiveScore").value(95.00));
    }

    // =========================================================================
    // 2. EXCEPTION PATH 1: INPUT VALIDATION FAILURES
    // =========================================================================

    @Test
    @DisplayName("WF2-02: Exception Path 1 - Empty source code is rejected with 400 Bad Request")
    void testWorkflow2_ExceptionPath1_EmptySourceCode_Returns400BadRequest() throws Exception {
        com.codeassess.submission.dto.request.SubmissionCreateRequest emptyRequest =
                new com.codeassess.submission.dto.request.SubmissionCreateRequest("");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentKim))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("WF2-03: Exception Path 1 - Blank whitespace source code is rejected with 400 Bad Request")
    void testWorkflow2_ExceptionPath1_BlankWhitespaceSourceCode_Returns400BadRequest() throws Exception {
        com.codeassess.submission.dto.request.SubmissionCreateRequest blankRequest =
                new com.codeassess.submission.dto.request.SubmissionCreateRequest("   \n\t  ");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentKim))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankRequest)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. EXCEPTION PATH 2A: RESOURCE OWNERSHIP & CLASS ENROLLMENT MASKING
    // =========================================================================

    @Test
    @DisplayName("WF2-04: Exception Path 2A - Student not enrolled in class receives 404 Masking on exam detail")
    void testWorkflow2_ExceptionPath2_StudentNotEnrolledInClass_ExamAccessReturns404NotFound() throws Exception {
        Mockito.when(studentExamService.getStudentExamById(EXAM_ID, "student-kim-2004"))
                .thenThrow(new com.codeassess.common.exception.ResourceNotFoundException("Exam not found or access denied"));

        mockMvc.perform(get("/api/student/exams/" + EXAM_ID).with(user(studentKim)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("WF2-05: Exception Path 2A - Student not enrolled in class receives 404 Masking on submission attempt")
    void testWorkflow2_ExceptionPath2_StudentNotEnrolledInClass_SubmissionReturns404NotFound() throws Exception {
        Mockito.when(studentSubmissionService.submitCode(
                org.mockito.ArgumentMatchers.eq(EXAM_ID),
                org.mockito.ArgumentMatchers.eq("student-kim-2004"),
                org.mockito.ArgumentMatchers.any(com.codeassess.submission.dto.request.SubmissionCreateRequest.class)))
                .thenThrow(new com.codeassess.common.exception.ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        com.codeassess.submission.dto.request.SubmissionCreateRequest request =
                new com.codeassess.submission.dto.request.SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentKim))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // 4. EXCEPTION PATH 2B: PEER PRIVACY & IDOR DEFENSE
    // =========================================================================

    @Test
    @DisplayName("WF2-06: Exception Path 2B - Student cannot access submission belonging to another student (IDOR Masking 404)")
    void testWorkflow2_ExceptionPath2_StudentCannotAccessOtherStudentSubmission_Returns404NotFound() throws Exception {
        Mockito.when(studentSubmissionService.getStudentSubmissionById("sub-wf2-999", "student-kim-2004"))
                .thenThrow(new com.codeassess.common.exception.ResourceNotFoundException("Submission not found"));

        mockMvc.perform(get("/api/student/submissions/sub-wf2-999").with(user(studentKim)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("WF2-07: Exception Path 2B - Student cannot access graded result of another student (IDOR Masking 404)")
    void testWorkflow2_ExceptionPath2_StudentCannotAccessOtherStudentSubmissionResult_Returns404NotFound() throws Exception {
        Mockito.when(studentScoringService.getStudentResult("sub-wf2-999", "student-kim-2004"))
                .thenThrow(new com.codeassess.common.exception.ResourceNotFoundException("Submission result not found"));

        mockMvc.perform(get("/api/student/submissions/sub-wf2-999/result").with(user(studentKim)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // 5. EXCEPTION PATH 3: EXAM LIFECYCLE BOUNDARIES
    // =========================================================================

    @Test
    @DisplayName("WF2-08: Exception Path 3 - Submission to closed or inactive exam is rejected with 400 Bad Request")
    void testWorkflow2_ExceptionPath3_SubmitToClosedOrInactiveExam_Returns400BadRequest() throws Exception {
        Mockito.when(studentSubmissionService.submitCode(
                org.mockito.ArgumentMatchers.eq(EXAM_ID),
                org.mockito.ArgumentMatchers.eq("student-kim-2004"),
                org.mockito.ArgumentMatchers.any(com.codeassess.submission.dto.request.SubmissionCreateRequest.class)))
                .thenThrow(new IllegalStateException("Exam is not currently active for submissions"));

        com.codeassess.submission.dto.request.SubmissionCreateRequest request =
                new com.codeassess.submission.dto.request.SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentKim))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("WF2-09: Exception Path 3 - Submission to non-existent exam ID returns 404 Not Found")
    void testWorkflow2_ExceptionPath3_SubmitToNonExistentExam_Returns404NotFound() throws Exception {
        Mockito.when(studentSubmissionService.submitCode(
                org.mockito.ArgumentMatchers.eq("non-existent-exam"),
                org.mockito.ArgumentMatchers.eq("student-kim-2004"),
                org.mockito.ArgumentMatchers.any(com.codeassess.submission.dto.request.SubmissionCreateRequest.class)))
                .thenThrow(new com.codeassess.common.exception.ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        com.codeassess.submission.dto.request.SubmissionCreateRequest request =
                new com.codeassess.submission.dto.request.SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/non-existent-exam/submissions")
                .with(user(studentKim))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // 6. SECURITY RULES & CROSS-ROLE AUTHORIZATION (RBAC)
    // =========================================================================

    @Test
    @DisplayName("WF2-10: Security - Unauthenticated student request returns 401 Unauthorized")
    void testWorkflow2_Security_AnonymousUser_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/student/exams"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/student/exams/" + EXAM_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/student/submissions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"sourceCode\":\"class A{}\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WF2-11: Security - Teacher role is forbidden (403) from accessing student submission endpoint")
    void testWorkflow2_CrossRole_TeacherCannotAccessStudentSubmissionApi_Returns403() throws Exception {
        com.codeassess.submission.dto.request.SubmissionCreateRequest request =
                new com.codeassess.submission.dto.request.SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(teacherUser))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WF2-12: Security - Admin role is forbidden (403) from accessing student submission endpoint")
    void testWorkflow2_CrossRole_AdminCannotAccessStudentSubmissionApi_Returns403() throws Exception {
        com.codeassess.submission.dto.request.SubmissionCreateRequest request =
                new com.codeassess.submission.dto.request.SubmissionCreateRequest("public class Test {}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(adminUser))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WF2-13: Security - Injected studentId in request body is ignored and bound to authenticated principal")
    void testWorkflow2_Security_InjectedStudentIdInBodyIsIgnored_ServerBindsToAuthenticatedUser() throws Exception {
        com.codeassess.submission.dto.response.SubmissionDetailResponse response =
                com.codeassess.submission.dto.response.SubmissionDetailResponse.builder()
                        .id("sub-wf2-501")
                        .examId(EXAM_ID)
                        .studentId("student-kim-2004")
                        .status("PENDING")
                        .sourceCode("public class Test {}")
                        .build();

        Mockito.when(studentSubmissionService.submitCode(
                org.mockito.ArgumentMatchers.eq(EXAM_ID),
                org.mockito.ArgumentMatchers.eq("student-kim-2004"),
                org.mockito.ArgumentMatchers.any(com.codeassess.submission.dto.request.SubmissionCreateRequest.class)))
                .thenReturn(response);

        // Attempting to spoof identity by adding injected studentId in payload
        String spoofedPayload = "{\"sourceCode\":\"public class Test {}\",\"studentId\":\"malicious-attacker-id\"}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/student/exams/" + EXAM_ID + "/submissions")
                .with(user(studentKim))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(spoofedPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value("student-kim-2004"));
    }
}
