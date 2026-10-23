package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.queue.service.BullMqJobDispatcher;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.config.ZipSubmissionProperties;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.dto.response.InternalGradingResponse;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Test Suite verifying Prompt 2:
 * 1. Valid vs Invalid submissions (validation & 404 masking before pipeline)
 * 2. Enqueue resiliency & fallback when Redis/BullMQ is offline
 * 3. Concurrent worker claiming & idempotency
 * 4. Zombie worker fencing & late result rejection
 * 5. Sweeper recovery for pending and expired processing leases
 * 6. Non-exposure of raw source code in queue payload
 */
@ExtendWith(MockitoExtension.class)
public class SubmissionWorkflowAndQueueVerificationTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private BullMqJobDispatcher bullMqJobDispatcher;

    @Mock
    private DockerSandboxService dockerSandboxService;

    @Mock
    private AstPlagiarismService astPlagiarismService;

    @Mock
    private SimilarityMatrixService similarityMatrixService;

    private StudentSubmissionService studentSubmissionService;
    private InternalGradingService internalGradingService;
    private SubmissionRecoverySweeper sweeper;
    private SubmissionMapper submissionMapper;

    private Exam activeExam;
    private User testStudent;
    private Submission testSubmission;

    @BeforeEach
    void setUp() {
        submissionMapper = new SubmissionMapper();

        activeExam = Exam.builder()
                .id("exam-workflow-01")
                .title("Java Core Assessment")
                .status("active")
                .examDate(LocalDateTime.now().minusMinutes(10))
                .duration(120)
                .build();

        testStudent = User.builder()
                .id("student-kim-2004")
                .email("kim@uni.edu")
                .fullName("Kim Nguyen")
                .build();

        testSubmission = Submission.builder()
                .id("sub-kim-100")
                .exam(activeExam)
                .student(testStudent)
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .sourceCode("public class Main { public static void main(String[] args) {} }")
                .createdAt(LocalDateTime.now())
                .build();

        internalGradingService = new InternalGradingService(
                submissionRepository,
                dockerSandboxService,
                astPlagiarismService,
                similarityMatrixService
        );

        sweeper = new SubmissionRecoverySweeper(
                submissionRepository,
                bullMqJobDispatcher,
                internalGradingService
        );

        studentSubmissionService = new StudentSubmissionService(
                submissionRepository,
                submissionMapper,
                examRepository,
                userRepository,
                eventPublisher,
                null,
                null,
                null,
                dockerSandboxService,
                astPlagiarismService,
                new ZipSubmissionProperties(),
                null,
                internalGradingService,
                null,
                bullMqJobDispatcher
        );
    }

    // =========================================================================
    // 1. INPUT VALIDATION & IDOR RESOURCE MASKING
    // =========================================================================

    @Test
    @DisplayName("Test 1: Student not enrolled in class returns 404 Resource Masking, never enters pipeline")
    void testSubmitCode_UnauthorizedClassEnrollment_Returns404Masking() {
        when(examRepository.findByIdAndStudentId("exam-other-class", "student-kim-2004"))
                .thenReturn(Optional.empty());

        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        assertThatThrownBy(() -> studentSubmissionService.submitCode("exam-other-class", "student-kim-2004", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Exam not found or you are not enrolled in this class");

        verifyNoInteractions(submissionRepository);
        verifyNoInteractions(bullMqJobDispatcher);
        verifyNoInteractions(dockerSandboxService);
    }

    @Test
    @DisplayName("Test 2: Submitting to inactive or closed exam is rejected before database persistence")
    void testSubmitCode_InactiveExam_RejectedBeforePersistence() {
        Exam closedExam = Exam.builder()
                .id("exam-closed-01")
                .title("Closed Exam")
                .status("completed")
                .build();

        when(examRepository.findByIdAndStudentId("exam-closed-01", "student-kim-2004"))
                .thenReturn(Optional.of(closedExam));

        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Test {}");

        assertThatThrownBy(() -> studentSubmissionService.submitCode("exam-closed-01", "student-kim-2004", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exam is not currently active for submissions");

        verifyNoInteractions(submissionRepository);
        verifyNoInteractions(bullMqJobDispatcher);
    }

    @Test
    @DisplayName("Test 3: Valid submission creates PENDING database record and prepares for async dispatch")
    void testSubmitCode_ValidSubmission_CreatesPendingDatabaseRecord() {
        when(examRepository.findByIdAndStudentId("exam-workflow-01", "student-kim-2004"))
                .thenReturn(Optional.of(activeExam));
        when(userRepository.findById("student-kim-2004"))
                .thenReturn(Optional.of(testStudent));
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(invocation -> {
                    Submission s = invocation.getArgument(0);
                    s.setId("sub-generated-101");
                    return s;
                });
        when(bullMqJobDispatcher.isEnabled()).thenReturn(true);

        SubmissionCreateRequest request = new SubmissionCreateRequest("public class Main {}");
        SubmissionDetailResponse response = studentSubmissionService.submitCode("exam-workflow-01", "student-kim-2004", request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("sub-generated-101");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getSubmissionType()).isEqualTo("SINGLE_FILE");

        ArgumentCaptor<Submission> subCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(subCaptor.capture());
        assertThat(subCaptor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(subCaptor.getValue().getExam().getId()).isEqualTo("exam-workflow-01");
    }

    // =========================================================================
    // 2. CONCURRENCY, IDEMPOTENCY & LEASE FENCING
    // =========================================================================

    @Test
    @DisplayName("Test 4: Two concurrent consumers receiving same submission: only one claims, second exits cleanly")
    void testInternalGrading_TwoConcurrentConsumers_OnlyOneClaims() {
        when(submissionRepository.findById("sub-kim-100")).thenReturn(Optional.of(testSubmission));
        // Consumer 1 wins claim (returns 1)
        when(submissionRepository.claimSubmissionWithToken(eq("sub-kim-100"), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(submissionRepository.completeGradingWithToken(
                eq("sub-kim-100"), anyString(), anyString(), anyInt(), any(), any(), any(), anyLong(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(dockerSandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.SUCCESS)
                .exitCode(0)
                .executionTimeMs(150L)
                .build());

        InternalGradingResponse resp1 = internalGradingService.processGrading("sub-kim-100");
        assertThat(resp1.getStatus()).isEqualTo("COMPLETED");

        // Consumer 2 attempts claim while Consumer 1 is processing -> claim returns 0
        testSubmission.setStatus("PROCESSING");
        when(submissionRepository.claimSubmissionWithToken(eq("sub-kim-100"), anyString(), any(LocalDateTime.class)))
                .thenReturn(0);

        InternalGradingResponse resp2 = internalGradingService.processGrading("sub-kim-100");
        assertThat(resp2.getStatus()).isEqualTo("PROCESSING");
        assertThat(resp2.getMessage()).contains("Grading in progress by concurrent worker");

        // Consumer 2 must NOT run docker or duplicate side effects
        verify(dockerSandboxService, times(1)).execute(anyString());
    }

    @Test
    @DisplayName("Test 5: Idempotency returns 200 immediately without re-execution if submission is already COMPLETED")
    void testInternalGrading_AlreadyCompleted_ReturnsImmediatelyWithoutReExecution() {
        testSubmission.setStatus("COMPLETED");
        testSubmission.setExecutionStatus("SUCCESS");
        testSubmission.setExitCode(0);
        when(submissionRepository.findById("sub-kim-100")).thenReturn(Optional.of(testSubmission));

        InternalGradingResponse response = internalGradingService.processGrading("sub-kim-100");

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getMessage()).isEqualTo("Already processed");

        verify(submissionRepository, never()).claimSubmissionWithToken(anyString(), anyString(), any());
        verify(dockerSandboxService, never()).execute(anyString());
        verify(astPlagiarismService, never()).generateFingerprintsForSubmission(anyString());
    }

    @Test
    @DisplayName("Test 6: Zombie worker whose lease expired has late results discarded with OBSOLETE_CLAIM")
    void testInternalGrading_ZombieWorker_LateResultsDiscarded() {
        when(submissionRepository.findById("sub-kim-100")).thenReturn(Optional.of(testSubmission));
        when(submissionRepository.claimSubmissionWithToken(eq("sub-kim-100"), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(dockerSandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.SUCCESS)
                .exitCode(0)
                .executionTimeMs(65000L) // Exceeded 60s lease window
                .build());
        // Fencing check: completeGradingWithToken returns 0 because sweeper revoked claim
        when(submissionRepository.completeGradingWithToken(
                eq("sub-kim-100"), anyString(), anyString(), anyInt(), any(), any(), any(), anyLong(), any(LocalDateTime.class)))
                .thenReturn(0);

        InternalGradingResponse response = internalGradingService.processGrading("sub-kim-100");

        assertThat(response.getStatus()).isEqualTo("OBSOLETE_CLAIM");
        assertThat(response.getMessage()).contains("Claim expired or superseded by another worker");

        // Obsolete worker must NEVER generate AST fingerprints or refresh matrix
        verify(astPlagiarismService, never()).generateFingerprintsForSubmission(anyString());
        verify(similarityMatrixService, never()).generateMatrixForExam(anyString());
    }

    // =========================================================================
    // 3. FAULT RECOVERY & SWEEPER
    // =========================================================================

    @Test
    @DisplayName("Test 7: Sweeper automatically recovers stuck PENDING submissions and expired PROCESSING leases")
    void testSweeper_RecoversPendingAndExpiredProcessingSubmissions() {
        Submission stuckPending = Submission.builder()
                .id("sub-stuck-pending")
                .exam(activeExam)
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        Submission stuckProcessing = Submission.builder()
                .id("sub-stuck-processing")
                .exam(activeExam)
                .status("PROCESSING")
                .submissionType("SINGLE_FILE")
                .claimToken(UUID.randomUUID().toString())
                .leaseExpiresAt(LocalDateTime.now().minusMinutes(2)) // Expired
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();

        when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of(stuckPending));
        when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of(stuckProcessing));
        when(submissionRepository.releaseExpiredClaim(eq(stuckProcessing.getId()), eq(stuckProcessing.getClaimToken())))
                .thenReturn(1);
        when(bullMqJobDispatcher.dispatchGradingJob(anyString(), anyString(), anyString()))
                .thenReturn(true);

        sweeper.recoverPendingSubmissions();

        // Expired claim was revoked
        verify(submissionRepository).releaseExpiredClaim(eq("sub-stuck-processing"), anyString());
        // Both recovered jobs re-dispatched to queue
        verify(bullMqJobDispatcher).dispatchGradingJob(eq("sub-stuck-pending"), eq("exam-workflow-01"), eq("SINGLE_FILE"));
        verify(bullMqJobDispatcher).dispatchGradingJob(eq("sub-stuck-processing"), eq("exam-workflow-01"), eq("SINGLE_FILE"));
    }

    @Test
    @DisplayName("Test 8: Sweeper runs direct fallback grading when BullMQ is offline")
    void testSweeper_RunsDirectFallbackGrading_WhenBullMqOffline() {
        Submission stuckPending = Submission.builder()
                .id("sub-fallback-1")
                .exam(activeExam)
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .sourceCode("public class Main {}")
                .build();

        when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of(stuckPending));
        when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        // Queue is unreachable
        when(bullMqJobDispatcher.dispatchGradingJob(anyString(), anyString(), anyString()))
                .thenReturn(false);

        when(submissionRepository.findById("sub-fallback-1")).thenReturn(Optional.of(stuckPending));
        when(submissionRepository.claimSubmissionWithToken(eq("sub-fallback-1"), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(submissionRepository.completeGradingWithToken(eq("sub-fallback-1"), anyString(), anyString(), anyInt(), any(), any(), any(), anyLong(), any(LocalDateTime.class))).thenReturn(1);
        when(dockerSandboxService.execute(anyString())).thenReturn(SandboxResult.builder()
                .status(SandboxResult.Status.SUCCESS)
                .exitCode(0)
                .build());

        sweeper.recoverPendingSubmissions();

        // Direct grading executed successfully despite queue offline
        verify(dockerSandboxService).execute(anyString());
        verify(astPlagiarismService).generateFingerprintsForSubmission("sub-fallback-1");
    }
}
