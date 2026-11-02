package com.codeassess.submission.controller;

import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class InternalSubmissionControllerTest {

    private static final String TEST_INTERNAL_SECRET = "test-secure-internal-secret-token-for-junit-only";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubmissionRepository submissionRepository;

    @MockBean
    private DockerSandboxService dockerSandboxService;

    @MockBean
    private AstPlagiarismService astPlagiarismService;

    @MockBean
    private SimilarityMatrixService similarityMatrixService;

    private Submission testSubmission;
    private Exam testExam;

    @BeforeEach
    void setUp() {
        testExam = Exam.builder().id("exam-123").title("Test Exam").build();
        testSubmission = Submission.builder()
                .id("sub-456")
                .exam(testExam)
                .student(User.builder().id("student-1").build())
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .sourceCode("public class Main { public static void main(String[] args) {} }")
                .build();
    }

    @Test
    @DisplayName("Internal API: Missing X-Internal-Token header returns 401 Unauthorized")
    void testMissingInternalToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized: invalid or missing internal token"));

        verify(submissionRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("Internal API: Legacy default token is rejected with 401 Unauthorized")
    void testLegacyDefaultToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading")
                        .header("X-Internal-Token", "codeassess-internal-secret-2026"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized: invalid or missing internal token"));

        verify(submissionRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("Internal API: Invalid X-Internal-Token header returns 401 Unauthorized")
    void testInvalidInternalToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading")
                        .header("X-Internal-Token", "wrong-secret-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized: invalid or missing internal token"));

        verify(submissionRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("Internal API: Valid token processes single-file grading successfully")
    void testValidToken_ProcessesGradingSuccessfully() throws Exception {
        when(submissionRepository.claimSubmissionWithToken(eq("sub-456"), anyString(), any(java.time.LocalDateTime.class))).thenReturn(1);
        when(submissionRepository.completeGradingWithToken(eq("sub-456"), anyString(), anyString(), anyInt(), any(), any(), any(), anyLong(), any(java.time.LocalDateTime.class))).thenReturn(1);
        when(submissionRepository.findById("sub-456")).thenReturn(Optional.of(testSubmission));

        when(dockerSandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.SUCCESS)
                .exitCode(0)
                .stdout("Output from sandbox")
                .executionTimeMs(120L)
                .build());

        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading")
                        .header("X-Internal-Token", TEST_INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value("sub-456"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.executionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.exitCode").value(0));

        verify(dockerSandboxService).execute(anyString());
        verify(astPlagiarismService).generateFingerprintsForSubmission("sub-456");
        verify(similarityMatrixService).requestRefresh("exam-123");
        verify(similarityMatrixService).generateMatrixForExam("exam-123");
    }

    @Test
    @DisplayName("Internal API: Concurrency guard returns 200 PROCESSING without executing Docker if already claimed")
    void testConcurrentWorker_AlreadyClaimed_ReturnsProcessingWithoutDockerExecution() throws Exception {
        testSubmission.setStatus("PROCESSING");
        when(submissionRepository.claimSubmissionWithToken(eq("sub-456"), anyString(), any(java.time.LocalDateTime.class))).thenReturn(0);
        when(submissionRepository.findById("sub-456")).thenReturn(Optional.of(testSubmission));

        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading")
                        .header("X-Internal-Token", TEST_INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value("sub-456"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.message").value("Grading in progress by concurrent worker"));

        verify(dockerSandboxService, never()).execute(anyString());
        verify(astPlagiarismService, never()).generateFingerprintsForSubmission(anyString());
    }

    @Test
    @DisplayName("Internal API: Zombie worker is fenced out if claim expired/superseded during Docker execution")
    void testZombieWorker_LeaseExpiredDuringDocker_ResultsDiscarded() throws Exception {
        when(submissionRepository.claimSubmissionWithToken(eq("sub-456"), anyString(), any(java.time.LocalDateTime.class))).thenReturn(1);
        when(submissionRepository.completeGradingWithToken(eq("sub-456"), anyString(), anyString(), anyInt(), any(), any(), any(), anyLong(), any(java.time.LocalDateTime.class))).thenReturn(0);
        when(submissionRepository.findById("sub-456")).thenReturn(Optional.of(testSubmission));

        when(dockerSandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.SUCCESS)
                .exitCode(0)
                .stdout("Output from late sandbox")
                .executionTimeMs(65000L)
                .build());

        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading")
                        .header("X-Internal-Token", TEST_INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value("sub-456"))
                .andExpect(jsonPath("$.status").value("OBSOLETE_CLAIM"))
                .andExpect(jsonPath("$.message").value("Claim expired or superseded by another worker"));

        verify(dockerSandboxService).execute(anyString());
        verify(astPlagiarismService, never()).generateFingerprintsForSubmission(anyString());
        verify(similarityMatrixService, never()).generateMatrixForExam(anyString());
    }

    @Test
    @DisplayName("Internal API: Idempotency returns 200 immediately if submission already COMPLETED")
    void testIdempotency_AlreadyCompleted_ReturnsOkWithoutReExecution() throws Exception {
        testSubmission.setStatus("COMPLETED");
        testSubmission.setExecutionStatus("SUCCESS");
        testSubmission.setExitCode(0);

        when(submissionRepository.findById("sub-456")).thenReturn(Optional.of(testSubmission));

        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading")
                        .header("X-Internal-Token", TEST_INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value("sub-456"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Already processed"));

        verify(dockerSandboxService, never()).execute(anyString());
        verify(astPlagiarismService, never()).generateFingerprintsForSubmission(anyString());
    }

    @Test
    @DisplayName("FIX-01: Docker unavailable releases claim and returns retryable HTTP 503 without zeroing score or override")
    void testDockerUnavailable_ReleasesClaimAndReturns503Retryable() throws Exception {
        testSubmission.setTotalScore(new BigDecimal("8.50"));
        testSubmission.setOverrideScore(new BigDecimal("9.00"));
        when(submissionRepository.findById("sub-456")).thenReturn(Optional.of(testSubmission));
        when(submissionRepository.claimSubmissionWithToken(eq("sub-456"), anyString(), any(java.time.LocalDateTime.class))).thenReturn(1);
        when(dockerSandboxService.execute(anyString())).thenThrow(new SandboxException("Docker engine error: daemon unavailable"));

        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading")
                        .header("X-Internal-Token", TEST_INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Docker engine error: daemon unavailable"));

        verify(submissionRepository).releaseExpiredClaim(eq("sub-456"), anyString());
        verify(submissionRepository, never()).save(any());
        assertThat(testSubmission.getStatus()).isEqualTo("PENDING");
        assertThat(testSubmission.getTotalScore()).isEqualTo(new BigDecimal("8.50"));
        assertThat(testSubmission.getOverrideScore()).isEqualTo(new BigDecimal("9.00"));
    }

    @Test
    @DisplayName("FIX-01: Sandbox capacity exceeded releases claim and returns retryable HTTP 503")
    void testSandboxCapacityExceeded_ReleasesClaimAndReturns503Retryable() throws Exception {
        when(submissionRepository.findById("sub-456")).thenReturn(Optional.of(testSubmission));
        when(submissionRepository.claimSubmissionWithToken(eq("sub-456"), anyString(), any(java.time.LocalDateTime.class))).thenReturn(1);
        when(dockerSandboxService.execute(anyString())).thenThrow(new SandboxException("Sandbox is at capacity. Try again shortly."));

        mockMvc.perform(post("/api/internal/submissions/sub-456/process-grading")
                        .header("X-Internal-Token", TEST_INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Sandbox is at capacity. Try again shortly."));

        verify(submissionRepository).releaseExpiredClaim(eq("sub-456"), anyString());
        verify(submissionRepository, never()).save(any());
    }
}
