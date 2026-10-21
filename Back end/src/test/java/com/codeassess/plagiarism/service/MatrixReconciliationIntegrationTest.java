package com.codeassess.plagiarism.service;

import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
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
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class MatrixReconciliationIntegrationTest {

    @Autowired
    private SimilarityMatrixService similarityMatrixService;

    @Autowired
    private ExamMatrixSweeper examMatrixSweeper;

    @Autowired
    private ExamMatrixCoordinationRepository coordinationRepository;

    @Autowired
    private SimilarityPairRepository similarityPairRepository;

    @Autowired
    private PlagiarismEvidenceRepository evidenceRepository;

    @Autowired
    private AstFingerprintRepository fingerprintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseClassRepository courseClassRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private com.codeassess.queue.service.MatrixRefreshWorker matrixRefreshWorker;

    private String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record TestExamContext(User teacher, CourseClass courseClass, Exam exam, Submission subA, Submission subB) {}

    private TestExamContext createTestExamWithSubmissions() {
        String suffix = randomSuffix();
        User teacher = userRepository.save(User.builder()
                .email("teacher_" + suffix + "@test.edu.vn")
                .fullName("Teacher " + suffix)
                .role(Role.TEACHER)
                .passwordHash("pwd")
                .build());

        CourseClass cc = courseClassRepository.save(CourseClass.builder()
                .code("CC_" + suffix)
                .name("Class " + suffix)
                .teacher(teacher)
                .semester("FA24")
                .build());

        Exam exam = examRepository.save(Exam.builder()
                .title("Exam " + suffix)
                .courseClass(cc)
                .status("ACTIVE")
                .build());

        User stuA = userRepository.save(User.builder().email("sa_" + suffix + "@test.com").fullName("Student A").passwordHash("pwd").role(Role.STUDENT).build());
        User stuB = userRepository.save(User.builder().email("sb_" + suffix + "@test.com").fullName("Student B").passwordHash("pwd").role(Role.STUDENT).build());

        Submission sub1 = submissionRepository.save(Submission.builder().exam(exam).student(stuA).status("EVALUATED").sourceCode("code A").build());
        Submission sub2 = submissionRepository.save(Submission.builder().exam(exam).student(stuB).status("EVALUATED").sourceCode("code B").build());

        Submission subA = sub1.getId().compareTo(sub2.getId()) < 0 ? sub1 : sub2;
        Submission subB = sub1.getId().compareTo(sub2.getId()) < 0 ? sub2 : sub1;

        // Seed AST fingerprints with some shared hashes
        fingerprintRepository.save(AstFingerprint.builder().submission(subA).hashValue(1001L).nodeType("MethodDeclaration").startLine(1).endLine(5).build());
        fingerprintRepository.save(AstFingerprint.builder().submission(subA).hashValue(1002L).nodeType("ForStmt").startLine(6).endLine(10).build());

        fingerprintRepository.save(AstFingerprint.builder().submission(subB).hashValue(1001L).nodeType("MethodDeclaration").startLine(2).endLine(6).build());
        fingerprintRepository.save(AstFingerprint.builder().submission(subB).hashValue(1003L).nodeType("WhileStmt").startLine(7).endLine(12).build());

        return new TestExamContext(teacher, cc, exam, subA, subB);
    }

    @Test
    @DisplayName("In-place reconciliation: Existing pair preserves primary key and faculty adjudication fields")
    public void testExistingPairPreservesIdAndAdjudication() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Initial generation
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        List<SimilarityPair> initialPairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(initialPairs).hasSize(1);
        SimilarityPair initialPair = initialPairs.get(0);
        Long pairId = initialPair.getId();

        // Teacher adjudicates the pair
        initialPair.setAdjudicationStatus("FLAG_CONFIRMED");
        initialPair.setAdjudicationNotes("Verified code collusion");
        initialPair.setAdjudicatedBy(ctx.teacher());
        initialPair.setAdjudicatedAt(LocalDateTime.now());
        similarityPairRepository.save(initialPair);

        // Add more fingerprints to change similarity rate
        fingerprintRepository.save(AstFingerprint.builder().submission(ctx.subB()).hashValue(1002L).nodeType("ForStmt").startLine(15).endLine(20).build());

        // Increment requested generation and regenerate
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        // Verification
        List<SimilarityPair> regeneratedPairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(regeneratedPairs).hasSize(1);
        SimilarityPair regeneratedPair = regeneratedPairs.get(0);

        // Crucial invariants:
        assertThat(regeneratedPair.getId()).isEqualTo(pairId); // Primary key preserved!
        assertThat(regeneratedPair.getAdjudicationStatus()).isEqualTo("FLAG_CONFIRMED"); // Adjudication preserved!
        assertThat(regeneratedPair.getAdjudicationNotes()).isEqualTo("Verified code collusion"); // Notes preserved!
        assertThat(regeneratedPair.getAdjudicatedBy().getId()).isEqualTo(ctx.teacher().getId());
        assertThat(regeneratedPair.getSharedHashesCount()).isEqualTo(2); // Metric updated from 1 to 2!
    }

    @Test
    @DisplayName("Evidence replacement and inactive pair retention: Evidence is replaced on update, retained on deactivation")
    public void testEvidenceReplacementAndRetention() {
        TestExamContext ctx = createTestExamWithSubmissions();

        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(pairs).hasSize(1);
        SimilarityPair pair = pairs.get(0);

        List<PlagiarismEvidence> evidences1 = evidenceRepository.findBySimilarityPair_Id(pair.getId());
        assertThat(evidences1).hasSize(1);
        String evidenceId1 = evidences1.get(0).getId();

        // Regenerate matrix with an extra shared hash
        fingerprintRepository.save(AstFingerprint.builder().submission(ctx.subB()).hashValue(1002L).nodeType("ForStmt").startLine(20).endLine(25).build());
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        // Evidence should be replaced: now has 2 evidence rows, old evidence row replaced
        List<PlagiarismEvidence> evidences2 = evidenceRepository.findBySimilarityPair_Id(pair.getId());
        assertThat(evidences2).hasSize(2);
        assertThat(evidences2).extracting(PlagiarismEvidence::getId).doesNotContain(evidenceId1);

        // Now test deactivation retention: soft-deactivate pair
        similarityPairRepository.deactivatePairsByIds(Collections.singletonList(pair.getId()));
        SimilarityPair deactivated = similarityPairRepository.findById(pair.getId()).orElseThrow();
        assertThat(deactivated.getIsActive()).isFalse();

        // Evidence for deactivated pair must still be retained in MySQL for audit
        List<PlagiarismEvidence> evidencesDeactivated = evidenceRepository.findBySimilarityPair_Id(pair.getId());
        assertThat(evidencesDeactivated).hasSize(2);
    }

    @Test
    @DisplayName("Stale worker fencing: Worker with outdated lease token is rejected at commit time")
    public void testStaleWorkerFencingRejection() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Step 1: Worker A claims lease
        Optional<SimilarityMatrixService.LeaseGrant> leaseOptA = similarityMatrixService.acquireLease(ctx.exam().getId(), "worker-A");
        assertThat(leaseOptA).isPresent();
        SimilarityMatrixService.LeaseGrant leaseA = leaseOptA.get();

        // Step 2: Compute calculation plan for Worker A
        SimilarityMatrixService.MatrixCalculationPlan planA = similarityMatrixService.computeCalculation(ctx.exam().getId());

        // Step 3: Simulate lease takeover by Worker B (e.g. Worker A timed out or lease was superseded)
        ExamMatrixCoordination coord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        coord.setLeaseToken("lease-token-B");
        coord.setWorkerId("worker-B");
        coordinationRepository.save(coord);

        // Step 4: Worker A attempts to commit with its old lease token
        assertThatThrownBy(() -> similarityMatrixService.commitReconciliation(
                ctx.exam().getId(), leaseA.lockedGeneration(), leaseA.leaseToken(), planA
        )).isInstanceOf(StaleWorkerGenerationException.class)
          .hasMessageContaining("Fencing check failed");

        // Verify that no pairs were created or modified by Worker A
        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(pairs).isEmpty();
    }

    @Test
    @DisplayName("Sweeper recovery: Abandoned expired lease is recovered by ExamMatrixSweeper")
    public void testSweeperRecoveryForExpiredLease() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Simulate an abandoned lease: requested_generation = 2, locked_generation = 1, status = RUNNING, lease_expires_at in past
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());
        ExamMatrixCoordination coord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        coord.setStatus("RUNNING");
        coord.setLockedGeneration(1L);
        coord.setRequestedGeneration(2L);
        coord.setLeaseToken("abandoned-token");
        coord.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(10)); // expired!
        coordinationRepository.save(coord);

        // Verify recovery query finds this exam
        List<String> recoveryExams = coordinationRepository.findExamsRequiringRefreshRecovery();
        assertThat(recoveryExams).contains(ctx.exam().getId());

        // Run sweeper
        int recovered = examMatrixSweeper.sweepAndRecover();
        assertThat(recovered).isGreaterThanOrEqualTo(1);

        // Verify that coordination is now IDLE and matrix is computed
        ExamMatrixCoordination updatedCoord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(updatedCoord.getStatus()).isEqualTo("IDLE");
        assertThat(updatedCoord.getCurrentGeneration()).isGreaterThanOrEqualTo(1L);

        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(pairs).hasSize(1);
    }

    @Test
    @DisplayName("Empty fingerprints and zero/single submissions handled safely without exceptions")
    public void testEmptyOrSingleSubmissionExam() {
        String suffix = randomSuffix();
        User teacher = userRepository.save(User.builder().email("t_empty_" + suffix + "@test.com").fullName("T").passwordHash("p").role(Role.TEACHER).build());
        CourseClass cc = courseClassRepository.save(CourseClass.builder().code("C_E_" + suffix).name("C").teacher(teacher).semester("FA24").build());
        Exam exam = examRepository.save(Exam.builder().title("Empty Exam " + suffix).courseClass(cc).status("ACTIVE").build());

        // Only 1 submission
        User stu = userRepository.save(User.builder().email("s_single_" + suffix + "@test.com").fullName("S").passwordHash("p").role(Role.STUDENT).build());
        submissionRepository.save(Submission.builder().exam(exam).student(stu).status("EVALUATED").sourceCode("single code").build());

        // Refresh matrix for exam with only 1 submission
        similarityMatrixService.generateMatrixForExam(exam.getId());

        // Must produce 0 pairs without any error
        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(exam.getId());
        assertThat(pairs).isEmpty();

        ExamMatrixCoordination coord = coordinationRepository.findById(exam.getId()).orElseThrow();
        assertThat(coord.getStatus()).isEqualTo("IDLE");
        assertThat(coord.getCurrentGeneration()).isEqualTo(coord.getRequestedGeneration());
    }

    @Test
    @DisplayName("Multi-class teacher query: Active pairs correctly filtered across single and multi-class exams")
    public void testMultiClassTeacherActiveQuery() {
        TestExamContext ctx = createTestExamWithSubmissions();

        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        List<SimilarityPair> activePairs = similarityPairRepository.findActiveByTeacherId(ctx.teacher().getId());
        assertThat(activePairs).hasSize(1);

        // Deactivate pair
        similarityPairRepository.deactivatePairsByIds(Collections.singletonList(activePairs.get(0).getId()));

        // Active query excludes it
        List<SimilarityPair> activeAfter = similarityPairRepository.findActiveByTeacherId(ctx.teacher().getId());
        assertThat(activeAfter).isEmpty();

        // Audit query includes it
        List<SimilarityPair> allAfter = similarityPairRepository.findAllByTeacherId(ctx.teacher().getId());
        assertThat(allAfter).hasSize(1);
    }

    @Test
    @DisplayName("Duplicate job delivery: Redundant execution is safely skipped without side effects")
    public void testDuplicateJobDeliveryIsNoOp() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Worker 1 runs and commits generation 1
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        ExamMatrixCoordination coord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coord.getCurrentGeneration()).isEqualTo(1L);
        assertThat(coord.getRequestedGeneration()).isEqualTo(1L);

        // Duplicate delivery arrives for generation 1 (requested_generation is not higher)
        Optional<SimilarityMatrixService.LeaseGrant> leaseOpt = similarityMatrixService.acquireLease(ctx.exam().getId(), "worker-duplicate");
        assertThat(leaseOpt).isEmpty(); // Safely rejected! No duplicate computation or overwrite

        // Verify state remains consistent
        ExamMatrixCoordination coordAfter = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordAfter.getCurrentGeneration()).isEqualTo(1L);
        assertThat(coordAfter.getStatus()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("Queue enqueue failure recovery: Unenqueued or dropped refresh request is reclaimed by Sweeper")
    public void testQueueEnqueueFailureReclaimedBySweeper() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Initial generation
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        // A new submission arrives, requested_generation increments, but queue enqueue fails (or worker is lost)
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());

        ExamMatrixCoordination coord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coord.getRequestedGeneration()).isEqualTo(2L);
        assertThat(coord.getCurrentGeneration()).isEqualTo(1L);
        assertThat(coord.getStatus()).isEqualTo("IDLE");

        // The Sweeper detects that requested_generation > current_generation even though status is IDLE
        int recovered = examMatrixSweeper.sweepAndRecover();
        assertThat(recovered).isGreaterThanOrEqualTo(1);

        // Now current_generation is brought up to 2
        ExamMatrixCoordination coordAfter = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordAfter.getCurrentGeneration()).isEqualTo(2L);
        assertThat(coordAfter.getStatus()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("Concurrent refresh requests: Multiple threads calling acquireLease result in exactly one active lease")
    public void testConcurrentRefreshLeaseAcquisition() throws Exception {
        TestExamContext ctx = createTestExamWithSubmissions();

        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());

        int threadCount = 2;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger grantedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String workerId = "worker-" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    Optional<SimilarityMatrixService.LeaseGrant> lease = similarityMatrixService.acquireLease(ctx.exam().getId(), workerId);
                    if (lease.isPresent()) {
                        grantedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore concurrency acquisition failure
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Exactly one worker must have obtained the lease
        assertThat(grantedCount.get()).isEqualTo(1);

        ExamMatrixCoordination coord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coord.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("Concurrent adjudication and reconciliation: Adjudication update succeeds and reconciliation retains teacher verdict")
    public void testConcurrentAdjudicationAndReconciliation() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // 1. Initial matrix calculation creates pair
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(pairs).hasSize(1);
        Long pairId = pairs.get(0).getId();

        // 2. Teacher adjudicates using pessimistic lock findByIdForUpdate
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            SimilarityPair lockedPair = similarityPairRepository.findByIdForUpdate(pairId).orElseThrow();
            lockedPair.setAdjudicationStatus("DISMISSED");
            lockedPair.setAdjudicationNotes("False positive reviewed by instructor");
            lockedPair.setAdjudicatedBy(ctx.teacher());
            lockedPair.setAdjudicatedAt(LocalDateTime.now());
            similarityPairRepository.save(lockedPair);
        });

        // 3. New submission fingerprints arrive triggering refresh
        fingerprintRepository.save(AstFingerprint.builder().submission(ctx.subB()).hashValue(1002L).nodeType("ForStmt").startLine(30).endLine(35).build());
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        // 4. Assert reconciliation preserved the adjudication and updated the metric
        SimilarityPair reconciledPair = similarityPairRepository.findById(pairId).orElseThrow();
        assertThat(reconciledPair.getAdjudicationStatus()).isEqualTo("DISMISSED");
        assertThat(reconciledPair.getAdjudicationNotes()).isEqualTo("False positive reviewed by instructor");
        assertThat(reconciledPair.getSharedHashesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Lease renewal and stale token rejection: Valid worker can extend lease; superseded worker is rejected")
    public void testLeaseRenewalAndStaleTokenRejection() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // 1. Worker 1 acquires initial lease
        Optional<SimilarityMatrixService.LeaseGrant> leaseOpt = similarityMatrixService.acquireLease(ctx.exam().getId(), "worker-renew-1");
        assertThat(leaseOpt).isPresent();
        SimilarityMatrixService.LeaseGrant lease = leaseOpt.get();

        ExamMatrixCoordination coordBefore = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        LocalDateTime initialExpiry = coordBefore.getLeaseExpiresAt();

        // 2. Worker 1 safely renews lease by 300 seconds
        boolean renewed = similarityMatrixService.renewLease(ctx.exam().getId(), lease.leaseToken(), 300);
        assertThat(renewed).isTrue();

        ExamMatrixCoordination coordAfterRenew = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordAfterRenew.getLeaseExpiresAt()).isAfter(initialExpiry);

        // 3. Simulate lease takeover / supersede by Worker 2
        coordAfterRenew.setLeaseToken("superseded-token-2");
        coordAfterRenew.setWorkerId("worker-renew-2");
        coordinationRepository.save(coordAfterRenew);

        // 4. Worker 1 attempts renewal with its old token -> rejected!
        boolean staleRenewed = similarityMatrixService.renewLease(ctx.exam().getId(), lease.leaseToken(), 300);
        assertThat(staleRenewed).isFalse();
    }

    @Test
    @DisplayName("Lease heartbeat: Active lease is periodically extended by heartbeat and shuts down cleanly without thread leaks")
    public void testLeaseHeartbeatPeriodicRenewalAndCleanShutdown() throws Exception {
        TestExamContext ctx = createTestExamWithSubmissions();

        // 1. Acquire lease
        Optional<SimilarityMatrixService.LeaseGrant> leaseOpt = similarityMatrixService.acquireLease(ctx.exam().getId(), "worker-heartbeat-1");
        assertThat(leaseOpt).isPresent();
        SimilarityMatrixService.LeaseGrant lease = leaseOpt.get();

        ExamMatrixCoordination coordBefore = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        LocalDateTime expiry1 = coordBefore.getLeaseExpiresAt();

        // 2. Start heartbeat running at 1-second interval, extending lease by 300s
        AutoCloseable heartbeat = similarityMatrixService.startLeaseHeartbeat(ctx.exam().getId(), lease.leaseToken(), 1, 300);

        // Sleep 1.5s so heartbeat fires at least once
        Thread.sleep(1500);

        ExamMatrixCoordination coordAfterHeartbeat = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordAfterHeartbeat.getLeaseExpiresAt()).isAfter(expiry1);

        // 3. Close heartbeat
        heartbeat.close();

        // Verify thread has terminated cleanly without leaks
        Thread.sleep(200);
        boolean heartbeatThreadActive = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.getName().contains("matrix-heartbeat-" + ctx.exam().getId()) && t.isAlive());
        assertThat(heartbeatThreadActive).isFalse();
    }

    @Test
    @DisplayName("Deterministic failure & retry backoff: Backoff delays retries and max retries marks exam FAILED")
    public void testDeterministicComputationFailureAndRetryBackoff() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Worker claims lease
        Optional<SimilarityMatrixService.LeaseGrant> leaseOpt = similarityMatrixService.acquireLease(ctx.exam().getId(), "worker-fail-1");
        assertThat(leaseOpt).isPresent();
        SimilarityMatrixService.LeaseGrant lease = leaseOpt.get();

        // Failure 1: records failure, sets backoff
        similarityMatrixService.handleCalculationFailure(ctx.exam().getId(), lease.leaseToken(), new IllegalArgumentException("Corrupted AST in submission"));

        ExamMatrixCoordination coord1 = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coord1.getFailureCount()).isEqualTo(1);
        assertThat(coord1.getLastError()).contains("IllegalArgumentException: Corrupted AST in submission");
        assertThat(coord1.getNextRetryAt()).isAfter(LocalDateTime.now());
        assertThat(coord1.getStatus()).isEqualTo("IDLE");

        // Sweeper must NOT pick up exam because nextRetryAt is in future!
        List<String> recoveryExams = coordinationRepository.findExamsRequiringRefreshRecovery();
        assertThat(recoveryExams).doesNotContain(ctx.exam().getId());

        // Simulate 2 more failures to trigger circuit breaker (3 total)
        coord1.setFailureCount(2);
        coord1.setLeaseToken(lease.leaseToken());
        coordinationRepository.save(coord1);

        similarityMatrixService.handleCalculationFailure(ctx.exam().getId(), lease.leaseToken(), new IllegalStateException("Fatal unrecoverable error"));

        ExamMatrixCoordination coordFatal = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordFatal.getFailureCount()).isEqualTo(3);
        assertThat(coordFatal.getStatus()).isEqualTo("FAILED");
        assertThat(coordFatal.getNextRetryAt()).isNull();

        // Sweeper permanently ignores FAILED exam without new refresh request
        List<String> recoveryAfterFatal = coordinationRepository.findExamsRequiringRefreshRecovery();
        assertThat(recoveryAfterFatal).doesNotContain(ctx.exam().getId());
    }

    @Test
    @DisplayName("Reactivation of previously failed exam: New refresh request resets failure count and FAILED status")
    public void testReactivationOfPreviouslyFailedExam() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Set exam to FAILED state with 3 failures
        coordinationRepository.initCoordinationIfAbsent(ctx.exam().getId());
        ExamMatrixCoordination coord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        coord.setStatus("FAILED");
        coord.setFailureCount(3);
        coord.setLastError("Past persistent failure");
        coord.setRequestedGeneration(2L);
        coord.setCurrentGeneration(1L);
        coordinationRepository.save(coord);

        // A new submission or refresh request arrives
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());

        ExamMatrixCoordination reactivatedCoord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(reactivatedCoord.getStatus()).isEqualTo("IDLE"); // Reactivated!
        assertThat(reactivatedCoord.getFailureCount()).isEqualTo(0); // Counter reset!
        assertThat(reactivatedCoord.getLastError()).isNull(); // Error cleared!
        assertThat(reactivatedCoord.getNextRetryAt()).isNull(); // Retry timestamp cleared!
        assertThat(reactivatedCoord.getRequestedGeneration()).isEqualTo(3L);

        // Now calculation can run successfully
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        ExamMatrixCoordination completedCoord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(completedCoord.getStatus()).isEqualTo("IDLE");
        assertThat(completedCoord.getCurrentGeneration()).isEqualTo(3L);
        assertThat(completedCoord.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Coalescing: A new generation arriving while another generation is running commits both sequentially")
    public void testNewGenerationArrivingWhileAnotherGenerationIsRunning() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // 1. Worker A acquires lease for Generation 1
        Optional<SimilarityMatrixService.LeaseGrant> leaseOptA = similarityMatrixService.acquireLease(ctx.exam().getId(), "worker-A");
        assertThat(leaseOptA).isPresent();
        SimilarityMatrixService.LeaseGrant leaseA = leaseOptA.get();
        assertThat(leaseA.lockedGeneration()).isEqualTo(1L);

        // 2. While Worker A is computing, new submission arrives and increments requested generation
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());

        ExamMatrixCoordination coordMid = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordMid.getRequestedGeneration()).isEqualTo(2L);
        assertThat(coordMid.getLockedGeneration()).isEqualTo(1L);

        // 3. Worker A commits Generation 1
        SimilarityMatrixService.MatrixCalculationPlan planA = similarityMatrixService.computeCalculation(ctx.exam().getId());
        similarityMatrixService.commitReconciliation(ctx.exam().getId(), leaseA.lockedGeneration(), leaseA.leaseToken(), planA);

        ExamMatrixCoordination coordAfterA = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordAfterA.getCurrentGeneration()).isEqualTo(1L);
        assertThat(coordAfterA.getRequestedGeneration()).isEqualTo(2L);
        assertThat(coordAfterA.getStatus()).isEqualTo("IDLE");

        // 4. Worker B acquires lease for pending Generation 2 and commits
        Optional<SimilarityMatrixService.LeaseGrant> leaseOptB = similarityMatrixService.acquireLease(ctx.exam().getId(), "worker-B");
        assertThat(leaseOptB).isPresent();
        assertThat(leaseOptB.get().lockedGeneration()).isEqualTo(2L);

        SimilarityMatrixService.MatrixCalculationPlan planB = similarityMatrixService.computeCalculation(ctx.exam().getId());
        similarityMatrixService.commitReconciliation(ctx.exam().getId(), leaseOptB.get().lockedGeneration(), leaseOptB.get().leaseToken(), planB);

        ExamMatrixCoordination coordAfterB = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordAfterB.getCurrentGeneration()).isEqualTo(2L);
        assertThat(coordAfterB.getStatus()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("Adjudication authorization prevents unauthorized row locking on unowned records")
    public void testAdjudicationAuthorizationPreventsUnauthorizedRowLocking() {
        TestExamContext ctx = createTestExamWithSubmissions();
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId());

        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(pairs).hasSize(1);
        Long pairId = pairs.get(0).getId();

        // Create a different teacher
        User unauthorizedTeacher = userRepository.save(User.builder()
                .email("unauthorized_" + randomSuffix() + "@test.edu.vn")
                .fullName("Unauthorized Teacher")
                .role(Role.TEACHER)
                .passwordHash("pwd")
                .build());

        // Unauthorized teacher attempts to lock and query record atomically
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Optional<SimilarityPair> unauthorizedOpt = similarityPairRepository.findByIdAndTeacherIdForUpdate(pairId, unauthorizedTeacher.getId());
            assertThat(unauthorizedOpt).isEmpty(); // No row locked, 0 rows returned!
        });

        // Authorized teacher successfully acquires lock
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Optional<SimilarityPair> authorizedOpt = similarityPairRepository.findByIdAndTeacherIdForUpdate(pairId, ctx.teacher().getId());
            assertThat(authorizedOpt).isPresent();
            assertThat(authorizedOpt.get().getId()).isEqualTo(pairId);
        });
    }

    @Test
    @DisplayName("BullMQ worker bridge: MatrixRefreshWorker processes job and deduplicates duplicate deliveries")
    public void testBullMQWorkerBridgeProcessingAndDeduplication() {
        TestExamContext ctx = createTestExamWithSubmissions();

        com.codeassess.queue.dto.PlagiarismJobPayload payload = com.codeassess.queue.dto.PlagiarismJobPayload.builder()
                .jobId("matrix-job-" + randomSuffix())
                .jobType(com.codeassess.queue.dto.JobType.MATRIX_REFRESH)
                .schemaVersion("1.0")
                .correlationId("corr-" + randomSuffix())
                .examId(ctx.exam().getId())
                .submissionId("ALL")
                .timestamp(java.time.Instant.now())
                .retryCount(0)
                .build();

        // Process job
        boolean processed = matrixRefreshWorker.processJob(payload);
        assertThat(processed).isTrue();

        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(pairs).hasSize(1);

        // Process duplicate job delivery (same generation already current)
        boolean duplicateProcessed = matrixRefreshWorker.processJob(payload);
        assertThat(duplicateProcessed).isTrue(); // Safely handled as idempotent no-op!
    }

    @Test
    @DisplayName("Drain pending generations: generateMatrixForExam automatically loops to drain newly requested generation")
    public void testMultipleRefreshRequestsWhileMatrixIsRunningDrainsGenerationsAutomatically() {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Initial state: generation 1 requested
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());

        // In addition, add SubC
        String suffix = randomSuffix();
        User stuC = userRepository.save(User.builder().email("sc_" + suffix + "@test.com").fullName("Student C").passwordHash("pwd").role(Role.STUDENT).build());
        Submission subC = submissionRepository.save(Submission.builder().exam(ctx.exam()).student(stuC).status("EVALUATED").sourceCode("code C").build());
        fingerprintRepository.save(AstFingerprint.builder().submission(subC).hashValue(1001L).nodeType("MethodDeclaration").startLine(1).endLine(5).build());

        // Also request refresh for SubC (requested_generation = 2)
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());

        ExamMatrixCoordination coordBefore = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordBefore.getRequestedGeneration()).isEqualTo(2L);
        assertThat(coordBefore.getCurrentGeneration()).isEqualTo(0L);

        // Run generateMatrixForExam once
        similarityMatrixService.generateMatrixForExam(ctx.exam().getId(), "drain-worker");

        // Verify that all generations are drained up to 2 without waiting for sweeper
        ExamMatrixCoordination coordAfter = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coordAfter.getStatus()).isEqualTo("IDLE");
        assertThat(coordAfter.getCurrentGeneration()).isEqualTo(2L);
        assertThat(coordAfter.getRequestedGeneration()).isEqualTo(2L);

        // All 3 pairs ((A,B), (A,C), (B,C)) must be present
        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(pairs).hasSize(3);
    }

    @Test
    @DisplayName("Concurrent matrix generation: Three workers concurrently calling generateMatrixForExam produce all 3 pairs without deadlock")
    public void testThreeConcurrentWorkersDoNotDeadlockAndProduceAllPairs() throws Exception {
        TestExamContext ctx = createTestExamWithSubmissions();

        // Add 3rd submission SubC
        String suffix = randomSuffix();
        User stuC = userRepository.save(User.builder().email("sc2_" + suffix + "@test.com").fullName("Student C2").passwordHash("pwd").role(Role.STUDENT).build());
        Submission subC = submissionRepository.save(Submission.builder().exam(ctx.exam()).student(stuC).status("EVALUATED").sourceCode("code C2").build());
        fingerprintRepository.save(AstFingerprint.builder().submission(subC).hashValue(1001L).nodeType("MethodDeclaration").startLine(1).endLine(5).build());

        // 3 refresh requests arrived
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());
        coordinationRepository.requestMatrixRefresh(ctx.exam().getId());

        int threadCount = 3;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String workerId = "concurrent-worker-" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    similarityMatrixService.generateMatrixForExam(ctx.exam().getId(), workerId);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // No uncaught exceptions / deadlocks
        assertThat(failureCount.get()).isEqualTo(0);

        // Final state: all 3 pairs exist
        List<SimilarityPair> pairs = similarityPairRepository.findByExam_Id(ctx.exam().getId());
        assertThat(pairs).hasSize(3);

        // Status is IDLE and currentGeneration equals requestedGeneration
        ExamMatrixCoordination coord = coordinationRepository.findById(ctx.exam().getId()).orElseThrow();
        assertThat(coord.getStatus()).isEqualTo("IDLE");
        assertThat(coord.getCurrentGeneration()).isEqualTo(coord.getRequestedGeneration());
    }
}
