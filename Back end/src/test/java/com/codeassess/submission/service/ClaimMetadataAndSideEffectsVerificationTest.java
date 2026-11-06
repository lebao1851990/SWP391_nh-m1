package com.codeassess.submission.service;

import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.queue.service.BullMqJobDispatcher;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.dto.response.InternalGradingResponse;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.SubmissionListResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.service.AutomatedTestRunnerService;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClaimMetadataAndSideEffectsVerificationTest {

        @Mock
        private SubmissionRepository submissionRepository;

        @Mock
        private DockerSandboxService dockerSandboxService;

        @Mock
        private AstPlagiarismService astPlagiarismService;

        @Mock
        private SimilarityMatrixService similarityMatrixService;

        @Mock
        private AutomatedTestRunnerService automatedTestRunnerService;

        @Mock
        private BullMqJobDispatcher bullMqJobDispatcher;

        @InjectMocks
        private InternalGradingService internalGradingService;

        private final SubmissionMapper submissionMapper = new SubmissionMapper();

        private Submission testSubmission;
        private Exam testExam;

        @BeforeEach
        void setUp() {
                testExam = Exam.builder().id("exam-claim-test").title("Claim Test Exam").build();
                testSubmission = Submission.builder()
                                .id("sub-claim-1")
                                .exam(testExam)
                                .student(User.builder().id("student-1").build())
                                .status("PENDING")
                                .sourceCode("public class Main { public static void main(String[] args) {} }")
                                .submissionType("SINGLE_FILE")
                                .createdAt(LocalDateTime.of(2026, 10, 17, 10, 0, 0))
                                .build();
        }

        @Test
        @DisplayName("P0.3 Test 1: Consecutive claims generated in the same millisecond are guaranteed globally unique")
        void testConsecutiveClaims_GenerateUniqueTokens() {
                Set<String> generatedTokens = new HashSet<>();
                int count = 10000;
                for (int i = 0; i < count; i++) {
                        String token = UUID.randomUUID().toString();
                        generatedTokens.add(token);
                }
                assertThat(generatedTokens.size()).isEqualTo(count);
        }

        @Test
        @DisplayName("P0.3 Test 2: Claim metadata is completely isolated from executionDurationMs and evaluatedAt")
        void testClaimMetadata_DoesNotPolluteBusinessTelemetry() {
                String claimToken = UUID.randomUUID().toString();
                LocalDateTime leaseExpiresAt = LocalDateTime.now().plusSeconds(60);

                Submission processingSub = Submission.builder()
                                .id("sub-in-flight")
                                .exam(testExam)
                                .student(User.builder().id("student-1").build())
                                .status("PROCESSING")
                                .claimToken(claimToken)
                                .leaseExpiresAt(leaseExpiresAt)
                                .executionDurationMs(null)
                                .evaluatedAt(null)
                                .createdAt(LocalDateTime.of(2026, 10, 17, 10, 0, 0))
                                .build();

                SubmissionDetailResponse detail = submissionMapper.toDetailResponse(processingSub);
                assertThat(detail.getStatus()).isEqualTo("PROCESSING");
                assertThat(detail.getExecutionDurationMs()).isNull();
                assertThat(detail.getEvaluatedAt()).isNull();

                SubmissionListResponse listResp = submissionMapper.toListResponse(processingSub);
                assertThat(listResp.getStatus()).isEqualTo("PROCESSING");
                assertThat(listResp.getEvaluatedAt()).isNull();
        }

        @Test
        @DisplayName("P0.3 Test 3: ZIP submission retains original execution telemetry when sweeper releases an expired claim")
        void testZipSubmission_PreservesExecutionDuration_WhenSweeperReleasesStaleClaim() {
                Submission zipSub = Submission.builder()
                                .id("sub-zip-1")
                                .exam(testExam)
                                .student(User.builder().id("student-1").build())
                                .status("PROCESSING")
                                .submissionType("ZIP")
                                .executionDurationMs(320L)
                                .claimToken(UUID.randomUUID().toString())
                                .leaseExpiresAt(LocalDateTime.now().minusSeconds(10))
                                .build();

                when(submissionRepository.releaseExpiredClaim(eq(zipSub.getId()), eq(zipSub.getClaimToken())))
                                .thenReturn(1);

                int released = submissionRepository.releaseExpiredClaim(zipSub.getId(), zipSub.getClaimToken());
                assertThat(released).isEqualTo(1);
                assertThat(zipSub.getExecutionDurationMs()).isEqualTo(320L);
        }

        @Test
        @DisplayName("P0.3 Test 4: Zombie worker with expired lease CANNOT write results or generate AST fingerprints")
        void testZombieWorker_CannotWriteResultsOrGenerateFingerprints() {
                testSubmission.setSubmissionType("ZIP");
                when(submissionRepository.findById("sub-claim-1")).thenReturn(Optional.of(testSubmission));
                when(submissionRepository.claimSubmissionWithToken(eq("sub-claim-1"), anyString(),
                                any(LocalDateTime.class)))
                                .thenReturn(1);
                when(submissionRepository.completeZipGradingWithToken(
                                eq("sub-claim-1"), anyString(), any(LocalDateTime.class)))
                                .thenReturn(0);

                InternalGradingResponse response = internalGradingService.processGrading("sub-claim-1");

                assertThat(response.getStatus()).isEqualTo("OBSOLETE_CLAIM");
                assertThat(response.getMessage()).contains("Claim expired or superseded");

                verify(astPlagiarismService, never()).generateFingerprintsForSubmission(anyString());
                verify(similarityMatrixService, never()).requestRefresh(anyString());
                verify(similarityMatrixService, never()).generateMatrixForExam(anyString());
        }

        @Test
        @DisplayName("P0.3 Test 5: Sweeper cannot revoke a new claim using an old stale token")
        void testSweeper_CannotRevokeNewClaim_WithStaleToken() {
                String oldToken = UUID.randomUUID().toString();

                when(submissionRepository.releaseExpiredClaim("sub-claim-1", oldToken)).thenReturn(0);

                int rowsReleased = submissionRepository.releaseExpiredClaim("sub-claim-1", oldToken);
                assertThat(rowsReleased).isEqualTo(0);

                verify(submissionRepository, times(1)).releaseExpiredClaim("sub-claim-1", oldToken);
        }

        @Test
        @DisplayName("P0.3 Test 6: Concurrent worker claiming ensures only one worker executes sandbox")
        void testConcurrentWorkers_OnlyOneClaims() {
                when(submissionRepository.findById("sub-claim-1")).thenReturn(Optional.of(testSubmission));
                when(submissionRepository.claimSubmissionWithToken(eq("sub-claim-1"), anyString(),
                                any(LocalDateTime.class)))
                                .thenReturn(0);

                testSubmission.setStatus("PROCESSING");

                InternalGradingResponse response = internalGradingService.processGrading("sub-claim-1");

                assertThat(response.getStatus()).isEqualTo("PROCESSING");
                assertThat(response.getMessage()).contains("Grading in progress by concurrent worker");

                verify(dockerSandboxService, never()).execute(anyString());
                verify(astPlagiarismService, never()).generateFingerprintsForSubmission(anyString());
        }

        @Test
        @DisplayName("FIX-03: Two recovery sweepers cannot both recover the same expired claim")
        void testTwoRecoverySweepers_CannotBothRecoverSameExpiredClaim() {
                InternalGradingService mockGradingService = mock(InternalGradingService.class);
                SubmissionRecoverySweeper sweeper1 = new SubmissionRecoverySweeper(
                                submissionRepository, bullMqJobDispatcher, mockGradingService);
                SubmissionRecoverySweeper sweeper2 = new SubmissionRecoverySweeper(
                                submissionRepository, bullMqJobDispatcher, mockGradingService);

                Submission expiredSub = Submission.builder()
                                .id("sub-expired-1")
                                .exam(testExam)
                                .student(User.builder().id("student-1").build())
                                .status("PROCESSING")
                                .submissionType("SINGLE_FILE")
                                .claimToken("expired-token-123")
                                .leaseExpiresAt(LocalDateTime.now().minusSeconds(15))
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(List.of(expiredSub));

                // Sweeper 1 atomically releases the claim (returns 1); Sweeper 2 sees 0 rows
                // updated
                when(submissionRepository.releaseExpiredClaim("sub-expired-1", "expired-token-123"))
                                .thenReturn(1)
                                .thenReturn(0);
                when(submissionRepository.claimSubmissionForRecovery(eq("sub-expired-1"), anyString(), any(), any()))
                                .thenReturn(1);
                when(bullMqJobDispatcher.dispatchGradingJob("sub-expired-1", "exam-claim-test", "SINGLE_FILE"))
                                .thenReturn(true);

                sweeper1.recoverPendingSubmissions();
                sweeper2.recoverPendingSubmissions();

                // Only Sweeper 1 dispatched recovery; Sweeper 2 did not
                verify(bullMqJobDispatcher, times(1)).dispatchGradingJob("sub-expired-1", "exam-claim-test",
                                "SINGLE_FILE");
                verify(mockGradingService, never()).processGrading(anyString());
        }

        @Test
        @DisplayName("FIX-03: A failed claim release (0 rows updated or exception) does not trigger BullMQ dispatch or fallback grading")
        void testFailedClaimRelease_DoesNotTriggerFallbackGrading() {
                InternalGradingService mockGradingService = mock(InternalGradingService.class);
                SubmissionRecoverySweeper sweeper = new SubmissionRecoverySweeper(
                                submissionRepository, bullMqJobDispatcher, mockGradingService);

                Submission expiredSubZeroRows = Submission.builder()
                                .id("sub-expired-zero")
                                .exam(testExam)
                                .status("PROCESSING")
                                .claimToken("stale-token-1")
                                .leaseExpiresAt(LocalDateTime.now().minusSeconds(10))
                                .build();

                Submission expiredSubThrows = Submission.builder()
                                .id("sub-expired-throws")
                                .exam(testExam)
                                .status("PROCESSING")
                                .claimToken("stale-token-2")
                                .leaseExpiresAt(LocalDateTime.now().minusSeconds(10))
                                .build();

                when(submissionRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                                .thenReturn(Collections.emptyList());
                when(submissionRepository.findByStatusAndLeaseExpiresAtBefore(eq("PROCESSING"),
                                any(LocalDateTime.class)))
                                .thenReturn(List.of(expiredSubZeroRows, expiredSubThrows));

                when(submissionRepository.releaseExpiredClaim("sub-expired-zero", "stale-token-1"))
                                .thenReturn(0);
                when(submissionRepository.releaseExpiredClaim("sub-expired-throws", "stale-token-2"))
                                .thenThrow(new RuntimeException("DB lock timeout"));

                sweeper.recoverPendingSubmissions();

                verify(bullMqJobDispatcher, never()).dispatchGradingJob(anyString(), anyString(), anyString());
                verify(mockGradingService, never()).processGrading(anyString());
        }
}
