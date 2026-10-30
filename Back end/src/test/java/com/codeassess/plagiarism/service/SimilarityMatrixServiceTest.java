package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.ExamMatrixCoordination;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.codeassess.plagiarism.exception.StaleWorkerGenerationException;
import com.codeassess.plagiarism.repository.AstFingerprintRepository;
import com.codeassess.plagiarism.repository.ExamMatrixCoordinationRepository;
import com.codeassess.plagiarism.repository.PlagiarismEvidenceRepository;
import com.codeassess.plagiarism.repository.SimilarityPairRepository;
import com.codeassess.plagiarism.similarity.EvidenceGenerator;
import com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SimilarityMatrixServiceTest {

    private ExamRepository examRepository;
    private SubmissionRepository submissionRepository;
    private AstFingerprintRepository fingerprintRepository;
    private SimilarityPairRepository similarityPairRepository;
    private PlagiarismEvidenceRepository evidenceRepository;
    private ExamMatrixCoordinationRepository coordinationRepository;
    private JaccardSimilarityCalculator similarityCalculator;
    private EvidenceGenerator evidenceGenerator;
    private SimilarityMatrixService service;

    private Exam testExam;

    @BeforeEach
    void setUp() {
        examRepository = mock(ExamRepository.class);
        submissionRepository = mock(SubmissionRepository.class);
        fingerprintRepository = mock(AstFingerprintRepository.class);
        similarityPairRepository = mock(SimilarityPairRepository.class);
        evidenceRepository = mock(PlagiarismEvidenceRepository.class);
        coordinationRepository = mock(ExamMatrixCoordinationRepository.class);
        similarityCalculator = new JaccardSimilarityCalculator();
        evidenceGenerator = new EvidenceGenerator();

        service = new SimilarityMatrixService(
                examRepository,
                submissionRepository,
                fingerprintRepository,
                similarityPairRepository,
                evidenceRepository,
                similarityCalculator,
                evidenceGenerator,
                coordinationRepository,
                null // null PlatformTransactionManager runs directly in callback
        );

        testExam = Exam.builder().id("exam-1").title("Midterm").build();
    }

    @Test
    @DisplayName("Original: generateMatrix normalizes ordering and rejects self comparison")
    void testGenerateMatrixNormalizesOrderingAndRejectsSelf() {
        when(examRepository.findById("exam-1")).thenReturn(Optional.of(testExam));

        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .exam(testExam)
                .currentGeneration(0L)
                .requestedGeneration(1L)
                .lockedGeneration(0L)
                .status("IDLE")
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        // Submissions with IDs out of order
        Submission subZ = Submission.builder().id("sub-Z").build();
        Submission subA = Submission.builder().id("sub-A").build();
        Submission subM = Submission.builder().id("sub-M").build();

        when(submissionRepository.findByExam_Id("exam-1")).thenReturn(Arrays.asList(subZ, subA, subM));
        when(similarityPairRepository.findByExam_Id("exam-1")).thenReturn(Collections.emptyList());
        when(fingerprintRepository.findBySubmission_Id(anyString())).thenReturn(Collections.emptyList());
        when(similarityPairRepository.save(any(SimilarityPair.class))).thenAnswer(i -> i.getArguments()[0]);

        service.generateMatrixForExam("exam-1");

        ArgumentCaptor<SimilarityPair> captor = ArgumentCaptor.forClass(SimilarityPair.class);
        verify(similarityPairRepository, times(3)).save(captor.capture());

        List<SimilarityPair> savedPairs = captor.getAllValues();
        assertEquals(3, savedPairs.size());

        for (SimilarityPair pair : savedPairs) {
            String id1 = pair.getSubmissionA().getId();
            String id2 = pair.getSubmissionB().getId();
            assertTrue(id1.compareTo(id2) < 0, "Pair ordering is not normalized: " + id1 + " >= " + id2);
        }
    }

    @Test
    @DisplayName("requestRefresh delegates atomically to coordination repository")
    void testRequestRefresh() {
        service.requestRefresh("exam-1");
        verify(coordinationRepository, times(1)).requestMatrixRefresh("exam-1");
    }

    @Test
    @DisplayName("acquireLease returns empty when exam status is FAILED (circuit breaker)")
    void testAcquireLease_WhenStatusFailed_ReturnsEmpty() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("FAILED")
                .requestedGeneration(2L)
                .currentGeneration(1L)
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        Optional<SimilarityMatrixService.LeaseGrant> lease = service.acquireLease("exam-1", "worker-1");

        assertThat(lease).isEmpty();
        verify(coordinationRepository, never()).save(any());
    }

    @Test
    @DisplayName("acquireLease returns empty when exam is in retry backoff")
    void testAcquireLease_WhenInRetryBackoff_ReturnsEmpty() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("IDLE")
                .requestedGeneration(2L)
                .currentGeneration(1L)
                .nextRetryAt(LocalDateTime.now().plusMinutes(5)) // in backoff
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        Optional<SimilarityMatrixService.LeaseGrant> lease = service.acquireLease("exam-1", "worker-1");

        assertThat(lease).isEmpty();
        verify(coordinationRepository, never()).save(any());
    }

    @Test
    @DisplayName("acquireLease returns empty when another worker holds an active unexpired lease")
    void testAcquireLease_WhenActiveUnexpiredLease_ReturnsEmpty() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("RUNNING")
                .leaseToken("token-active")
                .leaseExpiresAt(LocalDateTime.now().plusSeconds(60))
                .requestedGeneration(2L)
                .currentGeneration(1L)
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        Optional<SimilarityMatrixService.LeaseGrant> lease = service.acquireLease("exam-1", "worker-2");

        assertThat(lease).isEmpty();
        verify(coordinationRepository, never()).save(any());
    }

    @Test
    @DisplayName("acquireLease returns empty when matrix is already up to date")
    void testAcquireLease_WhenAlreadyUpToDate_ReturnsEmpty() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("IDLE")
                .requestedGeneration(2L)
                .currentGeneration(2L) // already up to date
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        Optional<SimilarityMatrixService.LeaseGrant> lease = service.acquireLease("exam-1", "worker-1");

        assertThat(lease).isEmpty();
        verify(coordinationRepository, never()).save(any());
    }

    @Test
    @DisplayName("acquireLease grants lease and marks status RUNNING when work is pending")
    void testAcquireLease_Success_GrantsLease() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("IDLE")
                .requestedGeneration(5L)
                .currentGeneration(4L)
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        Optional<SimilarityMatrixService.LeaseGrant> leaseOpt = service.acquireLease("exam-1", "worker-1");

        assertThat(leaseOpt).isPresent();
        SimilarityMatrixService.LeaseGrant grant = leaseOpt.get();
        assertThat(grant.examId()).isEqualTo("exam-1");
        assertThat(grant.lockedGeneration()).isEqualTo(5L);
        assertThat(grant.leaseToken()).isNotNull();

        assertThat(coord.getStatus()).isEqualTo("RUNNING");
        assertThat(coord.getLockedGeneration()).isEqualTo(5L);
        assertThat(coord.getWorkerId()).isEqualTo("worker-1");
        assertThat(coord.getLeaseExpiresAt()).isAfter(LocalDateTime.now());
        verify(coordinationRepository).save(coord);
    }

    @Test
    @DisplayName("renewLease returns false when coord missing, not RUNNING, token mismatch, or expired")
    void testRenewLease_FailureConditions() {
        // 1. Missing
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.empty());
        assertThat(service.renewLease("exam-1", "token-1", 60)).isFalse();

        // 2. Status not RUNNING
        ExamMatrixCoordination notRunning = ExamMatrixCoordination.builder()
                .status("IDLE")
                .leaseToken("token-1")
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(notRunning));
        assertThat(service.renewLease("exam-1", "token-1", 60)).isFalse();

        // 3. Token mismatch
        ExamMatrixCoordination tokenMismatch = ExamMatrixCoordination.builder()
                .status("RUNNING")
                .leaseToken("token-current")
                .leaseExpiresAt(LocalDateTime.now().plusSeconds(30))
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(tokenMismatch));
        assertThat(service.renewLease("exam-1", "stale-token", 60)).isFalse();

        // 4. Lease already expired
        ExamMatrixCoordination expired = ExamMatrixCoordination.builder()
                .status("RUNNING")
                .leaseToken("token-1")
                .leaseExpiresAt(LocalDateTime.now().minusSeconds(10))
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(expired));
        assertThat(service.renewLease("exam-1", "token-1", 60)).isFalse();
    }

    @Test
    @DisplayName("renewLease succeeds and extends expiry when valid")
    void testRenewLease_Success() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .status("RUNNING")
                .leaseToken("token-1")
                .leaseExpiresAt(LocalDateTime.now().plusSeconds(20))
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        boolean renewed = service.renewLease("exam-1", "token-1", 120);

        assertThat(renewed).isTrue();
        assertThat(coord.getLeaseExpiresAt()).isAfter(LocalDateTime.now().plusSeconds(100));
        verify(coordinationRepository).save(coord);
    }

    @Test
    @DisplayName("computeCalculation excludes self-comparison when student submitted multiple times")
    void testComputeCalculation_ExcludesSelfComparison_MultipleSubmissionsSameStudent() {
        User student = User.builder().id("stud-same").build();
        Submission sub1 = Submission.builder().id("sub-1").student(student).build();
        Submission sub2 = Submission.builder().id("sub-2").student(student).build();

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(sub1, sub2));
        when(similarityPairRepository.findByExam_Id("exam-1")).thenReturn(Collections.emptyList());

        SimilarityMatrixService.MatrixCalculationPlan plan = service.computeCalculation("exam-1");

        // Submissions belong to same student -> 0 inserts, 0 updates
        assertThat(plan.inserts()).isEmpty();
        assertThat(plan.updates()).isEmpty();
    }

    @Test
    @DisplayName("computeCalculation detects updates, inserts, and deactivations correctly")
    void testComputeCalculation_UpdatesInsertsAndDeactivations() {
        User stud1 = User.builder().id("stud-1").build();
        User stud2 = User.builder().id("stud-2").build();
        User stud3 = User.builder().id("stud-3").build();

        Submission subA = Submission.builder().id("sub-a").student(stud1).build();
        Submission subB = Submission.builder().id("sub-b").student(stud2).build();
        Submission subC = Submission.builder().id("sub-c").student(stud3).build();

        // Existing pair between A and B
        SimilarityPair pairAB = SimilarityPair.builder()
                .id(101L)
                .exam(testExam)
                .submissionA(subA)
                .submissionB(subB)
                .similarityRate(BigDecimal.valueOf(50.0))
                .sharedHashesCount(2)
                .isActive(true)
                .build();

        // Existing pair between A and deleted/removed submission D (should be deactivated)
        Submission subD = Submission.builder().id("sub-d").student(User.builder().id("stud-4").build()).build();
        SimilarityPair pairAD = SimilarityPair.builder()
                .id(102L)
                .exam(testExam)
                .submissionA(subA)
                .submissionB(subD)
                .isActive(true)
                .build();

        when(examRepository.findById("exam-1")).thenReturn(Optional.of(testExam));
        when(submissionRepository.findByExam_Id("exam-1")).thenReturn(List.of(subA, subB, subC));
        when(similarityPairRepository.findByExam_Id("exam-1")).thenReturn(List.of(pairAB, pairAD));

        // Setup fingerprints
        AstFingerprint fp1 = AstFingerprint.builder().submission(subA).hashValue(999L).nodeType("MethodDeclaration").startLine(1).endLine(10).build();
        AstFingerprint fp2 = AstFingerprint.builder().submission(subB).hashValue(999L).nodeType("MethodDeclaration").startLine(5).endLine(15).build();

        when(fingerprintRepository.findBySubmission_Id("sub-a")).thenReturn(List.of(fp1));
        when(fingerprintRepository.findBySubmission_Id("sub-b")).thenReturn(List.of(fp2));
        when(fingerprintRepository.findBySubmission_Id("sub-c")).thenReturn(Collections.emptyList());

        SimilarityMatrixService.MatrixCalculationPlan plan = service.computeCalculation("exam-1");

        // Pair AB exists -> update
        assertThat(plan.updates()).hasSize(1);
        assertThat(plan.updates().get(0).pairId()).isEqualTo(101L);

        // Pairs AC and BC are new -> inserts
        assertThat(plan.inserts()).hasSize(2);

        // Pair AD no longer in submissions -> deactivated
        assertThat(plan.pairsToDeactivate()).containsExactly(102L);
    }

    @Test
    @DisplayName("commitReconciliation throws StaleWorkerGenerationException when fencing checks fail")
    void testCommitReconciliation_Fails_WhenFencingTokenMismatchOrExpired() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("RUNNING")
                .lockedGeneration(3L)
                .leaseToken("token-valid")
                .leaseExpiresAt(LocalDateTime.now().plusSeconds(60))
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        SimilarityMatrixService.MatrixCalculationPlan emptyPlan = new SimilarityMatrixService.MatrixCalculationPlan(
                testExam, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );

        // 1. Token mismatch
        assertThatThrownBy(() -> service.commitReconciliation("exam-1", 3L, "token-stale", emptyPlan))
                .isInstanceOf(StaleWorkerGenerationException.class)
                .hasMessageContaining("Fencing check failed");

        // 2. Generation mismatch
        assertThatThrownBy(() -> service.commitReconciliation("exam-1", 2L, "token-valid", emptyPlan))
                .isInstanceOf(StaleWorkerGenerationException.class)
                .hasMessageContaining("Fencing check failed");

        // 3. Lease expired
        coord.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(5));
        assertThatThrownBy(() -> service.commitReconciliation("exam-1", 3L, "token-valid", emptyPlan))
                .isInstanceOf(StaleWorkerGenerationException.class)
                .hasMessageContaining("Fencing check failed");

        // Verify no side effects occurred
        verify(similarityPairRepository, never()).updateCalculatedMetrics(any(), any(), any(), any());
        verify(similarityPairRepository, never()).save(any());
        verify(similarityPairRepository, never()).deactivatePairsByIds(any());
    }

    @Test
    @DisplayName("commitReconciliation updates existing pairs, inserts new pairs, deactivates absent pairs, and releases lease")
    void testCommitReconciliation_Success() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("RUNNING")
                .lockedGeneration(4L)
                .currentGeneration(3L)
                .leaseToken("token-good")
                .leaseExpiresAt(LocalDateTime.now().plusSeconds(60))
                .failureCount(1)
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        Submission subA = Submission.builder().id("sub-a").build();
        Submission subB = Submission.builder().id("sub-b").build();
        AstFingerprint fp = AstFingerprint.builder().hashValue(12345L).nodeType("IfStmt").startLine(1).endLine(5).build();

        SimilarityPair pairRef = SimilarityPair.builder().id(200L).build();
        when(similarityPairRepository.getReferenceById(200L)).thenReturn(pairRef);

        SimilarityMatrixService.PairUpdatePlan updatePlan = new SimilarityMatrixService.PairUpdatePlan(
                200L, BigDecimal.valueOf(75.50), 1, true, List.of(fp), List.of(fp)
        );

        SimilarityPair savedNewPair = SimilarityPair.builder().id(201L).build();
        when(similarityPairRepository.save(any(SimilarityPair.class))).thenReturn(savedNewPair);

        SimilarityMatrixService.PairInsertPlan insertPlan = new SimilarityMatrixService.PairInsertPlan(
                subA, subB, BigDecimal.valueOf(90.00), 1, true, List.of(fp), List.of(fp)
        );

        SimilarityMatrixService.MatrixCalculationPlan plan = new SimilarityMatrixService.MatrixCalculationPlan(
                testExam, List.of(updatePlan), List.of(insertPlan), List.of(300L)
        );

        service.commitReconciliation("exam-1", 4L, "token-good", plan);

        // 1. Verify update metrics and evidence replacement
        verify(similarityPairRepository).updateCalculatedMetrics(eq(200L), eq(BigDecimal.valueOf(75.50)), eq(1), eq(true));
        verify(evidenceRepository).deleteBySimilarityPair_Id(200L);
        verify(evidenceRepository, atLeastOnce()).saveAll(anyList());

        // 2. Verify insert
        verify(similarityPairRepository).save(any(SimilarityPair.class));

        // 3. Verify deactivation of absent pairs
        verify(similarityPairRepository).deactivatePairsByIds(List.of(300L));

        // 4. Verify coordination updated to IDLE and currentGeneration advanced
        assertThat(coord.getCurrentGeneration()).isEqualTo(4L);
        assertThat(coord.getStatus()).isEqualTo("IDLE");
        assertThat(coord.getLeaseToken()).isNull();
        assertThat(coord.getWorkerId()).isNull();
        assertThat(coord.getLeaseExpiresAt()).isNull();
        assertThat(coord.getFailureCount()).isEqualTo(0);
        assertThat(coord.getLastError()).isNull();
        assertThat(coord.getNextRetryAt()).isNull();
        verify(coordinationRepository).save(coord);
    }

    @Test
    @DisplayName("handleCalculationFailure increments failure count and sets exponential backoff for attempt 1 and 2")
    void testHandleCalculationFailure_BackoffAttempts() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("RUNNING")
                .leaseToken("token-fail")
                .failureCount(0)
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        service.handleCalculationFailure("exam-1", "token-fail", new RuntimeException("DB Connection timeout"));

        assertThat(coord.getFailureCount()).isEqualTo(1);
        assertThat(coord.getStatus()).isEqualTo("IDLE");
        assertThat(coord.getLastError()).contains("RuntimeException: DB Connection timeout");
        assertThat(coord.getNextRetryAt()).isAfter(LocalDateTime.now());
        assertThat(coord.getLeaseToken()).isNull();
        verify(coordinationRepository).save(coord);
    }

    @Test
    @DisplayName("handleCalculationFailure marks status FAILED when failure count reaches 3 (circuit breaker)")
    void testHandleCalculationFailure_ThirdAttempt_TripsCircuitBreaker() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("RUNNING")
                .leaseToken("token-fail")
                .failureCount(2) // 2 + 1 = 3
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        service.handleCalculationFailure("exam-1", "token-fail", new IllegalStateException("Repeated fatal failure"));

        assertThat(coord.getFailureCount()).isEqualTo(3);
        assertThat(coord.getStatus()).isEqualTo("FAILED");
        assertThat(coord.getNextRetryAt()).isNull();
        assertThat(coord.getLastError()).contains("IllegalStateException: Repeated fatal failure");
        verify(coordinationRepository).save(coord);
    }

    @Test
    @DisplayName("handleCalculationFailure ignores failure if lease token does not match (stale worker report)")
    void testHandleCalculationFailure_StaleWorkerReport_Ignored() {
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("RUNNING")
                .leaseToken("new-worker-token")
                .failureCount(0)
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        service.handleCalculationFailure("exam-1", "old-stale-token", new RuntimeException("Stale error"));

        assertThat(coord.getFailureCount()).isEqualTo(0);
        assertThat(coord.getStatus()).isEqualTo("RUNNING");
        verify(coordinationRepository, never()).save(coord);
    }

    @Test
    @DisplayName("generateMatrixForExam catches StaleWorkerGenerationException cleanly without tripping circuit breaker")
    void testGenerateMatrixForExam_CatchesStaleWorkerGenerationException() {
        when(examRepository.findById("exam-1")).thenReturn(Optional.of(testExam));

        // Coord allows initial lease
        ExamMatrixCoordination coord = ExamMatrixCoordination.builder()
                .examId("exam-1")
                .status("IDLE")
                .requestedGeneration(2L)
                .currentGeneration(1L)
                .build();
        when(coordinationRepository.findByExamIdForUpdate("exam-1")).thenReturn(Optional.of(coord));

        when(submissionRepository.findByExam_Id("exam-1")).thenReturn(Collections.emptyList());
        when(similarityPairRepository.findByExam_Id("exam-1")).thenReturn(Collections.emptyList());

        // In second transaction (commit), lease is revoked or expired
        doAnswer(inv -> {
            coord.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
            return null;
        }).when(similarityPairRepository).deactivatePairsByIds(any());

        // Worker attempts generation
        service.generateMatrixForExam("exam-1", "worker-1");

        // StaleWorkerGenerationException was caught and logged; failureCount not incremented to FAILED
        assertThat(coord.getStatus()).isNotEqualTo("FAILED");
    }
}
