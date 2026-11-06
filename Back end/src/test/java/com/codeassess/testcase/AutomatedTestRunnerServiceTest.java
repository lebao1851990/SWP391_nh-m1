package com.codeassess.testcase;

import com.codeassess.exam.entity.Exam;
import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.sandbox.dto.response.SandboxResult;
import com.codeassess.sandbox.exception.SandboxException;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.submission.dto.EntryPointResult;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.EntryPointDiscoveryService;
import com.codeassess.submission.service.SubmissionStorageService;
import com.codeassess.submission.service.ZipExtractionService;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchSuiteResult;
import com.codeassess.testcase.dto.sandbox.TestCaseExecutionItemResult;
import com.codeassess.testcase.entity.SubmissionTestResult;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutomatedTestRunnerService Unit Tests")
class AutomatedTestRunnerServiceTest {

        @Mock
        private DockerSandboxService dockerSandboxService;

        @Mock
        private TestCaseRepository testCaseRepository;

        @Mock
        private SubmissionTestResultRepository submissionTestResultRepository;

        @Mock
        private SubmissionRepository submissionRepository;

        @Mock
        private SubmissionStorageService submissionStorageService;

        @Mock
        private ZipExtractionService zipExtractionService;

        @Mock
        private EntryPointDiscoveryService entryPointDiscoveryService;

        @Mock
        private AstPlagiarismService astPlagiarismService;

        @Mock
        private SimilarityMatrixService similarityMatrixService;

        @Mock
        private GradeAuditLogRepository gradeAuditLogRepository;

        private AutomatedTestRunnerService runnerService;

        private Exam exam;
        private Submission submission;

        @BeforeEach
        void setUp() {
                runnerService = new AutomatedTestRunnerService(
                                dockerSandboxService,
                                testCaseRepository,
                                submissionTestResultRepository,
                                submissionRepository);

                exam = new Exam();
                exam.setId("exam-1");
                exam.setTitle("Java Final Exam");

                submission = new Submission();
                submission.setId("sub-100");
                submission.setExam(exam);
                submission.setStudent(User.builder().id("student-1").fullName("Student One").build());
                submission.setSourceCode("public class Main { public static void main(String[] args) {} }");
                submission.setStatus("PENDING");
                submission.setExecutionStatus("PENDING");
        }

        @Test
        @DisplayName("When exam has no test cases, submission is marked COMPLETED with null auto_score")
        void testExamWithNoTestCases() {
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(Collections.emptyList());
                when(dockerSandboxService.execute(submission.getSourceCode()))
                                .thenReturn(SandboxResult.builder()
                                                .status(SandboxResult.Status.SUCCESS)
                                                .stdout("Run OK")
                                                .compilerOutput("Compile OK")
                                                .executionTimeMs(120L)
                                                .build());

                runnerService.runAndGrade(submission);

                verify(dockerSandboxService, never()).executeTestCaseSuite(any(), any());
                assertEquals("COMPLETED", submission.getStatus());
                assertNull(submission.getAutoScore());
                assertEquals(0, submission.getTestCasesPassed());
                assertEquals(0, submission.getTestCasesTotal());
                verify(submissionRepository).save(submission);
        }

        @Test
        @DisplayName("When compilation fails, all test cases marked COMPILE_ERROR and score is 0")
        void testCompilationFailure() {
                TestCase tc1 = new TestCase();
                tc1.setId("tc-1");
                tc1.setName("Test 1");
                tc1.setPoints(BigDecimal.valueOf(10));
                tc1.setExpectedOutput("Output 1");

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc1));

                TestCaseBatchSuiteResult compileFailResult = TestCaseBatchSuiteResult.builder()
                                .compiled(false)
                                .compileOutput("Compilation error at line 1")
                                .itemResults(Collections.emptyList())
                                .build();
                when(dockerSandboxService.executeTestCaseSuite(eq(submission.getSourceCode()), anyList()))
                                .thenReturn(compileFailResult);

                runnerService.runAndGrade(submission);

                assertEquals("COMPILE_ERROR", submission.getExecutionStatus());
                assertEquals(BigDecimal.ZERO.setScale(2), submission.getAutoScore());
                assertEquals(0, submission.getTestCasesPassed());
                assertEquals(1, submission.getTestCasesTotal());

                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
                verify(submissionTestResultRepository).saveAll(captor.capture());
                List<SubmissionTestResult> saved = captor.getValue();
                assertEquals(0, saved.size());
        }

        @Test
        @DisplayName("When all test cases pass with different weights, calculates 10.00 score and SUCCESS status")
        void testAllTestCasesPass() {
                TestCase tc1 = new TestCase();
                tc1.setId("tc-1");
                tc1.setName("Test 1");
                tc1.setPoints(BigDecimal.valueOf(30));
                tc1.setInputData("1 2");
                tc1.setExpectedOutput("3");

                TestCase tc2 = new TestCase();
                tc2.setId("tc-2");
                tc2.setName("Test 2");
                tc2.setPoints(BigDecimal.valueOf(70));
                tc2.setInputData("10 20");
                tc2.setExpectedOutput("30");

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc1, tc2));

                TestCaseExecutionItemResult res1 = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .testCaseName("Test 1")
                                .status("PASSED")
                                .actualOutput("3\n")
                                .expectedOutput("3")
                                .pointsAwarded(BigDecimal.valueOf(30))
                                .maxPoints(BigDecimal.valueOf(30))
                                .exitCode(0)
                                .executionTimeMs(50L)
                                .build();

                TestCaseExecutionItemResult res2 = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-2")
                                .testCaseName("Test 2")
                                .status("PASSED")
                                .actualOutput("30\r\n")
                                .expectedOutput("30")
                                .pointsAwarded(BigDecimal.valueOf(70))
                                .maxPoints(BigDecimal.valueOf(70))
                                .exitCode(0)
                                .executionTimeMs(60L)
                                .build();

                TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .compileOutput("OK")
                                .itemResults(List.of(res1, res2))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(eq(submission.getSourceCode()), anyList()))
                                .thenReturn(suiteResult);

                runnerService.runAndGrade(submission);

                assertEquals("SUCCESS", submission.getExecutionStatus());
                assertEquals(new BigDecimal("10.00"), submission.getAutoScore());
                assertEquals(new BigDecimal("10.00"), submission.getTotalScore());
                assertEquals(2, submission.getTestCasesPassed());
                assertEquals(2, submission.getTestCasesTotal());
        }

        @Test
        @DisplayName("Partial pass calculates proportional weighted auto_score on 10-point scale and PARTIAL_SUCCESS status")
        void testPartialPassWeightCalculation() {
                TestCase tc1 = new TestCase();
                tc1.setId("tc-1");
                tc1.setName("Easy Test");
                tc1.setPoints(BigDecimal.valueOf(20));
                tc1.setExpectedOutput("Pass");

                TestCase tc2 = new TestCase();
                tc2.setId("tc-2");
                tc2.setName("Hard Test");
                tc2.setPoints(BigDecimal.valueOf(80));
                tc2.setExpectedOutput("Complex Output");

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc1, tc2));

                TestCaseExecutionItemResult res1 = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .testCaseName("Easy Test")
                                .status("PASSED")
                                .actualOutput("Pass\n")
                                .expectedOutput("Pass")
                                .pointsAwarded(BigDecimal.valueOf(20))
                                .maxPoints(BigDecimal.valueOf(20))
                                .exitCode(0)
                                .build();

                TestCaseExecutionItemResult res2 = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-2")
                                .testCaseName("Hard Test")
                                .status("FAILED")
                                .actualOutput("Wrong Output\n")
                                .expectedOutput("Complex Output")
                                .pointsAwarded(BigDecimal.ZERO)
                                .maxPoints(BigDecimal.valueOf(80))
                                .exitCode(0)
                                .build();

                TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .compileOutput("OK")
                                .itemResults(List.of(res1, res2))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList()))
                                .thenReturn(suiteResult);

                runnerService.runAndGrade(submission);

                assertEquals("PARTIAL_SUCCESS", submission.getExecutionStatus());
                assertEquals(new BigDecimal("2.00"), submission.getAutoScore());
                assertEquals(1, submission.getTestCasesPassed());
                assertEquals(2, submission.getTestCasesTotal());
        }

        @Test
        @DisplayName("Timeout and OOM (exitCode 124 and 137) retain intended student-code grading behavior")
        void testTimeoutAndOomHandling() {
                TestCase tc1 = new TestCase();
                tc1.setId("tc-1");
                tc1.setName("Infinite Loop Test");
                tc1.setPoints(BigDecimal.valueOf(50));
                tc1.setExpectedOutput("Done");

                TestCase tc2 = new TestCase();
                tc2.setId("tc-2");
                tc2.setName("OOM Test");
                tc2.setPoints(BigDecimal.valueOf(50));
                tc2.setExpectedOutput("Done");

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc1, tc2));

                TestCaseExecutionItemResult res1 = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .testCaseName("Infinite Loop Test")
                                .status("TIMEOUT")
                                .actualOutput("")
                                .expectedOutput("Done")
                                .exitCode(124)
                                .pointsAwarded(BigDecimal.ZERO)
                                .maxPoints(BigDecimal.valueOf(50))
                                .executionTimeMs(5000L)
                                .build();

                TestCaseExecutionItemResult res2 = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-2")
                                .testCaseName("OOM Test")
                                .status("TIMEOUT")
                                .actualOutput("")
                                .expectedOutput("Done")
                                .exitCode(137)
                                .pointsAwarded(BigDecimal.ZERO)
                                .maxPoints(BigDecimal.valueOf(50))
                                .executionTimeMs(1200L)
                                .build();

                TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(res1, res2))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList()))
                                .thenReturn(suiteResult);

                runnerService.runAndGrade(submission);

                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
                verify(submissionTestResultRepository).saveAll(captor.capture());
                List<SubmissionTestResult> saved = captor.getValue();
                assertEquals(2, saved.size());
                assertEquals("TIMEOUT", saved.get(0).getStatus());
                assertEquals(124, saved.get(0).getExitCode());
                assertEquals(BigDecimal.ZERO, saved.get(0).getPointsAwarded());
                assertEquals("TIMEOUT", saved.get(1).getStatus());
                assertEquals(137, saved.get(1).getExitCode());
                assertEquals(BigDecimal.ZERO, saved.get(1).getPointsAwarded());
                assertEquals("COMPLETED", submission.getStatus());
                assertEquals("FAILED", submission.getExecutionStatus());
                assertEquals(0, submission.getTestCasesPassed());
                assertEquals(new BigDecimal("0.00"), submission.getAutoScore());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // FIX-01 & FIX-02 Targeted Verification Tests
        // ─────────────────────────────────────────────────────────────────────────

        @Test
        @DisplayName("Throws IllegalArgumentException when submission is null")
        void runAndGrade_NullSubmission_ThrowsIllegalArgumentException() {
                assertThrows(IllegalArgumentException.class, () -> runnerService.runAndGrade(null));
        }

        @Test
        @DisplayName("Throws IllegalStateException when submission is not linked to any exam")
        void runAndGrade_SubmissionWithoutExam_ThrowsIllegalStateException() {
                submission.setExam(null);
                assertThrows(IllegalStateException.class, () -> runnerService.runAndGrade(submission));
        }

        @Test
        @DisplayName("Throws IllegalStateException when exam ID is null")
        void runAndGrade_ExamWithoutId_ThrowsIllegalStateException() {
                exam.setId(null);
                assertThrows(IllegalStateException.class, () -> runnerService.runAndGrade(submission));
        }

        @Test
        @DisplayName("FIX-01: When exam has no test cases and Docker is unavailable, throws SandboxException without persisting COMPLETED or zero score")
        void runAndGrade_NoTestCases_DockerUnavailable_ThrowsSandboxExceptionWithoutMutatingScore() {
                submission.setTotalScore(new BigDecimal("8.00"));
                submission.setOverrideScore(new BigDecimal("9.00"));

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(Collections.emptyList());
                when(dockerSandboxService.execute(submission.getSourceCode()))
                                .thenThrow(new SandboxException("Docker daemon connection failed"));

                assertThrows(SandboxException.class, () -> runnerService.runAndGrade(submission));

                assertEquals("PENDING", submission.getStatus());
                assertEquals(new BigDecimal("8.00"), submission.getTotalScore());
                assertEquals(new BigDecimal("9.00"), submission.getOverrideScore());
                verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FIX-01: When exam has no test cases and submission is ZIP, extracts workspace and triggers AST plagiarism")
        void runAndGrade_NoTestCases_ZipSubmission_ExtractsAndExecutesWorkspace() throws Exception {
                submission.setSubmissionType("ZIP");
                ReflectionTestUtils.setField(runnerService, "submissionStorageService", submissionStorageService);
                ReflectionTestUtils.setField(runnerService, "zipExtractionService", zipExtractionService);
                ReflectionTestUtils.setField(runnerService, "entryPointDiscoveryService", entryPointDiscoveryService);
                ReflectionTestUtils.setField(runnerService, "astPlagiarismService", astPlagiarismService);

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(Collections.emptyList());
                when(submissionStorageService.hasZip("sub-100")).thenReturn(true);
                when(submissionStorageService.getZipBytes("sub-100")).thenReturn(new byte[] { 1, 2, 3 });

                ExtractedWorkspace mockWorkspace = new ExtractedWorkspace(null, Collections.emptyList(), 10L, "hash");
                when(zipExtractionService.extract(any(byte[].class))).thenReturn(mockWorkspace);
                when(entryPointDiscoveryService.discoverEntryPoint(mockWorkspace))
                                .thenReturn(EntryPointResult.found("com.example.Main", List.of("com.example.Main")));
                when(dockerSandboxService.executeWorkspace(mockWorkspace, "com.example.Main"))
                                .thenReturn(SandboxResult.builder()
                                                .status(SandboxResult.Status.SUCCESS)
                                                .stdout("ZIP Output OK")
                                                .compilerOutput("ZIP Compile OK")
                                                .executionTimeMs(150L)
                                                .build());

                runnerService.runAndGrade(submission);

                assertEquals("SUCCESS", submission.getExecutionStatus());
                assertEquals("COMPLETED", submission.getStatus());
                verify(astPlagiarismService).generateFingerprintsForWorkspace(submission, mockWorkspace);
        }

        @Test
        @DisplayName("FIX-01: When exam has no test cases and ZIP extraction fails, throws SandboxException without marking COMPLETED")
        void runAndGrade_NoTestCases_ZipExtractionFails_ThrowsSandboxException() {
                submission.setSubmissionType("ZIP");
                ReflectionTestUtils.setField(runnerService, "submissionStorageService", submissionStorageService);
                ReflectionTestUtils.setField(runnerService, "zipExtractionService", zipExtractionService);

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(Collections.emptyList());
                when(submissionStorageService.hasZip("sub-100")).thenReturn(true);
                when(submissionStorageService.getZipBytes("sub-100")).thenReturn(new byte[] { 1, 2, 3 });
                when(zipExtractionService.extract(any(byte[].class)))
                                .thenThrow(new RuntimeException("Corrupted zip archive header"));

                assertThrows(SandboxException.class, () -> runnerService.runAndGrade(submission));
                assertEquals("PENDING", submission.getStatus());
                verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("When exam has no test cases and similarityMatrixService is configured, requests matrix refresh")
        void runAndGrade_NoTestCases_WithSimilarityMatrixService_TriggersRefresh() {
                ReflectionTestUtils.setField(runnerService, "similarityMatrixService", similarityMatrixService);

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(Collections.emptyList());
                when(dockerSandboxService.execute(submission.getSourceCode()))
                                .thenReturn(SandboxResult.builder()
                                                .status(SandboxResult.Status.SUCCESS)
                                                .executionTimeMs(50L)
                                                .build());

                runnerService.runAndGrade(submission);

                verify(similarityMatrixService).requestRefresh("exam-1");
                verify(similarityMatrixService).generateMatrixForExam("exam-1");
        }

        @Test
        @DisplayName("When similarityMatrixService throws exception on refresh, handled gracefully without failing grading")
        void runAndGrade_NoTestCases_SimilarityMatrixThrowsException_HandledGracefully() {
                ReflectionTestUtils.setField(runnerService, "similarityMatrixService", similarityMatrixService);

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(Collections.emptyList());
                when(dockerSandboxService.execute(submission.getSourceCode()))
                                .thenReturn(SandboxResult.builder()
                                                .status(SandboxResult.Status.SUCCESS)
                                                .executionTimeMs(50L)
                                                .build());
                doThrow(new RuntimeException("Redis connection refused"))
                                .when(similarityMatrixService).requestRefresh("exam-1");

                assertDoesNotThrow(() -> runnerService.runAndGrade(submission));
                assertEquals("COMPLETED", submission.getStatus());
        }

        @Test
        @DisplayName("When transactionTemplate is present, saves submission inside transaction template")
        void runAndGrade_NoTestCases_TransactionTemplate_ExecutesSaveInTransaction() {
                PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
                when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

                AutomatedTestRunnerService txRunner = new AutomatedTestRunnerService(
                                dockerSandboxService,
                                testCaseRepository,
                                submissionTestResultRepository,
                                submissionRepository,
                                txManager);

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(Collections.emptyList());
                when(dockerSandboxService.execute(submission.getSourceCode()))
                                .thenReturn(SandboxResult.builder()
                                                .status(SandboxResult.Status.SUCCESS)
                                                .executionTimeMs(50L)
                                                .build());

                txRunner.runAndGrade(submission);

                verify(txManager).commit(any());
                verify(submissionRepository).save(submission);
        }

        @Test
        @DisplayName("When submission is ZIP with test cases, executes test suite for workspace and generates fingerprints")
        void runAndGrade_WithTestCases_ZipSubmission_ExecutesWorkspaceSuite() {
                submission.setSubmissionType("ZIP");
                ReflectionTestUtils.setField(runnerService, "submissionStorageService", submissionStorageService);
                ReflectionTestUtils.setField(runnerService, "zipExtractionService", zipExtractionService);
                ReflectionTestUtils.setField(runnerService, "astPlagiarismService", astPlagiarismService);

                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                when(submissionStorageService.hasZip("sub-100")).thenReturn(true);
                when(submissionStorageService.getZipBytes("sub-100")).thenReturn(new byte[] { 1, 2, 3 });

                ExtractedWorkspace mockWorkspace = new ExtractedWorkspace(null, Collections.emptyList(), 10L, "hash");
                when(zipExtractionService.extract(any(byte[].class))).thenReturn(mockWorkspace);

                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .testCaseName("TC1")
                                .status("PASSED")
                                .pointsAwarded(BigDecimal.valueOf(10))
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .totalDurationMs(200L)
                                .build();

                when(dockerSandboxService.executeTestCaseSuiteForWorkspace(eq(mockWorkspace), any(), anyList()))
                                .thenReturn(suiteRes);

                runnerService.runAndGrade(submission);

                assertEquals("SUCCESS", submission.getExecutionStatus());
                assertEquals(new BigDecimal("10.00"), submission.getAutoScore());
                verify(astPlagiarismService).generateFingerprintsForWorkspace(submission, mockWorkspace);
        }

        @Test
        @DisplayName("FIX-01: When submission is ZIP with test cases and extraction fails, throws SandboxException without zeroing score")
        void runAndGrade_WithTestCases_ZipExtractionFails_ThrowsSandboxException() {
                submission.setSubmissionType("ZIP");
                submission.setTotalScore(new BigDecimal("7.50"));
                submission.setOverrideScore(new BigDecimal("9.00"));
                ReflectionTestUtils.setField(runnerService, "submissionStorageService", submissionStorageService);
                ReflectionTestUtils.setField(runnerService, "zipExtractionService", zipExtractionService);

                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                when(submissionStorageService.hasZip("sub-100")).thenReturn(true);
                when(submissionStorageService.getZipBytes("sub-100")).thenReturn(new byte[] { 1, 2, 3 });
                when(zipExtractionService.extract(any(byte[].class)))
                                .thenThrow(new RuntimeException("Disk I/O read failure"));

                assertThrows(SandboxException.class, () -> runnerService.runAndGrade(submission));

                assertEquals("PENDING", submission.getStatus());
                assertNull(submission.getAutoScore());
                assertEquals(new BigDecimal("7.50"), submission.getTotalScore());
                assertEquals(new BigDecimal("9.00"), submission.getOverrideScore());
                verify(submissionTestResultRepository, never()).saveAll(any());
                verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FIX-01: Docker unavailable during test suite throws SandboxException and does NOT produce permanent zero score or touch override")
        void runAndGrade_WithTestCases_DockerUnavailable_DoesNotProducePermanentZeroScore() {
                submission.setTotalScore(new BigDecimal("8.50"));
                submission.setOverrideScore(new BigDecimal("9.50"));

                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList()))
                                .thenThrow(new SandboxException("Docker engine error: daemon socket unreachable"));

                assertThrows(SandboxException.class, () -> runnerService.runAndGrade(submission));

                assertEquals("PENDING", submission.getStatus());
                assertNotEquals("COMPILE_ERROR", submission.getExecutionStatus());
                assertNull(submission.getAutoScore());
                assertEquals(new BigDecimal("8.50"), submission.getTotalScore());
                assertEquals(new BigDecimal("9.50"), submission.getOverrideScore());
                verify(submissionTestResultRepository, never()).deleteBySubmission_Id(any());
                verify(submissionTestResultRepository, never()).saveAll(any());
                verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FIX-01: Sandbox capacity exceeded throws retryable SandboxException without persisting zero score")
        void runAndGrade_SandboxCapacityExceeded_ThrowsRetryableSandboxException() {
                submission.setTotalScore(new BigDecimal("7.00"));

                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList()))
                                .thenThrow(new SandboxException("Sandbox is at capacity. Try again shortly."));

                SandboxException ex = assertThrows(SandboxException.class, () -> runnerService.runAndGrade(submission));
                assertTrue(ex.getMessage().contains("Sandbox is at capacity"));
                assertEquals("PENDING", submission.getStatus());
                assertEquals(new BigDecimal("7.00"), submission.getTotalScore());
                verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FIX-02: Worker 1 loses its lease; Worker 2 claims; Worker 1 cannot persist test results or overwrite score")
        void runAndGrade_Worker1LosesLeaseToWorker2_CannotPersist() {
                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status("PASSED")
                                .pointsAwarded(BigDecimal.valueOf(10))
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .totalDurationMs(65000L)
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);
                // Worker 1's claim token ("token-worker-1") was superseded by Worker 2
                // ("token-worker-2")
                when(submissionRepository.completeGradingWithToken(
                                eq("sub-100"), eq("token-worker-1"), anyString(), anyInt(), any(), any(), any(),
                                anyLong(), any(LocalDateTime.class)))
                                .thenReturn(0);

                TestCaseBatchSuiteResult result = runnerService.runAndGrade(submission, "token-worker-1");

                assertTrue(result.isFencedOut());
                assertEquals("PENDING", submission.getStatus());
                assertNull(submission.getAutoScore());
                verify(submissionTestResultRepository, never()).deleteBySubmission_Id(any());
                verify(submissionTestResultRepository, never()).saveAll(any());
                verify(submissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FIX-02: Duplicate callback does not duplicate test results or audit logs")
        void runAndGrade_DuplicateCallback_DoesNotDuplicateTestResultsOrAuditLogs() {
                ReflectionTestUtils.setField(runnerService, "gradeAuditLogRepository", gradeAuditLogRepository);
                submission.setOverrideScore(new BigDecimal("6.00"));
                submission.setTotalScore(new BigDecimal("6.00"));

                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status("PASSED")
                                .pointsAwarded(BigDecimal.valueOf(10))
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .totalDurationMs(120L)
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);
                when(submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc("exam-1",
                                "student-1"))
                                .thenReturn(Optional.of(submission));

                // First callback succeeds with claim token
                when(submissionRepository.completeGradingWithToken(
                                eq("sub-100"), eq("token-1"), anyString(), anyInt(), any(), any(), any(), anyLong(),
                                any(LocalDateTime.class)))
                                .thenReturn(1)
                                // Second duplicate callback sees claimToken already consumed (returns 0)
                                .thenReturn(0);

                TestCaseBatchSuiteResult firstResult = runnerService.runAndGrade(submission, "token-1");
                assertFalse(firstResult.isFencedOut());
                assertEquals(new BigDecimal("10.00"), submission.getTotalScore());
                assertNull(submission.getOverrideScore());

                // Duplicate callback with same token is fenced out
                TestCaseBatchSuiteResult secondResult = runnerService.runAndGrade(submission, "token-1");
                assertTrue(secondResult.isFencedOut());

                // Test results and audit log were written exactly once
                verify(submissionTestResultRepository, times(1)).deleteBySubmission_Id("sub-100");
                verify(submissionTestResultRepository, times(1)).saveAll(any());
                verify(gradeAuditLogRepository, times(1)).save(any(GradeAuditLog.class));
                verify(submissionRepository, times(1)).save(submission);
        }

        @Test
        @DisplayName("FIX-02: A stale submission cannot overwrite the latest question score or write override-reset audit log")
        void runAndGrade_StaleSubmission_CannotOverwriteLatestQuestionScore() {
                ReflectionTestUtils.setField(runnerService, "gradeAuditLogRepository", gradeAuditLogRepository);

                // Newer submission sub-200 already graded with 10.00
                Submission latestSubmission = Submission.builder()
                                .id("sub-200")
                                .exam(exam)
                                .student(submission.getStudent())
                                .status("COMPLETED")
                                .autoScore(new BigDecimal("10.00"))
                                .totalScore(new BigDecimal("10.00"))
                                .createdAt(LocalDateTime.now())
                                .build();

                // Older submission sub-100 finishes late with 0.00
                submission.setCreatedAt(LocalDateTime.now().minusMinutes(5));
                submission.setOverrideScore(new BigDecimal("5.00"));

                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status("FAILED")
                                .pointsAwarded(BigDecimal.ZERO)
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .totalDurationMs(200L)
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);
                when(submissionRepository.completeGradingWithToken(
                                eq("sub-100"), eq("token-stale"), anyString(), anyInt(), any(), any(), any(), anyLong(),
                                any(LocalDateTime.class)))
                                .thenReturn(1);
                // Repository reports sub-200 is the latest submission for (exam-1, student-1)
                when(submissionRepository.findFirstByExam_IdAndStudent_IdOrderByCreatedAtDescIdDesc("exam-1",
                                "student-1"))
                                .thenReturn(Optional.of(latestSubmission));

                TestCaseBatchSuiteResult res = runnerService.runAndGrade(submission, "token-stale");

                assertFalse(res.isFencedOut());
                // Latest submission score remains 10.00 untouched
                assertEquals(new BigDecimal("10.00"), latestSubmission.getTotalScore());
                assertEquals(new BigDecimal("10.00"), runnerService.resolveLatestQuestionScore("exam-1", "student-1"));
                // Stale submission did NOT trigger an override-reset audit log
                verify(gradeAuditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("When all test cases fail, sets FAILED execution status and exitCode 2")
        void runAndGrade_WithTestCases_AllFailed_SetsFailedStatusAndExitCode2() {
                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status("FAILED")
                                .pointsAwarded(BigDecimal.ZERO)
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);

                runnerService.runAndGrade(submission);

                assertEquals("FAILED", submission.getExecutionStatus());
                assertEquals(2, submission.getExitCode());
                assertEquals(BigDecimal.ZERO.setScale(2), submission.getAutoScore());
        }

        @Test
        @DisplayName("When teacher has already set totalScore, grading preserves teacher totalScore without overwriting")
        void runAndGrade_WithTestCases_TeacherAlreadyGraded_PreservesTotalScore() {
                submission.setTotalScore(new BigDecimal("8.50"));

                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status("FAILED")
                                .pointsAwarded(BigDecimal.ZERO)
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);

                runnerService.runAndGrade(submission);

                assertEquals(new BigDecimal("8.50"), submission.getTotalScore());
                assertEquals(BigDecimal.ZERO.setScale(2), submission.getAutoScore());
        }

        @Test
        @DisplayName("When test case points is null, defaults to 10.00 weight and computes score accurately")
        void runAndGrade_WithTestCases_DefaultPointWeight_WhenPointsNull() {
                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setName("TC without points");
                tc.setPoints(null);

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status("PASSED")
                                .pointsAwarded(null)
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);

                runnerService.runAndGrade(submission);

                assertEquals("SUCCESS", submission.getExecutionStatus());
                assertEquals(new BigDecimal("10.00"), submission.getAutoScore());
        }

        @Test
        @DisplayName("When total weight is zero, falls back to equal count fraction")
        void runAndGrade_WithTestCases_ZeroTotalWeight_FallsBackToCountAverage() {
                TestCase tc1 = new TestCase();
                tc1.setId("tc-1");
                tc1.setPoints(BigDecimal.ZERO);

                TestCase tc2 = new TestCase();
                tc2.setId("tc-2");
                tc2.setPoints(BigDecimal.ZERO);

                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc1, tc2));

                TestCaseExecutionItemResult item1 = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status("PASSED")
                                .pointsAwarded(BigDecimal.ZERO)
                                .build();
                TestCaseExecutionItemResult item2 = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-2")
                                .status("FAILED")
                                .pointsAwarded(BigDecimal.ZERO)
                                .build();

                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(item1, item2))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);

                runnerService.runAndGrade(submission);

                assertEquals(new BigDecimal("5.00"), submission.getAutoScore());
                assertEquals("PARTIAL_SUCCESS", submission.getExecutionStatus());
        }

        @Test
        @DisplayName("Handles null fields in item results gracefully with safe defaults")
        void runAndGrade_WithTestCases_NullFieldsInItemResult_SafeDefaults() {
                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status(null)
                                .isHidden(null)
                                .actualOutput(null)
                                .expectedOutput(null)
                                .errorMessage(null)
                                .pointsAwarded(null)
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);

                runnerService.runAndGrade(submission);

                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
                verify(submissionTestResultRepository).saveAll(captor.capture());
                SubmissionTestResult saved = captor.getValue().get(0);

                assertEquals("FAILED", saved.getStatus());
                assertFalse(saved.getIsHidden());
                assertEquals(BigDecimal.ZERO, saved.getPointsAwarded());
                assertNull(saved.getActualOutput());
        }

        @Test
        @DisplayName("Safely truncates outputs exceeding maximum UTF-8 byte boundary")
        void runAndGrade_WithTestCases_OutputTruncation_SafeTruncatesLongOutput() {
                TestCase tc = new TestCase();
                tc.setId("tc-1");
                tc.setPoints(BigDecimal.valueOf(10));
                when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                                .thenReturn(List.of(tc));

                String hugeOutput = "X".repeat(70_000);
                TestCaseExecutionItemResult itemRes = TestCaseExecutionItemResult.builder()
                                .testCaseId("tc-1")
                                .status("FAILED")
                                .actualOutput(hugeOutput)
                                .errorMessage(hugeOutput)
                                .build();
                TestCaseBatchSuiteResult suiteRes = TestCaseBatchSuiteResult.builder()
                                .compiled(true)
                                .itemResults(List.of(itemRes))
                                .build();

                when(dockerSandboxService.executeTestCaseSuite(any(), anyList())).thenReturn(suiteRes);

                runnerService.runAndGrade(submission);

                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
                verify(submissionTestResultRepository).saveAll(captor.capture());
                SubmissionTestResult saved = captor.getValue().get(0);

                assertTrue(saved.getActualOutput().length() <= 65535);
                assertTrue(saved.getErrorMessage().length() <= 65535);
        }
}
