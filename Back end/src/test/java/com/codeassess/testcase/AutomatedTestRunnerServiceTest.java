package com.codeassess.testcase;

import com.codeassess.exam.entity.Exam;
import com.codeassess.sandbox.service.DockerSandboxService;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.dto.sandbox.TestCaseBatchSuiteResult;
import com.codeassess.testcase.dto.sandbox.TestCaseExecutionItemResult;
import com.codeassess.testcase.entity.SubmissionTestResult;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.SubmissionTestResultRepository;
import com.codeassess.testcase.repository.TestCaseRepository;
import com.codeassess.testcase.service.AutomatedTestRunnerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.codeassess.plagiarism.service.AstPlagiarismService;
import com.codeassess.plagiarism.service.SimilarityMatrixService;
import com.codeassess.submission.dto.EntryPointResult;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.codeassess.submission.service.EntryPointDiscoveryService;
import com.codeassess.submission.service.SubmissionStorageService;
import com.codeassess.submission.service.ZipExtractionService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

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

    private AutomatedTestRunnerService runnerService;

    private Exam exam;
    private Submission submission;

    @BeforeEach
    void setUp() {
        runnerService = new AutomatedTestRunnerService(
                dockerSandboxService,
                testCaseRepository,
                submissionTestResultRepository,
                submissionRepository
        );

        exam = new Exam();
        exam.setId("exam-1");
        exam.setTitle("Java Final Exam");

        submission = new Submission();
        submission.setId("sub-100");
        submission.setExam(exam);
        submission.setSourceCode("public class Main { public static void main(String[] args) {} }");
        submission.setExecutionStatus("PENDING");
    }

    @Test
    @DisplayName("When exam has no test cases, submission is marked COMPLETED with null auto_score")
    void testExamWithNoTestCases() {
        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.emptyList());
        when(dockerSandboxService.execute(submission.getSourceCode()))
                .thenReturn(com.codeassess.sandbox.dto.response.SandboxResult.builder()
                        .status(com.codeassess.sandbox.dto.response.SandboxResult.Status.SUCCESS)
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
        assertEquals(0, saved.size()); // Compile fail from suiteResult has empty itemResults unless generated
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
        // 20 out of 100 points -> 2.00 on 10-point scale
        assertEquals(new BigDecimal("2.00"), submission.getAutoScore());
        assertEquals(1, submission.getTestCasesPassed());
        assertEquals(2, submission.getTestCasesTotal());
    }

    @Test
    @DisplayName("Timeout test case gets TIMEOUT status and 0 points")
    void testTimeoutHandling() {
        TestCase tc = new TestCase();
        tc.setId("tc-1");
        tc.setName("Infinite Loop Test");
        tc.setPoints(BigDecimal.valueOf(10));
        tc.setExpectedOutput("Done");

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(tc));

        TestCaseExecutionItemResult res = TestCaseExecutionItemResult.builder()
                .testCaseId("tc-1")
                .testCaseName("Infinite Loop Test")
                .status("TIMEOUT")
                .actualOutput("")
                .expectedOutput("Done")
                .exitCode(124)
                .pointsAwarded(BigDecimal.ZERO)
                .maxPoints(BigDecimal.valueOf(10))
                .executionTimeMs(5000L)
                .build();

        TestCaseBatchSuiteResult suiteResult = TestCaseBatchSuiteResult.builder()
                .compiled(true)
                .itemResults(List.of(res))
                .build();

        when(dockerSandboxService.executeTestCaseSuite(any(), anyList()))
                .thenReturn(suiteResult);

        runnerService.runAndGrade(submission);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(submissionTestResultRepository).saveAll(captor.capture());
        SubmissionTestResult saved = captor.getValue().get(0);
        assertEquals("TIMEOUT", saved.getStatus());
        assertEquals(BigDecimal.ZERO, saved.getPointsAwarded());
        assertEquals(0, submission.getTestCasesPassed());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 2B Group 5 Targeted Test Extensions
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
    @DisplayName("When exam has no test cases and sandbox execution throws, sets INTERNAL_ERROR status")
    void runAndGrade_NoTestCases_SandboxThrowsException_SetsInternalError() {
        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.emptyList());
        when(dockerSandboxService.execute(submission.getSourceCode()))
                .thenThrow(new RuntimeException("Docker daemon connection failed"));

        runnerService.runAndGrade(submission);

        assertEquals("INTERNAL_ERROR", submission.getExecutionStatus());
        assertTrue(submission.getStderr().contains("Sandbox execution failure: Docker daemon connection failed"));
        assertEquals("COMPLETED", submission.getStatus());
        assertNull(submission.getAutoScore());
    }

    @Test
    @DisplayName("When exam has no test cases and submission is ZIP, extracts workspace and triggers AST plagiarism")
    void runAndGrade_NoTestCases_ZipSubmission_ExtractsAndExecutesWorkspace() throws Exception {
        submission.setSubmissionType("ZIP");
        ReflectionTestUtils.setField(runnerService, "submissionStorageService", submissionStorageService);
        ReflectionTestUtils.setField(runnerService, "zipExtractionService", zipExtractionService);
        ReflectionTestUtils.setField(runnerService, "entryPointDiscoveryService", entryPointDiscoveryService);
        ReflectionTestUtils.setField(runnerService, "astPlagiarismService", astPlagiarismService);

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.emptyList());
        when(submissionStorageService.hasZip("sub-100")).thenReturn(true);
        when(submissionStorageService.getZipBytes("sub-100")).thenReturn(new byte[]{1, 2, 3});

        ExtractedWorkspace mockWorkspace = new ExtractedWorkspace(null, Collections.emptyList(), 10L, "hash");
        when(zipExtractionService.extract(any(byte[].class))).thenReturn(mockWorkspace);
        when(entryPointDiscoveryService.discoverEntryPoint(mockWorkspace))
                .thenReturn(EntryPointResult.found("com.example.Main", List.of("com.example.Main")));
        when(dockerSandboxService.executeWorkspace(mockWorkspace, "com.example.Main"))
                .thenReturn(com.codeassess.sandbox.dto.response.SandboxResult.builder()
                        .status(com.codeassess.sandbox.dto.response.SandboxResult.Status.SUCCESS)
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
    @DisplayName("When exam has no test cases and ZIP extraction fails, sets INTERNAL_ERROR status")
    void runAndGrade_NoTestCases_ZipExtractionFails_SetsInternalError() {
        submission.setSubmissionType("ZIP");
        ReflectionTestUtils.setField(runnerService, "submissionStorageService", submissionStorageService);
        ReflectionTestUtils.setField(runnerService, "zipExtractionService", zipExtractionService);

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.emptyList());
        when(submissionStorageService.hasZip("sub-100")).thenReturn(true);
        when(submissionStorageService.getZipBytes("sub-100")).thenReturn(new byte[]{1, 2, 3});
        when(zipExtractionService.extract(any(byte[].class)))
                .thenThrow(new RuntimeException("Corrupted zip archive header"));

        runnerService.runAndGrade(submission);

        assertEquals("INTERNAL_ERROR", submission.getExecutionStatus());
        assertTrue(submission.getStderr().contains("Workspace extraction failure: Corrupted zip archive header"));
        assertEquals("COMPLETED", submission.getStatus());
    }

    @Test
    @DisplayName("When exam has no test cases and similarityMatrixService is configured, requests matrix refresh")
    void runAndGrade_NoTestCases_WithSimilarityMatrixService_TriggersRefresh() {
        ReflectionTestUtils.setField(runnerService, "similarityMatrixService", similarityMatrixService);

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.emptyList());
        when(dockerSandboxService.execute(submission.getSourceCode()))
                .thenReturn(com.codeassess.sandbox.dto.response.SandboxResult.builder()
                        .status(com.codeassess.sandbox.dto.response.SandboxResult.Status.SUCCESS)
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
                .thenReturn(com.codeassess.sandbox.dto.response.SandboxResult.builder()
                        .status(com.codeassess.sandbox.dto.response.SandboxResult.Status.SUCCESS)
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
                txManager
        );

        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(Collections.emptyList());
        when(dockerSandboxService.execute(submission.getSourceCode()))
                .thenReturn(com.codeassess.sandbox.dto.response.SandboxResult.builder()
                        .status(com.codeassess.sandbox.dto.response.SandboxResult.Status.SUCCESS)
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
        when(submissionStorageService.getZipBytes("sub-100")).thenReturn(new byte[]{1, 2, 3});

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
    @DisplayName("When submission is ZIP and extraction fails, maps all test cases to INTERNAL_ERROR")
    void runAndGrade_WithTestCases_ZipExtractionFails_SetsAllItemsInternalError() {
        submission.setSubmissionType("ZIP");
        ReflectionTestUtils.setField(runnerService, "submissionStorageService", submissionStorageService);
        ReflectionTestUtils.setField(runnerService, "zipExtractionService", zipExtractionService);

        TestCase tc = new TestCase();
        tc.setId("tc-1");
        tc.setName("TC1");
        tc.setPoints(BigDecimal.valueOf(10));
        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(tc));

        when(submissionStorageService.hasZip("sub-100")).thenReturn(true);
        when(submissionStorageService.getZipBytes("sub-100")).thenReturn(new byte[]{1, 2, 3});
        when(zipExtractionService.extract(any(byte[].class)))
                .thenThrow(new RuntimeException("Zip Slip attempt detected"));

        runnerService.runAndGrade(submission);

        assertEquals("COMPILE_ERROR", submission.getExecutionStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), submission.getAutoScore());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(submissionTestResultRepository).saveAll(captor.capture());
        SubmissionTestResult saved = captor.getValue().get(0);
        assertEquals("INTERNAL_ERROR", saved.getStatus());
        assertTrue(saved.getErrorMessage().contains("Workspace extraction failure: Zip Slip attempt detected"));
    }

    @Test
    @DisplayName("When sandbox test suite execution throws, maps all test cases to INTERNAL_ERROR")
    void runAndGrade_WithTestCases_SandboxSuiteThrowsException_SetsAllItemsInternalError() {
        TestCase tc = new TestCase();
        tc.setId("tc-1");
        tc.setName("TC1");
        tc.setPoints(BigDecimal.valueOf(10));
        when(testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc("exam-1"))
                .thenReturn(List.of(tc));

        when(dockerSandboxService.executeTestCaseSuite(any(), anyList()))
                .thenThrow(new RuntimeException("Docker daemon OOM killer killed container"));

        runnerService.runAndGrade(submission);

        assertEquals("COMPILE_ERROR", submission.getExecutionStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), submission.getAutoScore());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionTestResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(submissionTestResultRepository).saveAll(captor.capture());
        SubmissionTestResult saved = captor.getValue().get(0);
        assertEquals("INTERNAL_ERROR", saved.getStatus());
        assertTrue(saved.getErrorMessage().contains("Docker daemon OOM killer killed container"));
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
