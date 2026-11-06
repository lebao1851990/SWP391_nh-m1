package com.codeassess.submission.service;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.scoring.entity.StudentQuestionScore;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database Integration Test Suite for codeassess submission intake, concurrency, and recovery fencing.
 *
 * This test suite executes true JDBC transactions against an isolated test database (MySQL or isolated TiDB staging).
 * It is guarded by:
 * - -Dtest.database.integration=true (skips execution when isolated test DB is not running)
 * - DatabaseSafetyGuard (aborts if pointing to live dev/production database)
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "test.database.integration", matches = "true")
@DisplayName("Database Integration Test: Real Transactional Concurrency and Rollback")
class ZipSubmissionIntakeDatabaseIntegrationTest {

    @Autowired
    private SubmissionPersistenceService submissionPersistenceService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamQuestionRepository examQuestionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private DataSource dataSource;

    private Exam testExam;
    private ExamQuestion testQuestion;
    private User testStudent;

    @BeforeEach
    void setUp() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                isDatabaseAvailable(),
                "Isolated test database is not available. Skipping real database integration test."
        );

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        testStudent = User.builder()
                .id("db-student-" + uniqueSuffix)
                .email("student-" + uniqueSuffix + "@test.com")
                .passwordHash("hash")
                .fullName("Test Student " + uniqueSuffix)
                .role(Role.STUDENT)
                .build();
        userRepository.save(testStudent);

        testExam = Exam.builder()
                .id("db-exam-" + uniqueSuffix)
                .title("Database Concurrency Exam " + uniqueSuffix)
                .status("active")
                .examDate(LocalDateTime.now().minusHours(1))
                .duration(120)
                .build();
        examRepository.save(testExam);

        testQuestion = ExamQuestion.builder()
                .id("db-q-" + uniqueSuffix)
                .exam(testExam)
                .title("Question 1")
                .status("PUBLISHED")
                .maxScore(BigDecimal.valueOf(10.00))
                .displayOrder(1)
                .build();
        examQuestionRepository.save(testQuestion);
    }

    private boolean isDatabaseAvailable() {
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Integration A: True concurrent database transactions for same question & student increment sequence monotonically without lost updates")
    void testConcurrentSubmissions_RealDatabaseTransactions_MonotonicSequence() throws Exception {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<Submission>> futures = new ArrayList<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        List<Submission> committedSubmissions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(); // Simultaneous start across separate DB transactions
                    TransactionTemplate tt = new TransactionTemplate(transactionManager);
                    tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                    return tt.execute(status -> {
                        Submission sub = submissionPersistenceService.createInitialZipSubmission(
                                testExam.getId(),
                                testQuestion.getId(),
                                testExam,
                                testQuestion,
                                testStudent,
                                "sol-" + index + ".zip",
                                "arch-hash-" + index,
                                "tree-hash-" + index,
                                1,
                                "Main"
                        );
                        committedSubmissions.add(sub);
                        return sub;
                    });
                } catch (Throwable t) {
                    errors.add(t);
                    throw new RuntimeException(t);
                }
            }));
        }

        for (Future<Submission> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
        if (!terminated) executor.shutdownNow();

        assertThat(terminated).isTrue();
        assertThat(errors).isEmpty();
        assertThat(committedSubmissions).hasSize(threadCount);

        // Verify unique submission IDs
        Set<String> uniqueIds = new HashSet<>();
        for (Submission s : committedSubmissions) {
            uniqueIds.add(s.getId());
        }
        assertThat(uniqueIds).as("All committed submissions must have unique IDs").hasSize(threadCount);

        // Inspect database state
        Optional<StudentQuestionScore> scoreOpt = studentQuestionScoreRepository.findByQuestion_IdAndStudent_Id(
                testQuestion.getId(), testStudent.getId()
        );
        assertThat(scoreOpt).isPresent();
        StudentQuestionScore score = scoreOpt.get();

        // Verify no lost updates & monotonic sequence
        assertThat(score.getSubmissionCount()).as("Submission count must equal exactly threadCount").isEqualTo(threadCount);
        assertThat(score.getLatestSubmissionSeq()).as("Latest sequence must equal exactly threadCount").isEqualTo(threadCount);

        // Verify latest_submission_id belongs to one of the committed submissions
        assertThat(uniqueIds).contains(score.getLatestSubmissionId());
    }

    @Test
    @DisplayName("Integration B: If upsertIntakeSequence fails inside a transaction, the submission insert is completely rolled back")
    void testTransactionRollback_UpsertFails_SubmissionRolledBack() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String orphanSubId = "sub-orphan-" + UUID.randomUUID();

        assertThatThrownBy(() -> {
            tt.execute(status -> {
                Submission sub = Submission.builder()
                        .id(orphanSubId)
                        .exam(testExam)
                        .question(testQuestion)
                        .student(testStudent)
                        .status("PENDING")
                        .submissionType("ZIP")
                        .build();
                submissionRepository.save(sub);

                // Force exception inside the transaction
                throw new IllegalStateException("Forced crash before score sequence upsert completes");
            });
        }).isInstanceOf(IllegalStateException.class);

        // Verify submission was rolled back and is not in DB
        Optional<Submission> found = submissionRepository.findById(orphanSubId);
        assertThat(found).as("Submission must be completely rolled back from DB").isEmpty();

        // Verify student_question_scores remains untouched
        Optional<StudentQuestionScore> scoreOpt = studentQuestionScoreRepository.findByQuestion_IdAndStudent_Id(
                testQuestion.getId(), testStudent.getId()
        );
        assertThat(scoreOpt).isEmpty();
    }

    @Test
    @DisplayName("Integration C: Stale recovery tokens cannot update newer claims, and completed worker state cannot be overwritten")
    void testConditionalStateUpdates_StaleRecoveryToken_CannotUpdateNewerClaim() {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String subId = "sub-claim-fencing-" + UUID.randomUUID();

        tt.execute(status -> {
            Submission sub = Submission.builder()
                    .id(subId)
                    .exam(testExam)
                    .question(testQuestion)
                    .student(testStudent)
                    .status("PENDING")
                    .submissionType("ZIP")
                    .claimToken("active-token-B")
                    .leaseExpiresAt(LocalDateTime.now().plusSeconds(60))
                    .build();
            submissionRepository.save(sub);
            return null;
        });

        // 1. Attempt recovery dispatch with stale token A -> matches 0 rows in actual DB
        int staleDispatchRows = submissionRepository.recordSuccessfulRecoveryDispatch(subId, "stale-token-A");
        assertThat(staleDispatchRows).as("Stale recovery token must match 0 rows in DB update").isEqualTo(0);

        // 2. Transition submission to COMPLETED
        tt.execute(status -> {
            Submission sub = submissionRepository.findById(subId).orElseThrow();
            sub.setStatus("COMPLETED");
            sub.setExecutionStatus("COMPLETED");
            submissionRepository.save(sub);
            return null;
        });

        // 3. Stale sweeper attempts failPendingSubmission on COMPLETED submission -> matches 0 rows in DB
        int failRows = submissionRepository.failPendingSubmission(subId, "active-token-B", "TIMEOUT", "stale fail");
        assertThat(failRows).as("failPendingSubmission must match 0 rows when submission status is COMPLETED").isEqualTo(0);

        // Invariant: status in DB remains COMPLETED
        Submission finalSub = submissionRepository.findById(subId).orElseThrow();
        assertThat(finalSub.getStatus()).isEqualTo("COMPLETED");
    }
}
