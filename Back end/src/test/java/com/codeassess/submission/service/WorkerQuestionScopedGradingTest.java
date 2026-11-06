package com.codeassess.submission.service;

import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.dto.response.InternalGradingResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchItem;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchSuiteResult;
import com.codeassess.testcase.dto.sandbox.TestCaseExecutionItemResult;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.SubmissionTestResultRepository;
import com.codeassess.testcase.repository.TestCaseRepository;
import com.codeassess.testcase.service.AutomatedTestRunnerService;
import com.codeassess.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkerQuestionScopedGradingTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private DockerSandboxService dockerSandboxService;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @Mock
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Mock
    private ExamQuestionRepository examQuestionRepository;

    @Mock
    private AstPlagiarismService astPlagiarismService;

    @Mock
    private SimilarityMatrixService similarityMatrixService;

    private AutomatedTestRunnerService automatedTestRunnerService;
    private InternalGradingService internalGradingService;

    private Exam testExam;
    private ExamQuestion testQuestion1;
    private User testStudent;
    private Submission testSubmission;
    private TestCase testCaseQ1;

    @BeforeEach
    void setUp() {
        automatedTestRunnerService = new AutomatedTestRunnerService(
                dockerSandboxService,
                testCaseRepository,
                submissionTestResultRepository,
                submissionRepository,
                null,
                studentQuestionScoreRepository
        );

        internalGradingService = new InternalGradingService(
                submissionRepository,
                dockerSandboxService,
                astPlagiarismService,
                similarityMatrixService,
                automatedTestRunnerService,
                studentQuestionScoreRepository
        );

        testExam = Exam.builder()
                .id("exam-mq-1")
                .title("Multi-Question Exam")
                .build();

        testQuestion1 = ExamQuestion.builder()
                .id("q-algo-1")
                .exam(testExam)
                .title("Algorithm 1")
                .displayOrder(1)
                .build();

        testStudent = User.builder()
                .id("student-42")
                .email("student42@test.com")
                .fullName("Ada Lovelace")
                .build();

        testSubmission = Submission.builder()
                .id("sub-work-100")
                .exam(testExam)
                .question(testQuestion1)
                .student(testStudent)
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .sourceCode("public class Solution { public static void main(String[] args) {} }")
                .build();

        testCaseQ1 = TestCase.builder()
                .id("tc-q1-1")
                .exam(testExam)
                .question(testQuestion1)
                .name("Sample Test 1")
                .points(BigDecimal.valueOf(10.00))
                .expectedOutput("Hello World")
                .build();
    }

    @Test
    @DisplayName("Invariant 1: Successful grading updates question-scoped test cases and persists student_question_scores")
    void testSuccessfulGrading_UpdatesCorrectQuestionScore() {
        when(submissionRepository.findById("sub-work-100")).thenReturn(Optional.of(testSubmission));
        when(submissionRepository.claimSubmissionWithToken(eq("sub-work-100"), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        // Verify question-specific test cases are loaded
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-algo-1"))
                .thenReturn(List.of(testCaseQ1));

        TestCaseExecutionItemResult itemResult = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-q1-1")
                .status("PASSED")
                .pointsAwarded(BigDecimal.valueOf(10.00))
                .build();

        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .compileOutput("Compilation successful")
                .itemResults(List.of(itemResult))
                .totalDurationMs(150L)
                .build();

        when(dockerSandboxService.executeTestCaseSuite(anyString(), anyList())).thenReturn(suiteResult);
        when(submissionRepository.completeGradingWithToken(
                eq("sub-work-100"), anyString(), eq("SUCCESS"), eq(0), any(), any(), any(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);

        when(submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc("exam-mq-1", "student-42"))
                .thenReturn(Optional.of(testSubmission));

        when(studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                eq("q-algo-1"), eq("student-42"), eq("sub-work-100"), eq(BigDecimal.valueOf(10.00).setScale(2))
        )).thenReturn(1);

        InternalGradingResponse response = internalGradingService.processGrading("sub-work-100");

        assertThat(response.getStatus()).isEqualTo("COMPLETED");

        // Verify question score was updated with the exact questionId and submissionId
        verify(studentQuestionScoreRepository).completeWorkerGradingIfLatest(
                eq("q-algo-1"), eq("student-42"), eq("sub-work-100"), eq(BigDecimal.valueOf(10.00).setScale(2))
        );
    }

    @Test
    @DisplayName("Invariant 2: Stale claim token cannot persist submission results or mutate question scores")
    void testStaleClaimToken_CannotUpdateSubmissionOrScoreData() {
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-algo-1"))
                .thenReturn(List.of(testCaseQ1));

        TestCaseExecutionItemResult itemResult = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-q1-1")
                .status("PASSED")
                .pointsAwarded(BigDecimal.valueOf(10.00))
                .build();

        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .itemResults(List.of(itemResult))
                .totalDurationMs(200L)
                .build();

        when(dockerSandboxService.executeTestCaseSuite(anyString(), anyList())).thenReturn(suiteResult);

        // Stale worker lease has expired or was superseded: completeGradingWithToken returns 0
        when(submissionRepository.completeGradingWithToken(
                eq("sub-work-100"), eq("stale-token-1"), anyString(), anyInt(), any(), any(), any(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(0);

        TestCaseBatchSuiteResult result = automatedTestRunnerService.runAndGrade(testSubmission, "stale-token-1");

        assertThat(result.isFencedOut()).isTrue();

        // Stale worker must NOT write test results or student question scores
        verify(submissionTestResultRepository, never()).deleteBySubmission_Id(anyString());
        verify(submissionTestResultRepository, never()).saveAll(anyList());
        verify(studentQuestionScoreRepository, never()).completeWorkerGradingIfLatest(anyString(), anyString(), anyString(), any());
        verify(submissionRepository, never()).save(testSubmission);
    }

    @Test
    @DisplayName("Invariant 3: Missing claim token is rejected without updating worker-owned data")
    void testMissingClaimToken_CannotUpdateWorkerOwnedData() {
        InternalGradingResponse responseNull = internalGradingService.processGradingWithToken("sub-work-100", null);
        assertThat(responseNull.getStatus()).isEqualTo("INVALID_TOKEN");

        InternalGradingResponse responseBlank = internalGradingService.processGradingWithToken("sub-work-100", "   ");
        assertThat(responseBlank.getStatus()).isEqualTo("INVALID_TOKEN");

        // Verify zero interactions with database writes
        verifyNoInteractions(dockerSandboxService);
        verifyNoInteractions(studentQuestionScoreRepository);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Invariant 4: An older submission cannot overwrite a newer submission's question score")
    void testOlderSubmission_CannotOverwriteNewerSubmissionScore() {
        // Submission 1 (older) completes grading after Submission 2 was already registered
        // completeWorkerGradingIfLatest returns 0 because latestSubmissionId is now sub-new-200
        when(studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                eq("q-algo-1"), eq("student-42"), eq("sub-old-100"), any(BigDecimal.class)
        )).thenReturn(0);

        int rows = studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                "q-algo-1", "student-42", "sub-old-100", BigDecimal.valueOf(8.00)
        );

        assertThat(rows).isEqualTo(0);
        verify(studentQuestionScoreRepository).completeWorkerGradingIfLatest(
                "q-algo-1", "student-42", "sub-old-100", BigDecimal.valueOf(8.00)
        );
    }

    @Test
    @DisplayName("Invariant 5: Duplicate BullMQ job execution is idempotent and does not re-execute grading or mutate scores")
    void testDuplicateExecution_Idempotent_DoesNotMutateScoreOrReexecute() {
        Submission completedSubmission = Submission.builder()
                .id("sub-completed-1")
                .exam(testExam)
                .question(testQuestion1)
                .student(testStudent)
                .status("COMPLETED")
                .executionStatus("SUCCESS")
                .autoScore(BigDecimal.valueOf(10.00))
                .build();

        when(submissionRepository.findById("sub-completed-1")).thenReturn(Optional.of(completedSubmission));

        InternalGradingResponse response = internalGradingService.processGrading("sub-completed-1");

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getMessage()).isEqualTo("Already processed");

        // Verify no new claim was taken and no sandbox or score writes occurred
        verify(submissionRepository, never()).claimSubmissionWithToken(anyString(), anyString(), any());
        verifyNoInteractions(dockerSandboxService);
        verifyNoInteractions(studentQuestionScoreRepository);
    }

    @Test
    @DisplayName("Invariant 6: Transient sandbox infrastructure failure safely releases claim for BullMQ retry")
    void testTransientFailure_ReleasesClaimForRetry() {
        when(submissionRepository.findById("sub-work-100")).thenReturn(Optional.of(testSubmission));
        when(submissionRepository.claimSubmissionWithToken(eq("sub-work-100"), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-algo-1"))
                .thenReturn(List.of(testCaseQ1));

        when(dockerSandboxService.executeTestCaseSuite(anyString(), anyList()))
                .thenThrow(new SandboxException("Docker daemon socket closed unexpectedly"));

        assertThatThrownBy(() -> internalGradingService.processGrading("sub-work-100"))
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("Docker daemon socket closed");

        // Fenced claim release must be invoked to permit BullMQ retry with fresh claim token
        verify(submissionRepository).releaseExpiredClaim(eq("sub-work-100"), anyString());
        // StudentQuestionScore must NOT be set to FAILED on transient retryable infrastructure error
        verifyNoInteractions(studentQuestionScoreRepository);
    }

    @Test
    @DisplayName("Invariant 7: Terminal worker failure updates submission to FAILED and syncs StudentQuestionScore")
    void testTerminalFailure_UpdatesSubmissionAndQuestionScoreStatus() {
        when(submissionRepository.findById("sub-work-100")).thenReturn(Optional.of(testSubmission));
        when(submissionRepository.claimSubmissionWithToken(eq("sub-work-100"), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        // Simulate terminal, unrecoverable exception during grading
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-algo-1"))
                .thenThrow(new IllegalStateException("Unrecoverable workspace corruption"));

        when(submissionRepository.failProcessingSubmissionWithToken(
                eq("sub-work-100"), anyString(), eq("INTERNAL_ERROR"), contains("Unrecoverable workspace corruption")
        )).thenReturn(1);

        when(studentQuestionScoreRepository.updateFailureStatusIfLatest(
                eq("q-algo-1"), eq("student-42"), eq("sub-work-100"), eq("FAILED")
        )).thenReturn(1);

        assertThatThrownBy(() -> internalGradingService.processGrading("sub-work-100"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unrecoverable workspace corruption");

        // Fenced terminal failure transition verified
        verify(submissionRepository).failProcessingSubmissionWithToken(
                eq("sub-work-100"), anyString(), eq("INTERNAL_ERROR"), anyString()
        );
        // StudentQuestionScore synced to FAILED if latest
        verify(studentQuestionScoreRepository).updateFailureStatusIfLatest(
                eq("q-algo-1"), eq("student-42"), eq("sub-work-100"), eq("FAILED")
        );
    }

    @Test
    @DisplayName("Invariant 8: Single-question legacy exams fallback to exam-level test cases and remain compatible")
    void testSingleQuestionLegacyCompatibility_FallsBackToExamTestCases() {
        Submission legacySub = Submission.builder()
                .id("sub-legacy-1")
                .exam(testExam)
                .question(null) // Legacy call with null question
                .student(testStudent)
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .sourceCode("public class Main { public static void main(String[] args) {} }")
                .build();

        TestCase legacyTestCase = TestCase.builder()
                .id("tc-legacy-1")
                .exam(testExam)
                .question(null)
                .name("Legacy Exam Test")
                .points(BigDecimal.valueOf(10.00))
                .expectedOutput("Pass")
                .build();

        // Fallback to exam test cases
        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-mq-1"))
                .thenReturn(List.of(legacyTestCase));

        when(submissionRepository.completeGradingWithToken(
                eq("sub-legacy-1"), eq("legacy-token-1"), anyString(), anyInt(), any(), any(), any(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);

        TestCaseExecutionItemResult itemResult = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-legacy-1")
                .status("PASSED")
                .pointsAwarded(BigDecimal.valueOf(10.00))
                .build();

        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .itemResults(List.of(itemResult))
                .totalDurationMs(100L)
                .build();

        when(dockerSandboxService.executeTestCaseSuite(anyString(), anyList())).thenReturn(suiteResult);
        when(submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc("exam-mq-1", "student-42"))
                .thenReturn(Optional.of(legacySub));

        TestCaseBatchSuiteResult result = automatedTestRunnerService.runAndGrade(legacySub, "legacy-token-1");

        assertThat(result.isCompiled()).isTrue();
        assertThat(legacySub.getAutoScore()).isEqualTo(BigDecimal.valueOf(10.00).setScale(2));
        assertThat(legacySub.getStatus()).isEqualTo("COMPLETED");
        verify(submissionRepository).save(legacySub);
    }

    @Test
    @DisplayName("Question Isolation 1: Submission for Question A executes only Question A's test cases, not Question B's")
    void testQuestionIsolation_QuestionAExecutesOnlyQuestionACases() {
        ExamQuestion questionA = ExamQuestion.builder().id("q-A").exam(testExam).title("QA").build();
        ExamQuestion questionB = ExamQuestion.builder().id("q-B").exam(testExam).title("QB").build();

        Submission subA = Submission.builder()
                .id("sub-A-1")
                .exam(testExam)
                .question(questionA)
                .student(testStudent)
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .sourceCode("public class QA {}")
                .build();

        TestCase tcA = TestCase.builder().id("tc-A-1").exam(testExam).question(questionA).points(BigDecimal.valueOf(10)).build();
        TestCase tcB = TestCase.builder().id("tc-B-1").exam(testExam).question(questionB).points(BigDecimal.valueOf(10)).build();

        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-A"))
                .thenReturn(List.of(tcA));

        when(submissionRepository.completeGradingWithToken(
                eq("sub-A-1"), eq("tok-A"), anyString(), anyInt(), any(), any(), any(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);

        TestCaseExecutionItemResult itemResult = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-A-1")
                .status("PASSED")
                .pointsAwarded(BigDecimal.valueOf(10.00))
                .build();
        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .itemResults(List.of(itemResult))
                .build();

        when(dockerSandboxService.executeTestCaseSuite(anyString(), anyList())).thenReturn(suiteResult);
        when(submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc("exam-mq-1", "student-42"))
                .thenReturn(Optional.of(subA));

        automatedTestRunnerService.runAndGrade(subA, "tok-A");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestCaseBatchItem>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(dockerSandboxService).executeTestCaseSuite(eq("public class QA {}"), batchCaptor.capture());

        List<TestCaseBatchItem> capturedItems = batchCaptor.getValue();
        assertThat(capturedItems).hasSize(1);
        assertThat(capturedItems.get(0).getId()).isEqualTo("tc-A-1");

        // Verify Question B test cases were never fetched or executed
        verify(testCaseRepository, never()).findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-B");
        verify(testCaseRepository, never()).findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(anyString());
    }

    @Test
    @DisplayName("Question Isolation 2: Question A with no test cases does not execute Question B cases or fallback to exam cases")
    void testQuestionIsolation_QuestionAWithZeroCases_DoesNotExecuteQuestionBCases() {
        ExamQuestion questionA = ExamQuestion.builder().id("q-A-empty").exam(testExam).title("QA Empty").build();
        ExamQuestion questionB = ExamQuestion.builder().id("q-B-full").exam(testExam).title("QB Full").build();

        Submission subA = Submission.builder()
                .id("sub-A-empty")
                .exam(testExam)
                .question(questionA)
                .student(testStudent)
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .sourceCode("public class EmptyQA {}")
                .build();

        TestCase tcB = TestCase.builder().id("tc-B-1").exam(testExam).question(questionB).points(BigDecimal.valueOf(10)).build();

        // Question A returns empty list; Question B has tcB
        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-A-empty"))
                .thenReturn(Collections.emptyList());

        // Zero test cases uses compile-and-run check via execute
        when(dockerSandboxService.execute(eq("public class EmptyQA {}")))
                .thenReturn(SandboxResult.builder()
                        .status(SandboxResult.Status.SUCCESS)
                        .exitCode(0)
                        .compilerOutput("Compiled")
                        .executionTimeMs(50L)
                        .build());

        when(submissionRepository.completeGradingWithToken(
                eq("sub-A-empty"), eq("tok-A-empty"), eq("SUCCESS"), eq(0), any(), any(), any(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);

        TestCaseBatchSuiteResult result = automatedTestRunnerService.runAndGrade(subA, "tok-A-empty");

        assertThat(result.isCompiled()).isTrue();
        assertThat(result.getItemResults()).isEmpty();

        // Critical: Verify findByExam_Id was NEVER called and executeTestCaseSuite was NEVER called
        verify(testCaseRepository, never()).findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(anyString());
        verify(dockerSandboxService, never()).executeTestCaseSuite(anyString(), anyList());
        // Verify zero testcases state
        assertThat(subA.getStatus()).isEqualTo("COMPLETED");
        assertThat(subA.getAutoScore()).isNull();
        assertThat(subA.getTestCasesPassed()).isEqualTo(0);
        assertThat(subA.getTestCasesTotal()).isEqualTo(0);
    }

    @Test
    @DisplayName("Question Isolation 4: Empty question suite follows explicitly defined zero-case behavior and updates StudentQuestionScore")
    void testEmptyQuestionSuite_FollowsDefinedEmptySuiteBehavior() {
        ExamQuestion emptyQuestion = ExamQuestion.builder().id("q-empty").exam(testExam).title("Empty Q").build();
        Submission emptySub = Submission.builder()
                .id("sub-empty-1")
                .exam(testExam)
                .question(emptyQuestion)
                .student(testStudent)
                .status("PENDING")
                .submissionType("SINGLE_FILE")
                .sourceCode("public class Solution {}")
                .build();

        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-empty"))
                .thenReturn(Collections.emptyList());

        when(dockerSandboxService.execute(eq("public class Solution {}")))
                .thenReturn(SandboxResult.builder()
                        .status(SandboxResult.Status.SUCCESS)
                        .exitCode(0)
                        .stdout("Run OK")
                        .executionTimeMs(40L)
                        .build());

        when(submissionRepository.completeGradingWithToken(
                eq("sub-empty-1"), eq("empty-token"), eq("SUCCESS"), eq(0), any(), any(), any(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);

        when(studentQuestionScoreRepository.completeWorkerGradingIfLatest(
                eq("q-empty"), eq("student-42"), eq("sub-empty-1"), eq(BigDecimal.ZERO.setScale(2))
        )).thenReturn(1);

        TestCaseBatchSuiteResult result = automatedTestRunnerService.runAndGrade(emptySub, "empty-token");

        assertThat(result.isCompiled()).isTrue();
        assertThat(result.isFencedOut()).isFalse();
        assertThat(emptySub.getStatus()).isEqualTo("COMPLETED");
        assertThat(emptySub.getAutoScore()).isNull();
        assertThat(emptySub.getTestCasesPassed()).isEqualTo(0);
        assertThat(emptySub.getTestCasesTotal()).isEqualTo(0);

        // StudentQuestionScore synced with 0.00 auto-score
        verify(studentQuestionScoreRepository).completeWorkerGradingIfLatest(
                eq("q-empty"), eq("student-42"), eq("sub-empty-1"), eq(BigDecimal.ZERO.setScale(2))
        );
        // No testcase results saved
        verifyNoInteractions(submissionTestResultRepository);
    }

    @Test
    @DisplayName("Write Atomicity: Worker arriving for already COMPLETED submission cannot update question scores or persist results")
    void testWorkerFencedOutBySubmissionCompletion_CannotUpdateQuestionScores() {
        Submission completedSub = Submission.builder()
                .id("sub-already-done")
                .exam(testExam)
                .question(testQuestion1)
                .student(testStudent)
                .status("COMPLETED")
                .executionStatus("SUCCESS")
                .autoScore(BigDecimal.valueOf(10.00))
                .build();

        when(submissionRepository.findById("sub-already-done")).thenReturn(Optional.of(completedSub));

        InternalGradingResponse response = internalGradingService.processGradingWithToken("sub-already-done", "worker-token-late");

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getMessage()).isEqualTo("Already processed");

        // Proves that when already completed, neither test results nor scores can be touched
        verifyNoInteractions(testCaseRepository);
        verifyNoInteractions(dockerSandboxService);
        verifyNoInteractions(submissionTestResultRepository);
        verifyNoInteractions(studentQuestionScoreRepository);
    }

    @Test
    @DisplayName("Write Atomicity: Transaction rollback on partial persistence failure leaves no inconsistent state")
    void testPartialFailure_RollsBackTransaction_LeavesNothingInconsistent() {
        PlatformTransactionManager mockTxManager = mock(PlatformTransactionManager.class);
        TransactionStatus mockStatus = mock(TransactionStatus.class);

        when(mockTxManager.getTransaction(any())).thenReturn(mockStatus);

        AutomatedTestRunnerService runnerWithTx = new AutomatedTestRunnerService(
                dockerSandboxService,
                testCaseRepository,
                submissionTestResultRepository,
                submissionRepository,
                mockTxManager,
                studentQuestionScoreRepository
        );

        when(testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc("q-algo-1"))
                .thenReturn(List.of(testCaseQ1));

        TestCaseExecutionItemResult itemResult = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-q1-1")
                .status("PASSED")
                .pointsAwarded(BigDecimal.valueOf(10.00))
                .build();
        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .itemResults(List.of(itemResult))
                .totalDurationMs(100L)
                .build();
        when(dockerSandboxService.executeTestCaseSuite(anyString(), anyList())).thenReturn(suiteResult);

        when(submissionRepository.completeGradingWithToken(
                eq("sub-work-100"), eq("tx-token"), anyString(), anyInt(), any(), any(), any(), anyLong(), any(LocalDateTime.class)
        )).thenReturn(1);

        when(submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc("exam-mq-1", "student-42"))
                .thenReturn(Optional.of(testSubmission));

        // Simulate database constraint failure during score update
        doThrow(new DataIntegrityViolationException("Simulated score constraint failure"))
                .when(studentQuestionScoreRepository)
                .completeWorkerGradingIfLatest(anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> runnerWithTx.runAndGrade(testSubmission, "tx-token"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("Simulated score constraint failure");

        // Verify that transaction manager rollback was invoked
        verify(mockTxManager).rollback(mockStatus);
        verify(mockTxManager, never()).commit(mockStatus);
    }
}
